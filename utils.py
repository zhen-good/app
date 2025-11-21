# utils.py
import json
import os
import re
from bson import ObjectId
from langchain_core.messages import messages_from_dict
from langchain_openai import ChatOpenAI
from config import MEMORY_FOLDER, OPENAI_API_KEY
from langchain.chains.conversation.base import ConversationChain
from langchain.memory import ConversationBufferMemory
from mongodb_utils import trips_collection

user_chains = []

def get_user_chain(user_id: str):
    if user_id not in user_chains:
        llm = ChatOpenAI(
            model="gpt-4o-mini",  # ✅
            api_key=OPENAI_API_KEY
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


def load_memory(user_id: str):
    path = os.path.join(MEMORY_FOLDER, f"memory_{user_id}.json")
    if os.path.exists(path):
        print(f"🔍 載入記憶檔案：{path}")
        with open(path, "r", encoding="utf-8") as f:
            return messages_from_dict(json.load(f))
    print(f"⚠️ 找不到記憶檔案：{path}")
    return []


def extract_json(text: str):
    """
    從文字中抽取第一個合法 JSON 區塊，並轉成 Python 物件 (dict 或 list) 回傳。
    """
    cleaned_text = text.replace('\xa0', ' ').strip()
    
    pattern_code_block = re.compile(r'```json\s*(.*?)\s*```', re.DOTALL)
    match = pattern_code_block.search(cleaned_text)
    
    if match:
        json_str = match.group(1).strip()
        try:
            parsed_json = json.loads(json_str)
            print(f"✅ 從 ```json 區塊中成功解析：{json_str[:50]}...")
            return parsed_json
        except json.JSONDecodeError as e:
            print(f"❌ 從 ```json 區塊中解析 JSON 失敗：{e}")
    
    try:
        parsed_json = json.loads(cleaned_text)
        print("✅ 直接解析為 JSON 成功！")
        return parsed_json
    except json.JSONDecodeError:
        print("⚠️ json.loads 直接解析失敗，嘗試尋找最外層的 {} 或 []...")
    
    general_json_matches = re.findall(r'(\[.*\]|\{.*\})', cleaned_text, re.DOTALL)
    if general_json_matches:
        for json_str in general_json_matches:
            try:
                parsed_json = json.loads(json_str)
                print(f"✅ 從一般文本中成功解析：{json_str[:50]}...")
                return parsed_json
            except Exception as e:
                print(f"❌ 解析一般文本區塊失敗：{e}，內容開頭：{json_str[:50]}...")
                continue

    print("❌ 所有 JSON 提取嘗試均失敗。")
    return None

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