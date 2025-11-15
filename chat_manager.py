# chat_manager.py
import os
import json
import re
from datetime import date, datetime
from bson import ObjectId
from langchain.chains.conversation.base import ConversationChain
from langchain.memory import ConversationBufferMemory
from langchain_core.messages import messages_from_dict, messages_to_dict, SystemMessage
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_openai import ChatOpenAI
from mongodb_utils import save_recommendation, trips_collection,user_collection
from flask_socketio import emit
from preference import extract_preferences_from_text, update_user_preferences
from utils import extract_json


pending_add_location = {}

# === 初始化 ===
today = date.today().strftime("%Y年%m月%d日")
os.environ["GOOGLE_API_KEY"] = "AIzaSyD--xrfytwcRt6aGzCnvLauVz-JDmV5GOA" 

MEMORY_FOLDER = "memories"
os.makedirs(MEMORY_FOLDER, exist_ok=True)

user_chains = {}
last_analysis = {}

# === 記憶體處理 ===
def load_memory(user_id: str):
    path = os.path.join(MEMORY_FOLDER, f"memory_{user_id}.json")
    if os.path.exists(path):
        print(f"🔍 載入記憶檔案：{path}")
        with open(path, "r", encoding="utf-8") as f:
            return messages_from_dict(json.load(f))
    print(f"⚠️ 找不到記憶檔案：{path}")
    return []

def save_memory(user_id: str, messages):
    path = os.path.join(MEMORY_FOLDER, f"memory_{user_id}.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(messages_to_dict(messages), f, ensure_ascii=False, indent=2)
    print(f"💾 已儲存記憶：{path}")

def get_user_chain(user_id: str):
    if user_id not in user_chains:
        llm = ChatOpenAI(
            model="gpt-4o-mini",  # ✅
            api_key=os.getenv("OPENAI_API_KEY")
        )
        memory = ConversationBufferMemory(
            return_messages=True,
            k=50
        )
        all_msgs = load_memory(user_id)
        filtered_msgs = [msg for msg in all_msgs if "今天是20" not in msg.content]
        memory.chat_memory.messages = filtered_msgs

        chain = ConversationChain(
            llm=llm,
            memory=memory,
            verbose=False
        )
        user_chains[user_id] = chain
    return user_chains[user_id]

def update_and_save_memory(user_id: str, chain):
    messages = chain.memory.chat_memory.messages
    save_memory(user_id, messages)

# 💡 這裡開始是修改後的函式
def display_trip_by_trip_id(trip_id: ObjectId) -> str:
    """
    根據新的 nodes 資料結構，將行程資料轉換為文字格式。
    """
    trip = trips_collection.find_one({"_id": trip_id})
    print("trip的資料型態",type(trip))

    if not trip:
        return "❌ 查無行程"

    days = trip.get("days", [])
    nodes = trip.get("nodes", [])
    
    if not days:
        return "❌ 查無行程 (無任何天數安排)"
    
    if not nodes:
        return "❌ 查無行程 (無任何 nodes)"

    # 建立一個 node_id 到 node 物件的對應字典，方便快速查找
    nodes_map = {node.get("node_id"): node for node in nodes}

    result = (
        f"📌 行程名稱：{trip.get('title', '未命名')}\n"
        f"📅 日期：{trip.get('start_date')} 至 {trip.get('end_date')}\n"
        f"💰 預算：{trip.get('total_budget', 'N/A')} 元\n"
        f"📍 每日行程安排：\n"
    )

    for day_data in days:
        day_number = day_data.get("day")
        date = day_data.get("date", "")
        city = day_data.get("city", "")
        head_id = day_data.get("head_id")

        result += f"\n=== Day {day_number} ({date}) - {city} ===\n"

        if not head_id:
            result += "無排程\n"
            continue

        # 根據 head_id 開始遍歷該天的 nodes
        current_id = head_id
        while current_id:
            current_node = nodes_map.get(current_id)
            if not current_node:
                result += f"⚠️ 連結錯誤：找不到 ID 為 {current_id} 的 node\n"
                break

            slot = current_node.get("slot", "")
            start_time = current_node.get("start", "??:??")
            end_time = current_node.get("end", "??:??")
            places = current_node.get("places", [])

            result += f"{start_time}~{end_time} ({slot})\n"

            # 顯示該 slot 的所有地點
            for place in places:
                name = place.get("name", "未填活動")
                category = place.get("category", "")
                stay_minutes = place.get("stay_minutes", 0)

                result += f"  • {name} ({category})\n"
                result += f" ⏱️ {stay_minutes}分鐘\n"

            # 移動到下一個 node
            current_id = current_node.get("next_id")

    return result.strip()


# ✅ 查詢行程
def get_itinerary(user_id):
    try:
        if isinstance(user_id, str):
            # 💡 由於 trip_id 是字串，這裡不需要轉成 ObjectId
            query = {"trip_id": user_id}
        else:
            print("⚠️ 警告: get_itinerary 接收到非字串 user_id，請檢查呼叫方式。")
            return None

        print(f"🔍 正在查詢行程：{query}")
        doc = trips_collection.find_one(query)

        if doc:
            print("✅ 成功找到行程！標題：", doc.get("title"))
        else:
            print("❌ 沒找到對應行程")
        return doc
    except Exception as e:
        print("❌ get_itinerary 發生錯誤：", e)
        return None


# ----------------- 核心修改部分 -----------------
def verify_alternative_places(alternative_places: list) -> list:
    """
    使用 Google Maps API 驗證替代景點是否真實存在
    返回驗證成功的景點清單
    """
    try:
        from place_util import search_places_by_tag
        verified_places = []

        for place in alternative_places:
            if not place or not isinstance(place, str):
                continue

            # 使用現有的 Google Maps API 搜尋功能
            search_results = search_places_by_tag(place.strip())
            if search_results:
                # 取得第一個搜尋結果的名稱（經過 Google Maps 驗證的正確名稱）
                verified_name = search_results[0].get('name', place.strip())
                verified_places.append(verified_name)
                print(f"✅ 驗證成功：{place} -> {verified_name}")
            else:
                print(f"❌ 驗證失敗：{place} (Google Maps 找不到)")

        return verified_places[:3]  # 最多返回 3 個驗證成功的景點

    except Exception as e:
        print(f"❌ verify_alternative_places 發生錯誤：{e}")
        return []

# chat_manager.py (修正後的 analyze_active_users_preferences 函式)
# chat_manager.py (修正後的 analyze_active_users_preferences 函式)
# chat_manager.py 中修改後的 analyze_active_users_preferences 函式

# chat_manager.py 中修改後的 analyze_active_users_preferences 函式

def analyze_active_users_preferences(user_chains: dict, trip_id_ob: ObjectId) -> list:
    """
    分析行程中所有使用者的偏好，並提供行程修改建議
    讓 AI 自動判斷旅遊地點，確保推薦同縣市的景點
    """
    try:
        # 取得行程資料
        trip_text = display_trip_by_trip_id(trip_id_ob)
        print("✅ trip_text:", trip_text)
        
        # 如果查無行程，立即返回空列表
        if "❌ 查無行程" in trip_text:
            print("❌ 找不到行程資料，無法進行分析")
            return []
        
        trip_id = str(trip_id_ob)

        # 載入相關偏好
        from preference import load_preferences_by_trip_id
        trip_preferences = load_preferences_by_trip_id(trip_id)
        
        all_prefer = trip_preferences.get("prefer", [])
        all_avoid = trip_preferences.get("avoid", [])
        
        print(f"🔍 行程 {trip_id} 的合併偏好：")
        print(f"   喜歡：{all_prefer}")
        print(f"   避免：{all_avoid}")

        # 收集聊天紀錄
        combined_text = ""
        for user_id, chain in user_chains.items():
            messages = chain.memory.chat_memory.messages
            for msg in messages:
                if msg.type in ["human", "ai"]:
                    combined_text += f"{msg.type}: {msg.content}\n"

        if not combined_text.strip():
            print("⚠️ 沒有聊天紀錄可供分析")
            combined_text = "無聊天紀錄"

        # 準備更結構化的偏好摘要
        prefer_list = "\n".join([f"- {p}" for p in sorted(set(all_prefer))]) or "- 無特定偏好"
        avoid_list = "\n".join([f"- {p}" for p in sorted(set(all_avoid))]) or "- 無特定避免項目"
        
        preference_summary = f"""
🧠 整體喜好：
{prefer_list}

⚠️ 整體避免：
{avoid_list}
"""

        # 💡 讓 AI 自動判斷並生成建議的智能 Prompt
        prompt = f"""
你是一位智慧旅遊顧問，請根據使用者們的聊天內容與偏好，對他們目前的旅遊行程提出**具體修改建議**。

🧠 **智能分析要求：**
1. 首先分析行程內容，自動判斷這是哪個縣市的旅遊行程
2. 所有推薦的替代景點都必須位於**同一個縣市**內
3. 絕對不可推薦其他縣市的景點

⚠️ 重要規則：
1. 你必須強制優先提供「modify」建議
2. **每個 modify 建議都必須提供恰好 3 個替代景點選項**
3. **所有替代景點都必須與行程中其他景點位於同一縣市**
4. 只有在完全無法找到任何同縣市替代景點時，才能使用「delete」

**請務必只回傳一個符合 JSON Schema 的 JSON 程式碼區塊，不要包含任何額外的文字或說明。**
JSON 必須包含在 ```json 和 ``` 之間。

**建議格式（每個 modify 必須有 3 個同縣市的 alternative_places）：**
```json
[
    {{
        "type": "modify",
        "day": 1,
        "place": "原景點名稱",
        "alternative_places": ["同縣市景點1", "同縣市景點2", "同縣市景點3"],
        "reason": "詳細說明為什麼要替換這個景點，以及推薦的替代景點如何更符合使用者需求。請確保替代景點都在同一縣市。"
    }},
    {{
        "type": "add",
        "day": 2,
        "place": "建議新增的同縣市景點",
        "reason": "說明為什麼建議新增這個景點。"
    }},
    {{
        "type": "delete",
        "day": 3,
        "place": "建議刪除的景點",
        "reason": "說明為什麼建議刪除，且確實找不到同縣市的合適替代景點。"
    }}
]
```

**分析步驟：**
1. **地點判斷**：根據行程中的景點名稱，判斷這是哪個縣市的旅遊
2. **地理一致性**：確保所有推薦的替代景點都在同一縣市
3. **質量評估**：分析現有景點是否符合使用者偏好
4. **優化建議**：提供更符合偏好且地理位置合理的替代方案

**重要提醒：**
- 仔細觀察行程中的景點，判斷旅遊地點（例如：如果看到愛河、駁二、蓮池潭，就知道是高雄）
- 所有替代景點必須與判斷出的旅遊地點一致
- 每個 modify 建議必須提供 3 個選項，讓使用者有充分選擇
- 考慮交通便利性、時間安排合理性、景點特色多樣性

**地理位置原則：**
- 如果是高雄行程，只推薦高雄的景點
- 如果是台北行程，只推薦台北的景點  
- 如果是台中行程，只推薦台中的景點
- 以此類推，絕對不可跨縣市推薦

=== 使用者偏好分析 ===
{preference_summary.strip()}

=== 目前行程內容（請先分析這是哪個縣市的旅遊） ===
{trip_text}

=== 聊天紀錄分析 ===
{combined_text[:2000]}

請先智能判斷旅遊地點，然後為需要修改的景點提供 3 個該地區的優質替代選項。

🚨 **核心要求：推薦的景點必須與行程中其他景點位於同一縣市！**
"""
        print("🧠 讓 AI 自動判斷旅遊地點，準備分析...")
        print(f"📝 Prompt 長度：{len(prompt)} 字元")

        # 呼叫 Gemini 進行分析
        from langchain_google_genai import ChatGoogleGenerativeAI
        analysis_llm = ChatOpenAI(
            model="gpt-4o-mini",  # ✅
            api_key=os.getenv("OPENAI_API_KEY")
        )
        response = analysis_llm.invoke(prompt).content
        print("📩 Gemini 回應原始文字：\n", response)

        # 解析 JSON 回應
        from utils import extract_json
        recommendations = extract_json(response)
        
        if recommendations is None:
            print("⚠️ 無法解析 Gemini 回應為 JSON，請檢查 Gemini 的回應格式。")
            return []
            
        if not isinstance(recommendations, list):
            print("⚠️ Gemini 回應不是陣列格式，請檢查 Gemini 的回應格式。")
            return []

        # 處理回應格式並驗證替代景點
        processed_recommendations = []
        for rec in recommendations:
            if not isinstance(rec, dict):
                print(f"⚠️ 略過無效的建議項目：{rec}")
                continue
                
            # 確保必要欄位存在
            if rec.get('type') not in ['add', 'delete', 'modify']:
                print(f"⚠️ 略過無效的建議類型：{rec}")
                continue
                
            processed_rec = {
                'type': rec['type'],
                'day': rec.get('day'),
                'place': rec.get('place', ''),
                'reason': rec.get('reason', '')
            }
            
            # 處理 modify 類型：驗證替代景點
            if rec['type'] == 'modify':
                alternative_places = rec.get('alternative_places', [])
                if isinstance(alternative_places, str):
                    alternative_places = [alternative_places]
                
                # 確保最多 3 個選項
                alternative_places = alternative_places[:3]
                
                # 使用 Google Maps API 驗證替代景點
                verified_alternatives = verify_alternative_places(alternative_places)
                
                if verified_alternatives:
                    # 如果驗證成功的選項夠多，使用驗證後的
                    if len(verified_alternatives) >= 2:
                        processed_rec['new_places'] = verified_alternatives
                        processed_recommendations.append(processed_rec)
                        print(f"✅ Modify 建議已驗證：{rec.get('place')} -> {verified_alternatives}")
                    else:
                        # 驗證成功的太少，保留原始選項
                        print(f"⚠️ 驗證成功的選項太少({len(verified_alternatives)})，保留原始選項")
                        processed_rec['new_places'] = alternative_places
                        processed_recommendations.append(processed_rec)
                else:
                    # 沒有驗證成功的，保留原始選項
                    if alternative_places:
                        processed_rec['new_places'] = alternative_places
                        processed_recommendations.append(processed_rec)
                        print(f"📝 保留未驗證的 Modify 建議：{rec.get('place')} -> {alternative_places}")
                    else:
                        # 真的沒有任何替代選項，降級為 delete
                        delete_rec = {
                            'type': 'delete',
                            'day': rec.get('day'),
                            'place': rec.get('place', ''),
                            'reason': f"{rec.get('reason', '')} 且確實找不到任何合適的替代景點。"
                        }
                        processed_recommendations.append(delete_rec)
                        print(f"🔄 Modify 最終降級為 Delete：{rec.get('place')} (完全無替代選項)")
            else:
                processed_recommendations.append(processed_rec)

        print(f"✅ 成功解析 {len(processed_recommendations)} 個建議")
        
        # 統計建議類型和選項數量
        type_counts = {}
        modify_option_counts = []
        for rec in processed_recommendations:
            type_counts[rec['type']] = type_counts.get(rec['type'], 0) + 1
            if rec['type'] == 'modify':
                option_count = len(rec.get('new_places', []))
                modify_option_counts.append(option_count)
        
        print(f"📊 建議類型統計：{type_counts}")
        if modify_option_counts:
            avg_options = sum(modify_option_counts) / len(modify_option_counts)
            print(f"📊 Modify 建議平均選項數：{avg_options:.1f}")
        
        for i, rec in enumerate(processed_recommendations):
            if rec['type'] == 'modify':
                option_count = len(rec.get('new_places', []))
                print(f"   建議 {i+1}：{rec['type']} - {rec.get('place')} -> {rec.get('new_places')} ({option_count}個選項) (Day {rec.get('day')})")
            else:
                print(f"   建議 {i+1}：{rec['type']} - {rec.get('place')} (Day {rec.get('day')})")
        
        return processed_recommendations

    except Exception as e:
        print(f"❌ analyze_active_users_preferences 發生錯誤：{e}")
        import traceback
        traceback.print_exc()
        return []


# ----------------- 意圖偵測 Prompt -----------------
INTENT_PROMPT = """
你是一位專門處理旅遊行程的助理。

請根據使用者的發言判斷：使用者是否**表達了明確想新增一個景點到旅遊行程中**的意圖？

**請務必只回傳一個符合 JSON Schema 的 JSON 程式碼區塊，不要包含任何額外的文字或說明。**
JSON 必須包含在 ````json` 和 ```` 之間。

JSON 格式如下：
```json
{{
    "add_location": true,
    "place_name": ""
}}
使用者說：
「{text}」
"""
def detect_add_location_intent(text: str) -> dict:
    from langchain_google_genai import ChatGoogleGenerativeAI

    llm = ChatOpenAI(
        model="gpt-4o-mini",  # 用便宜的版本
        api_key=os.getenv("OPENAI_API_KEY"),
        temperature=0.3,
        max_tokens=128
    )

    prompt = INTENT_PROMPT.format(text=text)
    print("🧠 Intent Prompt:\n", prompt)

    response = llm.invoke(prompt).content
    processed_response = str(response).strip()
    print("📩 Gemini 回應原始文字（repr）：", repr(processed_response))
    print("📩 Gemini 回應原始文字：", processed_response)

    result = extract_json(processed_response)
    print(f"🔍 extract_json 解析結果：{result}")

    if result and isinstance(result, dict):
        return {
            "add_location": result.get("add_location", False),
            "place_name": result.get("place_name", "").strip()
        }

    print("⚠️ 意圖偵測失敗：無法從 Gemini 回應中解析出有效 JSON。返回預設值。")
    return {"add_location": False, "place_name": ""}

def decide_location_placement(user_id: str, trip_id: str, place: str):
    """
    決定新地點應該放在行程的哪一天、哪個時段
    """
    try:
        llm = ChatGoogleGenerativeAI(
            model="gemini-1.5-pro-latest",
            temperature=0.3
        )

        chain = get_user_chain(user_id)
        chat_history = "\n".join([
            f"{msg.type}: {msg.content}"
            for msg in chain.memory.chat_memory.messages
        ])
        itinerary_text = display_trip_by_trip_id(trip_id)

        # 💡 修正：載入該使用者的個人偏好
        from preference import load_user_preferences
        all_preferences = load_user_preferences()
        user_preferences = all_preferences.get(user_id, {})
        
        prefer_str = "、".join(user_preferences.get("prefer", [])) or "無特定偏好"
        avoid_str = "、".join(user_preferences.get("avoid", [])) or "無特定避免項目"

        prompt = f"""
你是一位智慧行程規劃助理。請根據使用者目前的行程、個人偏好和聊天紀錄，判斷最適合將「{place}」這個景點安排在哪一天、哪個時段？

請考慮以下因素：
1. 地理位置的合理性（同區域景點安排在同一天）
2. 行程的鬆緊度（避免過度密集）
3. 使用者的個人偏好

使用者個人偏好：
🧠 喜歡：{prefer_str}
⚠️ 避免：{avoid_str}

目前行程內容：
{itinerary_text}

使用者聊天紀錄：
{chat_history[-1000:]}  # 限制最近的聊天紀錄

請務必只回傳一個符合 JSON Schema 的 JSON 程式碼區塊，不要包含任何額外的文字或說明。
JSON 必須包含在 ```json 和 ``` 之間。

JSON 格式如下：
```json
{{
    "day": 1,         // 最適合安排的日期，例如 Day1 就填 1
    "period": "上午"   // 最適合安排的時段，請選擇 "上午" 或 "下午"
}}
```

如果無法判斷或行程已滿，請回傳：
```json
{{
    "day": null,
    "period": null
}}
```
"""

        print("🧠 Placement Prompt:\n", prompt)
        response = llm.invoke(prompt).content
        print("📩 Gemini Placement 回應（repr）：", repr(response))

        from utils import extract_json
        result = extract_json(str(response))
        print(f"🔍 Placement 解析結果：{result}")

        if (
            result
            and isinstance(result, dict)
            and result.get("day") is not None
            and result.get("period") is not None
        ):
            return result

        return {"day": None, "period": None}
        
    except Exception as e:
        print(f"❌ decide_location_placement 發生錯誤：{e}")
        return {"day": None, "period": None}
    

