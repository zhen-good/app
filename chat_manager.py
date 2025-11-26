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
def analyze_active_users_preferences(user_id: str, user_chains: dict, trip_id_ob: ObjectId) -> list:
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
        
        # 🆕 提取每天的城市信息
        day_map = {}
        for i, day_data in enumerate(days, 1):
            city = day_data.get('city', '未知城市')
            day_map[i] = city
        
        existing_days = sorted(day_map.keys())
        day_list_str = ", ".join(map(str, existing_days))
        
        print(f"✅ 已建立 day_map：{day_map}")
        
        # 🆕 提取目前行程中所有景點的名稱和地址（用於去重）
        existing_places = []
        existing_addresses = set()  # 👈 用地址去重
        
        for day_data in days:
            places = day_data.get('places', [])
            for place in places:
                place_name = place.get('name', '').strip()
                place_address = place.get('address', '').strip()
                
                if place_name:
                    existing_places.append(place_name)
                
                if place_address:
                    # 標準化地址（移除空白、統一大小寫）
                    normalized_address = place_address.replace(' ', '').lower()
                    existing_addresses.add(normalized_address)
        
        existing_places_str = "\n".join([f"- {p}" for p in existing_places]) if existing_places else "- 無現有景點"
        print(f"📍 目前行程中的景點：{existing_places}")
        print(f"🏠 目前行程中的地址數量：{len(existing_addresses)}")
        
        # 2️⃣ 取得偏好和聊天紀錄
        trip_preferences = load_preferences_by_trip_id(trip_id_ob)
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
        
        city_info = "📍 行程城市分布：\n" + "\n".join([f"- Day {day}: {city}" for day, city in sorted(day_map.items())])
        
        day_limit_rule = f"**新增 (add) 和修改 (modify) 建議只能在行程中已存在的 Day 進行。請從以下天數中選一個：[{day_list_str}]**"
        
        prompt = f"""
你是一位智慧旅遊顧問，擅長從使用者偏好中挖掘深層興趣，並提供多元化的推薦。

{city_info}

🧠 **使用者明確偏好：**
✅ 喜歡：{', '.join(all_prefer) if all_prefer else '無'}
❌ 避免：{', '.join(all_avoid) if all_avoid else '無'}

🚫 **目前行程中已存在的景點（請勿重複推薦）：**
{existing_places_str}

=== 🎯 推薦策略：多元化且避免重複 ===

**重要提醒：**
- 系統會用你提供的關鍵字在 Google Maps 搜尋景點
- **請提供差異化的關鍵字組合**，避免搜到相同景點
- 例如：避免同時使用 ["咖啡廳"] 和 ["特色咖啡廳"]，因為可能搜到同一家店
- 建議使用更具體的修飾詞：["日式咖啡廳"]、["海景咖啡廳"]、["老屋咖啡廳"]

**階段一：深度分析使用者偏好**
推測使用者的深層興趣：

範例推測邏輯：
- 喜歡「山」→ 可能喜歡「大自然」→ 也可能喜歡「海邊」、「森林步道」、「瀑布」、「湖泊」
- 喜歡「咖啡廳」→ 可能喜歡「放鬆氛圍」→ 也可能喜歡「獨立書店」、「茶藝館」、「文青空間」
- 喜歡「夜市」→ 可能喜歡「熱鬧」→ 也可能喜歡「傳統市集」、「商店街」、「文創市集」
- 喜歡「博物館」→ 可能喜歡「文化知識」→ 也可能喜歡「古蹟」、「美術館」、「文化園區」

**階段二：多元化推薦（避免重複）**
根據以下比例生成建議：
- 📌 **75% 直接符合偏好**：使用不同角度的關鍵字（如：日式咖啡廳、海景咖啡廳、老屋咖啡廳）
- 🎲 **25% 推測相關偏好**：基於深層興趣，提供相關但不同類型的選項

**關鍵字多樣化技巧：**
1. 加入特定風格：「日式」、「歐式」、「復古」、「現代」
2. 加入地理特色：「海景」、「山景」、「河岸」、「老街」
3. 加入體驗類型：「親子」、「網美」、「秘境」、「在地」
4. 組合不同維度：「日式老屋咖啡廳」vs「海景玻璃屋咖啡廳」

**階段三：處理衝突景點**
1. 檢查行程中與「避免」偏好衝突的景點
2. 提供「修改」建議
3. 提供「刪除」建議（嚴重衝突）

=== ⚠️ 重要規則 ===
- {day_limit_rule}
- **絕對不可推薦「目前行程中已存在的景點」**
- **每個 search_keywords 組合必須顯著不同，避免搜到相同景點**
- **推測相關偏好時，不要使用原偏好關鍵字**
- 優先處理「避免」偏好衝突
- search_keywords 要具體且差異化

=== 📋 建議格式 ===
```json
[
    {{
        "type": "modify",
        "day": 1,
        "place": "原景點名稱",
        "search_keywords": ["具體關鍵字1", "具體關鍵字2"],
        "reason": "此景點與使用者避免的XX偏好衝突",
        "recommendation_type": "direct"
    }},
    {{
        "type": "add",
        "day": 1,
        "search_keywords": ["日式老屋咖啡廳"],
        "reason": "直接符合使用者喜歡「咖啡廳」的偏好，使用日式老屋特色避免重複",
        "recommendation_type": "direct"
    }},
    {{
        "type": "add",
        "day": 1,
        "search_keywords": ["海景玻璃屋咖啡廳"],
        "reason": "直接符合使用者喜歡「咖啡廳」的偏好，使用海景特色避免重複",
        "recommendation_type": "direct"
    }},
    {{
        "type": "add",
        "day": 2,
        "search_keywords": ["獨立書店"],
        "reason": "從「喜歡咖啡廳」推測使用者喜歡放鬆文青氛圍，建議嘗試書店",
        "recommendation_type": "inferred"
    }}
]
```

=== 📊 使用者偏好詳情 ===
🧠 整體喜好：
{prefer_list}

⚠️ 整體避免：
{avoid_list}

=== 🗺️ 目前行程內容 ===
{trip_text}

=== 💬 聊天記錄參考 ===
{combined_text[:1000]}

=== 🎯 執行步驟 ===
1. **推測深層興趣**：分析「喜歡」偏好背後的興趣
2. **設計差異化關鍵字**：確保每組關鍵字會搜到不同景點
3. **掃描衝突**：找出與「避免」偏好衝突的景點
4. **生成建議**：按 3:1 比例，使用差異化關鍵字
5. **品質檢查**：
   - ✅ 關鍵字組合顯著不同
   - ✅ 不在現有行程中
   - ✅ 具體且可搜尋

現在請開始分析！
"""
        
        # 4️⃣ 呼叫 LLM
        print("🧠 準備呼叫 LLM 進行分析...")
        print("\n--- LLM 分析 Prompt 內容開始 ---")
        print(prompt) 
        print("--- LLM 分析 Prompt 內容結束 ---\n")

        analysis_llm = ChatOpenAI(
            model="gpt-4o-mini",
            api_key=OPENAI_API_KEY
        )
        response = analysis_llm.invoke(prompt).content
        print("📩 LLM 回應已取得", response)
        
        # 5️⃣ 解析建議
        recommendations = extract_json(response)
        if not isinstance(recommendations, list):
            print("❌ LLM 回應無法解析為有效的列表結構")
            return []
        elif not recommendations:
            print("✅ LLM 成功解析，但沒有返回任何建議 ([])")
            return []
        
        # 6️⃣ 處理每個建議，並根據地址去重
        processed_recommendations = []
        recommended_addresses = set()  # 👈 記錄已推薦的地址
        type_counts = {}
        
        print(f"\n🔄 開始處理 {len(recommendations)} 個建議...")
        
        for idx, rec in enumerate(recommendations, 1):
            if not isinstance(rec, dict):
                continue
            
            rec_type = rec.get('type')
            handler = RECOMMENDATION_HANDLERS.get(rec_type)
            
            if handler:
                print(f"\n處理建議 [{idx}] - 類型: {rec_type}")
                
                # 調用 handler 獲取結果
                if rec_type == 'modify':
                    result = handler(user_id, trip_id_ob, rec, day_map)
                else:
                    result = handler(user_id, trip_id_ob, rec, day_map)
                
                # 🔍 對返回的結果進行地址去重
                if result:
                    for item in result:
                        if not isinstance(item, dict):
                            continue
                        
                        # 取得地址
                        item_address = item.get('address', '').strip()
                        
                        if not item_address:
                            print(f"   ⚠️ 景點無地址，跳過去重檢查")
                            processed_recommendations.append(item)
                            continue
                        
                        # 標準化地址
                        normalized_address = item_address.replace(' ', '').lower()
                        
                        # 檢查是否與原行程重複
                        if normalized_address in existing_addresses:
                            print(f"   ❌ 跳過：景點 '{item.get('name')}' 與原行程重複")
                            print(f"      地址: {item_address}")
                            continue
                        
                        # 檢查是否與已推薦的重複
                        if normalized_address in recommended_addresses:
                            print(f"   ❌ 跳過：景點 '{item.get('name')}' 與其他推薦重複")
                            print(f"      地址: {item_address}")
                            continue
                        
                        # ✅ 不重複，加入結果
                        print(f"   ✅ 加入：景點 '{item.get('name')}'")
                        print(f"      地址: {item_address}")
                        processed_recommendations.append(item)
                        recommended_addresses.add(normalized_address)
        
        # 7️⃣ 統計結果
        print(f"\n📋 處理結果統計：")
        print(f"   原始建議數: {len(recommendations)}")
        print(f"   最終建議數: {len(processed_recommendations)}")
        print(f"   過濾重複數: {len(recommendations) - len(processed_recommendations)}")

        for rec in processed_recommendations:
            if isinstance(rec, dict) and 'type' in rec:
                rec_type = rec['type']
                type_counts[rec_type] = type_counts.get(rec_type, 0) + 1

        print(f"\n📊 建議統計：{type_counts}")
        
        # 統計推薦類型
        direct_count = sum(1 for r in processed_recommendations if r.get('recommendation_type') == 'direct')
        inferred_count = sum(1 for r in processed_recommendations if r.get('recommendation_type') == 'inferred')
        print(f"📊 推薦類型：直接 {direct_count} | 推測 {inferred_count}")
        
        print(f"✅ 成功生成 {len(processed_recommendations)} 個不重複建議\n")
        
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


def decide_location_placement(user_id: str, trip_id_ob, place: str):
    """
    決定新地點應該放在行程的哪一天、哪個時段
    """
    try:
        
        analysis_llm = ChatOpenAI(
            model="gpt-4o-mini",
            api_key=OPENAI_API_KEY,
            temperature=0.3
        )

        chain = get_user_chain(user_id)
        chat_history = "\n".join([
            f"{msg.type}: {msg.content}"
            for msg in chain.memory.chat_memory.messages
        ])
        itinerary_text = display_trip_by_trip_id(trip_id_ob)

        from preference import load_preferences_by_trip_id
        all_preferences = load_preferences_by_trip_id(trip_id_ob)
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
        response = analysis_llm.invoke(prompt).content

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

def _process_modify_recommendation(user_id, trip_id_ob,rec, day_map):  # ← 加入 trip_id_ob
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



def _process_add_recommendation(user_id, trip_id_ob, rec, day_map):
    """處理「新增」類型建議：根據關鍵字搜尋景點作為新增選項"""
    
    # 1. 💡 類型轉換和檢查 (防禦性程式設計，處理 LLM 輸出 Day 格式不正確的情況)
    raw_day = rec.get("day")
    day = None
    try:
        if raw_day is not None:
            day = int(raw_day)
    except (ValueError, TypeError):
        pass 
    
    if not isinstance(day, int) or day <= 0:
        print(f"⚠️ ADD 建議結構錯誤：缺少有效的 Day 參數。原始值為：{raw_day}")
        print(f"{'='*50}\n")
        return []

    # 確保 City 存在
    city = day_map.get(day)
    if not city:
        print(f"⚠️ 找不到 Day {day} 對應的城市資訊。跳過此 ADD 建議。")
        print(f"{'='*50}\n")
        return []
    
    search_keywords = rec.get("search_keywords", [])
    new_places = []
    
    print(f"\n{'='*50}")
    print(f"➕ 處理 add 建議")
    print(f"   Day: {day}")
    print(f"   City: {city}")
    print(f"   關鍵字: {search_keywords}")
    
    # ✅ 執行搜尋新景點
    if search_keywords:
        for keyword in search_keywords:
            query = f"{city} {keyword}"
            print(f"🔍 Google Maps 查詢：{query}")
            
            # 呼叫搜尋函式 (假設 search_places_by_tag 已在作用域內)
            places = search_places_by_tag(query) 
            
            if places:
                print(f"📍 找到 {len(places)} 個景點")
                
                # ✅ 取前 3 個結果並格式化 (景點越多，越有機會取到最優的)
                for place in places[:3]: 
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
    
    # =========================================================
    # 📌 關鍵修正區塊：去重和強制限制為 1 個景點
    # =========================================================
    if not new_places:
        print(f"⚠️ 找不到任何適合新增的景點，跳過此建議")
        print(f"{'='*50}\n")
        return []

    # 1. 執行去重：使用 place_id 確保每個景點只出現一次
    unique_places = {}
    for place in new_places:
        place_id = place.get("place_id")
        if place_id and place_id not in unique_places:
            unique_places[place_id] = place

    # 2. 嚴格限制為 1 個最相關的景點 (符合您的需求)
    new_places = list(unique_places.values())[:1]
    
    print(f"✅ 經過去重與限制後，最終保留 {len(new_places)} 個景點選項。")

    # 防禦性檢查：如果限制後列表為空，則返回 []
    if not new_places:
        print(f"⚠️ 限制為 1 個選項後列表為空，跳過此建議。")
        print(f"{'='*50}\n")
        return []
    # =========================================================

    print(f"✅ 總共準備了 {len(new_places)} 個新增景點選項") # 這裡現在應該只會是 1

    # 5. ✅ 步驟 決定新景點的位置
    day_to_add = None
    slot_to_add = None
    action_to_add = None
    node_id_ref_to_add = None
    
    # 💡 選擇唯一剩下的景點作為 LLM 判斷位置的依據
    top_place = new_places[0].get("name")
    print(f"🧠 嘗試為首選景點 '{top_place}' 決定位置...")
    
    # 呼叫您的位置決策函數
    placement = decide_location_placement(user_id, trip_id_ob, top_place)

    
    # 轉換 period 為 slot 
    day_to_add = placement.get("day")
    slot_to_add = placement.get("period") 
    
    # 捕捉 LLM 判斷的關鍵欄位
    action_to_add = placement.get("action")
    node_id_ref_to_add = placement.get("node_id_ref")

    if day_to_add and slot_to_add:
        print(f"✅ LLM 建議插入位置：Day {day_to_add}, 時段 {slot_to_add}")
        print(f"   動作/節點：{action_to_add} / {node_id_ref_to_add}")
    else:
        print("⚠️ LLM 無法決定最佳插入位置或結果不完整。")
    
    # 📌 關鍵修正：確保 Action 和 Node ID 欄位存在
    if not action_to_add:
        action_to_add = "APPEND" 
        node_id_ref_to_add = None 
        print(f"🛠️ 強制設定為預設動作：{action_to_add}")

    # 確保 day_to_add 有一個合理的預設值
    if not day_to_add:
        day_to_add = day
        slot_to_add = "上午"
        print(f"🛠️ 強制設定為預設 Day/Slot：Day {day_to_add}, {slot_to_add}")
        
    # 6. ✅ 返回完整的建議結構
    final_recommendation = [{
        "type": "add",
        "day": day,
        "new_places": new_places, # 這裡只有 1 個景點
        "reason": rec.get("reason", ""),
        "city": city,
        # 📌 關鍵：新增位置資訊 
        "recommend_day": day_to_add, 
        "recommend_slot": slot_to_add,
        "recommend_action": action_to_add,
        "recommend_node_id": node_id_ref_to_add
    }]
    
    print(f"🎉 ADD 建議處理完成，準備返回。")
    print(f"   首個景點名稱：{new_places[0].get('name')}")
    print(f"   最終返回的 Action: {action_to_add}")
    print(f"{'='*50}\n")
    
    return final_recommendation


def _process_delete_recommendation(ser_id, trip_id,rec, day_map):
    """處理「刪除」類型建議"""
    
    day = rec.get("day")
    
    # 1. 從 day_map 獲取城市資訊 (Day map: Day -> City name)
    city = day_map.get(day, "未知城市")
    
    print(f"\n{'='*50}")
    print(f"➖ 處理 delete 建議")
    print(f"   Day: {day}")
    print(f"   City: {city}")
    print(f"   景點: {rec.get('place', '未知景點')}")
    print(f"{'='*50}\n")
    
    # 2. 返回包含 city 欄位的建議結構
    return [{
        "type": "delete",
        "day": day,
        "place": rec.get("place", ""),
        "ori_place": rec.get("place", ""),
        "reason": rec.get("reason", ""),
        "city": city # ✅ 新增城市資訊
    }]

# ============================================================================
# 或者，如果你有 RECOMMENDATION_HANDLERS 字典，改它：
# ============================================================================

RECOMMENDATION_HANDLERS = {
    "modify": _process_modify_recommendation,
    "add": _process_add_recommendation,
    "delete": _process_delete_recommendation,
}


