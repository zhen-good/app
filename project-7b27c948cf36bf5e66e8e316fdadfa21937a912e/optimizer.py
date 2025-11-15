import copy
from langchain_google_genai import ChatGoogleGenerativeAI
from comment import search_places_by_tag
from convert_trip import convert_trip_to_prompt  # ✅ 使用 Google Maps API 動態查詢地點
from chat_manager import get_user_chain


# 🔧 自動移除避免活動、標記喜好活動
def adjust_trip_by_preferences(trip_data: dict, preferences: dict) -> dict:
    adjusted_trip = copy.deepcopy(trip_data)

    for day in adjusted_trip.get("itinerary", []):
        new_schedule = []
        for item in day.get("schedule", []):
            activity = item["activity"]

            if any(bad in activity for bad in preferences.get("avoid", [])):
                print(f"⚠️ 移除避免活動：{activity}")
                continue

            if any(good in activity for good in preferences.get("prefer", [])):
                item["activity"] += " ⭐"

            new_schedule.append(item)
        day["schedule"] = new_schedule

    return adjusted_trip


# 💬 將 Gemini 回傳整理為建議文字
# def summarize_recommendations(user_id: str, trip_name: str, recommendations: list) -> str:
#     prompt = f"""
# 你是一個旅遊顧問，使用者已建立行程「{trip_name}」，以下是針對其偏好與原行程的建議：

# {chr(10).join(recommendations)}

# 請幫我整理為一段溫和、有條理的建議回覆，並鼓勵使用者採納你給的修改建議。
# """
#     llm = ChatGoogleGenerativeAI(model="gemini-1.5-flash")
#     return llm.invoke(prompt).content

def summarize_recommendations(trip_name: str, recommendations: list) -> str:
    if not recommendations:
        return f"""您好！針對您的「{trip_name}」行程，我已仔細檢視過，發現目前的安排已經非常符合您的偏好，無需進一步調整。\n\n祝您旅途愉快，玩得開心！"""

    result = f"""您好！針對您的「{trip_name}」行程，我有以下幾點優化建議，希望能讓您的旅程更加貼近您的偏好與需求：\n\n"""
    result += "\n\n".join(recommendations)
    result += "\n\n如果您覺得這些建議不錯，可以輸入「更換」立即更新行程內容。祝您旅途愉快！"
    return result




# 🌟 根據偏好標籤，自動查詢 Google Map 推薦景點
def find_similar_place(original: str, preferences: set, location: str = "台北") -> str:
    """
    根據使用者偏好活動標籤，自動查詢並推薦一個替代景點。
    """
    for tag in preferences:
        places = search_places_by_tag(tag, location=location)
        if places:
            print(f"🔍 根據偏好「{tag}」找到：{places[0]['name']}")
            return places[0]["name"]  # 可改成 random.choice(places) 取得更多變化
    return None


import jieba
import copy
from preference import load_user_preferences  # ✅ 動態載入偏好

def get_chat_history(user_id: str) -> str:
    try:
        chain = get_user_chain(user_id)
        messages = chain.memory.chat_memory.messages
        history_text = "\n".join([f"{msg.type}: {msg.content}" for msg in messages])
        return history_text or "無"
    except Exception as e:
        print(f"⚠️ 無法取得聊天紀錄：{e}")
        return "無"

def suggest_trip_modifications(user_id: str, current_trip: dict, location: str) -> dict:
    prefs = load_user_preferences().get(user_id, {})
    prefer_str = "、".join(prefs.get("prefer", [])) or "無"
    avoid_str = "、".join(prefs.get("avoid", [])) or "無"

    itinerary_text = convert_trip_to_prompt(current_trip)
    chat_history = get_chat_history(user_id)

    prompt = f"""
你是一位智慧旅遊顧問，請根據以下使用者偏好、行程與聊天內容，提供具體建議。請考慮語意相近詞（如「博物館」與「博物院」視為同一類），建議刪除與新增哪些活動，並說明原因。

偏好：
喜歡：{prefer_str}
避免：{avoid_str}

行程內容：
{itinerary_text}

使用者聊天紀錄：
{chat_history}

請列出：
1. 應刪除的活動（含理由）
2. 可新增的活動（含理由）

請使用條列格式，一項一項列出建議。
"""

    llm = ChatGoogleGenerativeAI(model="gemini-1.5-flash")
    response = llm.invoke(prompt).content
    recommendations = response.split("\n\n")
    
    return {
        "updated_trip": current_trip,  # ⚠️ 真正修改需另處理
        "recommendations": recommendations,
        "modified_items": []
    }


def ask_to_add_place(place_name: str, location: str = "台北") -> str:
    places = search_places_by_tag(place_name, location=location)
    if places:
        return f"已在 Google 地圖找到「{place_name}」，是否要將此地點加入您的行程？請回覆「加入」或「略過」。"
    else:
        return f"很抱歉，Google 地圖上找不到「{place_name}」，請確認地點名稱或再試一次。"
