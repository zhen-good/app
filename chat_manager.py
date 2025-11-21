import os
import json
from bson import ObjectId

from langchain_core.messages import  messages_to_dict
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_openai import ChatOpenAI
from config import INTENT_PROMPT
from mongodb_utils import trips_collection
from utils import display_trip_by_trip_id, extract_json, get_user_chain
from config import user_chains,MEMORY_FOLDER,OPENAI_API_KEY


# === 記憶體處理 ===


def save_memory(user_id: str, messages):
    path = os.path.join(MEMORY_FOLDER, f"memory_{user_id}.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(messages_to_dict(messages), f, ensure_ascii=False, indent=2)
    print(f"💾 已儲存記憶：{path}")


def update_and_save_memory(user_id: str, chain):
    messages = chain.memory.chat_memory.messages
    save_memory(user_id, messages)


# =============== 新的推薦行程方式 ===============
def analyze_active_users_preferences(user_chains: dict, trip_id_ob: ObjectId) -> list:
    """
    分析行程中所有使用者的偏好，並提供行程修改建議
    支持跨縣市行程，每個建議都會根據該天的縣市搜尋替代景點
    """
    try:
        from preference import load_preferences_by_trip_id
        
        # 1️⃣ 取得行程資料
        trip = trips_collection.find_one({"_id": trip_id_ob})
        if not trip or 'days' not in trip:
            print("❌ 找不到行程資料或無有效天數")
            return []
        
        days = trip.get("days", [])
        trip_id = str(trip_id_ob)
        
        # 🆕 改這裡：提取每天的城市信息
        day_map = {}
        for i, day_data in enumerate(days, 1):
            city = day_data.get('city', '未知城市')
            day_map[i] = city
        
        print(f"✅ 已建立 day_map：{day_map}")
        
        # 2️⃣ 取得偏好和聊天紀錄
        trip_preferences = load_preferences_by_trip_id(trip_id)
        all_prefer = trip_preferences.get("prefer", [])
        all_avoid = trip_preferences.get("avoid", [])
        
        combined_text = "\n".join([
            f"{msg.type}: {msg.content}"
            for user_id, chain in user_chains.items()
            for msg in chain.memory.chat_memory.messages
            if msg.type in ["human", "ai"]
        ]) or "無聊天紀錄"
        
        # 3️⃣ 準備提示詞
        trip_text = display_trip_by_trip_id(trip_id_ob)
        prefer_list = "\n".join([f"- {p}" for p in sorted(set(all_prefer))]) or "- 無特定偏好"
        avoid_list = "\n".join([f"- {p}" for p in sorted(set(all_avoid))]) or "- 無特定避免項目"
        
        # 🆕 新增城市信息到提示詞
        city_info = "📍 行程城市分布：\n" + "\n".join([f"- Day {day}: {city}" for day, city in sorted(day_map.items())])
        
        prompt = f"""
        你是一位智慧旅遊顧問。請用兩階段分析使用者的行程：

        {city_info}

        🧠 **使用者偏好：**
        ✅ 喜歡：{', '.join(all_prefer) if all_prefer else '無'}
        ❌ 避免：{', '.join(all_avoid) if all_avoid else '無'}

        === 階段一：檢查衝突 ===
        1. 仔細檢查「行程內容」中的每個景點
        2. 判斷是否與「避免」偏好衝突
        3. 找出需要修改或刪除的景點

        === 階段二：生成建議 ===
        1. 對於衝突的景點，提供「修改」建議（用 search_keywords）
        2. 根據「喜歡」偏好，提供「新增」建議（用 search_keywords）
        3. 對於明顯不適合的景點，提供「刪除」建議

        ⚠️ **重要規則：**
        - 優先處理「避免」偏好的衝突（例如：使用者避免「人潮」，則夜市、熱門景點需要修改）
        - 你只需提供「搜尋關鍵字」，不需要具體景點名稱
        - 系統會用關鍵字在對應城市的 Google Maps 搜尋
        - search_keywords 應該反映使用者的「喜歡」偏好

        **建議格式：**
        ```json
        [
            {{"type": "modify", "day": 1, "place": "原景點名稱", "search_keywords": ["關鍵字1", "關鍵字2"], "reason": "此景點與使用者避免的XX偏好衝突，建議替換為符合YY偏好的景點"}},
            {{"type": "delete", "day": 2, "place": "景點名稱", "reason": "此景點與使用者避免的XX偏好嚴重衝突"}},
            {{"type": "add", "day": 3, "search_keywords": ["關鍵字"], "reason": "根據使用者喜歡的XX偏好，建議新增此類型景點"}}
        ]
        ```

        === 使用者偏好詳情 ===
        🧠 整體喜好：
        {prefer_list}

        ⚠️ 整體避免（請優先處理這些衝突）：
        {avoid_list}

        === 目前行程內容 ===
        {trip_text}

        === 聊天記錄參考 ===
        {combined_text[:1000]}

        📝 **分析步驟：**
        1. 先掃描行程，找出與「避免」偏好衝突的景點
        2. 對這些景點提供修改建議（search_keywords 要符合「喜歡」偏好）
        3. 根據「喜歡」偏好，建議可以新增的景點類型
        """
        
        # 4️⃣ 呼叫 LLM 生成建議
        print("🧠 準備呼叫 LLM 進行分析...")
        analysis_llm = ChatOpenAI(
            model="gpt-4o-mini",
            api_key=OPENAI_API_KEY
        )
        response = analysis_llm.invoke(prompt).content
        print("📩 LLM 回應已取得",response)
        
        # 5️⃣ 解析並處理建議
        recommendations = extract_json(response)
        if not recommendations or not isinstance(recommendations, list):
            print("⚠️ 無法解析 LLM 回應為有效的陣列")
            return []
        
        # 6️⃣ 處理每個建議
        processed_recommendations = []
        for rec in recommendations:
            if not isinstance(rec, dict):
                continue
            
            rec_type = rec.get('type')
            handler = RECOMMENDATION_HANDLERS.get(rec_type)
            
            if handler:
            # ✅ 檢查是否為 modify 類型，需要額外傳入 trip_id_ob
                if rec_type == 'modify':
                    result = handler(rec, day_map, trip_id_ob)  # ← 傳入 trip_id_ob
                else:
                    result = handler(rec, day_map)  # ← 其他類型不需要
                
                # ✅ 過濾掉空結果（當 place_id 找不到時會返回 []）
                if result:
                    processed_recommendations.extend(result)
                
                # 📊 統計結果
                type_counts = {}

        print(f"\n📋 處理建議統計：")
        print(f"   長度: {len(processed_recommendations)}")

        for i, rec in enumerate(processed_recommendations):
            # 🔴 防守性檢查
            if not isinstance(rec, dict):
                print(f"   ⚠️ 建議 [{i}] 不是字典")
                continue
            
            if 'type' not in rec:
                print(f"   ⚠️ 建議 [{i}] 沒有 'type' 字段")
                continue
            
            rec_type = rec['type']
            type_counts[rec_type] = type_counts.get(rec_type, 0) + 1

        print(f"\n📊 建議統計：{type_counts}")
        print(f"✅ 成功生成 {len(processed_recommendations)} 個建議\n")
        
        return processed_recommendations

    except Exception as e:
        print(f"❌ analyze_active_users_preferences 發生錯誤：{e}")
        import traceback
        traceback.print_exc()
        return []
    
def detect_add_location_intent(text: str) -> dict:

    llm = ChatOpenAI(
        model="gpt-4o-mini",
        api_key=OPENAI_API_KEY,
        temperature=0.3,
        max_tokens=128
    )

    prompt = INTENT_PROMPT.format(text=text)
    print("🧠 Intent Prompt:\n", prompt)

    response = llm.invoke(prompt).content
    processed_response = str(response).strip()
    print("📩 回應原始文字：", repr(processed_response))

    result = extract_json(processed_response)
    print(f"🔍 解析結果：{result}")

    if result and isinstance(result, dict):
        return {
            "add_location": result.get("add_location", False),
            "place_name": result.get("place_name", "").strip()
        }

    print("⚠️ 意圖偵測失敗：無法解析 JSON")
    return {"add_location": False, "place_name": ""}


def decide_location_placement(user_id: str, trip_id: str, place: str):
    """
    決定新地點應該放在行程的哪一天、哪個時段
    """
    try:
        llm = ChatGoogleGenerativeAI(
            model="gpt-4o-mini",
            temperature=0.3
        )

        chain = get_user_chain(user_id)
        chat_history = "\n".join([
            f"{msg.type}: {msg.content}"
            for msg in chain.memory.chat_memory.messages
        ])
        itinerary_text = display_trip_by_trip_id(trip_id)

        from preference import load_user_preferences
        all_preferences = load_user_preferences()
        user_preferences = all_preferences.get(user_id, {})
        
        prefer_str = "、".join(user_preferences.get("prefer", [])) or "無特定偏好"
        avoid_str = "、".join(user_preferences.get("avoid", [])) or "無特定避免項目"

        prompt = f"""
        你是一位智慧行程規劃助理。請判斷最適合將「{place}」安排在哪一天、哪個時段。

        使用者個人偏好：
        🧠 喜歡：{prefer_str}
        ⚠️ 避免：{avoid_str}

        目前行程內容：
        {itinerary_text}

        使用者聊天紀錄：
        {chat_history[-1000:]}

        請回傳 JSON：
        ```json
        {{"day": 1, "period": "上午"}}
        ```
        或無法判斷時回傳：
        ```json
        {{"day": null, "period": null}}
        ```
        """

        print("🧠 Placement Prompt:\n", prompt)
        response = llm.invoke(prompt).content

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


 
from chat_manager import decide_location_placement
from place_util import _get_place_id_by_name, search_places_by_tag

#這三個函式分別處理三種建議形式

def _process_modify_recommendation(rec, day_map, trip_id_ob):  # ← 加入 trip_id_ob
    """處理「修改」類型建議"""
    day = rec.get("day")
    city = day_map.get(day, "未知城市")
    original_place_name = rec.get("place", "")
    
    print(f"\n{'='*50}")
    print(f"🔧 處理 modify 建議")
    print(f"   Day: {day}")
    print(f"   City: {city}")
    print(f"   原景點: {original_place_name}")
    
    # ✅ 步驟 2.1：查詢原景點的 place_id
    original_place_id = _get_place_id_by_name(trip_id_ob, day, original_place_name)
    
    if not original_place_id:
        print(f"⚠️ 無法取得 place_id，跳過此建議")
        print(f"{'='*50}\n")
        return []  # ← 返回空列表，這個建議會被跳過
    
    # ✅ 步驟 2.2：搜尋新景點
    search_keywords = rec.get("search_keywords", [])
    new_places = []
    
    print(f"🔍 搜尋關鍵字：{search_keywords}")
    
    if search_keywords:
        for keyword in search_keywords:
            query = f"{city} {keyword}"
            print(f"🔍 Google Maps 查詢：{query}")
            
            # 呼叫搜尋函式
            places = search_places_by_tag(query)
            
            if places:
                print(f"📍 找到 {len(places)} 個景點")
                
                # ✅ 取前 3-5 個結果並格式化
                for place in places[:3]:  # ← 改回 3 個就好
                    # 🔍 檢查 place 的結構
                    print(f"   - {place.get('name', '未知')}")
                    
                    new_places.append({
                        "name": place.get("name"),
                        "place_id": place.get("place_id"),
                        "address": place.get("formatted_address", ""),
                        "lat": place.get("geometry", {}).get("location", {}).get("lat"),
                        "lng": place.get("geometry", {}).get("location", {}).get("lng"),
                        "rating": place.get("rating"),
                        "types": place.get("types", [])
                    })
            else:
                print(f"⚠️ 關鍵字 '{keyword}' 沒有找到結果")
    
    print(f"✅ 總共準備了 {len(new_places)} 個替代景點")
    print(f"{'='*50}\n")
    
    # ✅ 步驟 2.3：返回完整的建議
    return [{
        "type": "modify",
        "day": day,
        "place": original_place_name,
        "place_id": original_place_id,  # ← 現在有值了！
        "new_places": new_places,
        "reason": rec.get("reason", ""),
        "city": city
    }]



def _process_add_recommendation(user_id, trip_id,rec, day_map):
    """處理「新增」類型建議：根據關鍵字搜尋景點作為新增選項"""
    
    day = rec.get("day")
    city = day_map.get(day, "未知城市")
    search_keywords = rec.get("search_keywords", [])
    new_places = []
    
    print(f"\n{'='*50}")
    print(f"➕ 處理 add 建議")
    print(f"   Day: {day}")
    print(f"   City: {city}")
    print(f"   關鍵字: {search_keywords}")
    
    # ✅ 執行搜尋新景點
    if search_keywords:
        for keyword in search_keywords:
            query = f"{city} {keyword}"
            print(f"🔍 Google Maps 查詢：{query}")
            
            # 呼叫搜尋函式 (假設與 modify 使用相同的 search_places_by_tag)
            places = search_places_by_tag(query) 
            
            if places:
                print(f"📍 找到 {len(places)} 個景點")
                
                # ✅ 取前 3-5 個結果並格式化
                for place in places[:3]:  # ← 只取前 3 個作為選項
                    # 🔍 檢查 place 的結構
                    print(f"   - {place.get('name', '未知')}")
                    
                    new_places.append({
                        "name": place.get("name"),
                        "place_id": place.get("place_id"),
                        "address": place.get("formatted_address", ""),
                        "lat": place.get("geometry", {}).get("location", {}).get("lat"),
                        "lng": place.get("geometry", {}).get("location", {}).get("lng"),
                        "rating": place.get("rating"),
                        "types": place.get("types", [])
                    })
            else:
                print(f"⚠️ 關鍵字 '{keyword}' 沒有找到結果")
    
    # 如果完全沒有找到任何景點選項，則跳過此建議
    if not new_places:
        print(f"⚠️ 找不到任何適合新增的景點，跳過此建議")
        print(f"{'='*50}\n")
        return []

    print(f"✅ 總共準備了 {len(new_places)} 個新增景點選項")
    print(f"{'='*50}\n")

    # 5. ✅ 步驟 決定新景點的位置
    if new_places:
        # 💡 選擇第一個景點作為 LLM 判斷位置的依據
        top_place = new_places[0].get("name")
        print(f"🧠 嘗試為首選景點 '{top_place}' 決定位置...")
        
        # 呼叫您的位置決策函數
        placement = decide_location_placement(user_id, trip_id, top_place)

        
        # 轉換 period 為 slot (假設您的 period/slot 是一致的：上午, 中午, 下午, 晚上)
        day_to_add = placement.get("day")
        slot_to_add = placement.get("period") 
        
        if day_to_add and slot_to_add:
            print(f"✅ LLM 建議插入位置：Day {day_to_add}, 時段 {slot_to_add}")
        else:
            print("⚠️ LLM 無法決定最佳插入位置。")
    
    # ✅ 返回完整的建議結構
    return [{
        "type": "add",
        "day": day,
        "new_places": new_places,
        "reason": rec.get("reason", ""),
        "city": city,
        # 📌 關鍵：新增位置資訊
        "recommend_day": day_to_add,  # 推薦的 Day
        "recommend_slot": slot_to_add # 推薦的 Slot (時段)
    }]

def _process_delete_recommendation(rec, day_map):
    """处理「删除」类型建议"""
    day = rec.get("day")
    
    # ✅ 改这里：day_map[day] 现在是字符串城市名称
    city = day_map.get(day, "未知城市")
    
    return [{
        "type": "delete",
        "day": day,
        "place": rec.get("place", ""),
        "ori_place": rec.get("place", ""),
        "reason": rec.get("reason", ""),
        "city": city  # ← 城市信息
    }]


# ============================================================================
# 或者，如果你有 RECOMMENDATION_HANDLERS 字典，改它：
# ============================================================================

RECOMMENDATION_HANDLERS = {
    "modify": _process_modify_recommendation,
    "add": _process_add_recommendation,
    "delete": _process_delete_recommendation,
}


