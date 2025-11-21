# mongodb_utils.py
import uuid
from pymongo import MongoClient
from bson.objectid import ObjectId
from datetime import datetime

# ✅ 使用你的雲端 MongoDB 連線字串
client = MongoClient("mongodb+srv://Amy:123@cluster0.g54wj9s.mongodb.net/")
db = client["tripDemo-shan"] # 使用你指定的資料庫名稱

# ✅ 重新定義 collection
user_collection = db["users"]
trips_collection = db["structured_itineraries"]
forms_collection = db["forms"]
preferences_collection = db["preferences"]
chat_question = db["question"]
message_collection = db["chat_messages"]

def get_trip_by_id(trip_id):
    """根據 trip_id 取得單一行程資料"""
    return trips_collection.find_one({"trip_id": trip_id})


def add_to_itinerary(trip_id, day, start, end, location, after_place=None):
    """
    新增景點到特定行程、特定日期的鏈結串列中。
    - 如果 after_place 為 None，新增到行程末尾。
    - 否則，新增到指定景點之後。
    """ 
    trip = get_trip_by_id(trip_id)
    if not trip:
        return {"error": "找不到行程"}

    day_data = next((d for d in trip.get("days", []) if d.get("day") == day), None)

    new_attraction_id = ObjectId()
    new_attraction = {
        "_id": new_attraction_id,
        "name": location,
        "start_time": start,
        "end_time": end,
        "next_id": None
    }
    
    # === 如果找不到當天行程，建立一個新的一天 ===
    if not day_data:
        trips_collection.update_one(
            {"trip_id": trip_id},
            {"$push": {
                "days": {
                    "day": day,
                    "head": new_attraction_id,
                    "attractions": [new_attraction]
                }
            }}
        )
        return {"message": "已新增新的天數與景點"}

    # === 新增到指定景點之後或行程末尾 ===
    head_id = day_data.get("head")
    attractions = day_data.get("attractions", [])
    
    prev_id = None
    target_next_id = None
    
    if not head_id: # 行程為空
        trips_collection.update_one(
            {"trip_id": trip_id, "days.day": day},
            {"$set": {"days.$.head": new_attraction_id},
             "$push": {"days.$.attractions": new_attraction}}
        )
        return {"message": "已新增景點到空行程"}

    current_id = head_id
    while current_id:
        current_attraction = next((attr for attr in attractions if attr.get("_id") == current_id), None)
        if not current_attraction:
            return {"error": "行程資料鏈結錯誤"}
        
        if after_place and current_attraction.get("name") == after_place:
            prev_id = current_id
            target_next_id = current_attraction.get("next_id")
            break
        
        if not current_attraction.get("next_id"):
            prev_id = current_id
            break

        current_id = current_attraction.get("next_id")

    new_attraction["next_id"] = target_next_id
    
    # 執行更新操作 (分成兩步)
    # A. 增加新的景點到 attractions 陣列
    trips_collection.update_one(
        {"trip_id": trip_id, "days.day": day},
        {"$push": {"days.$.attractions": new_attraction}}
    )

    # B. 更新前一個景點的 next_id
    if prev_id:
        trips_collection.update_one(
            {"trip_id": trip_id, "days.day": day, "days.attractions._id": prev_id},
            {"$set": {"days.$[day].attractions.$[attraction].next_id": new_attraction_id}},
            array_filters=[{"day.day": day}, {"attraction._id": prev_id}]
        )

    return {"message": f"已在 {after_place or '行程末尾'} 之後新增景點"}
        
import re
from typing import Dict, Any, Optional

def delete_from_itinerary(trip_id: str, day: int, place_name: str) -> Dict[str, Any]:
    """
    依新結構刪除地點：
    - 先以 day 找到該日 head_id，沿著 nodes 的鏈結（next_id）走訪
    - 找到第一個其 places[*].name 包含 place_name 的 node
      * 若該 node 的 places > 1：只 pull 這個 place
      * 若 places == 1：刪除整個 node，並修補鏈結
    回傳：{"message": "..."} 或 {"error": "..."}
    """
    trip = trips_collection.find_one({"trip_id": trip_id}, {"days": 1, "nodes": 1})
    if not trip:
        return {"error": "找不到行程"}

    days = trip.get("days") or []
    nodes = trip.get("nodes") or []
    day_meta = next((d for d in days if d.get("day") == int(day)), None)
    if not day_meta:
        return {"error": f"找不到第 {day} 天"}

    node_map = {n.get("node_id"): n for n in nodes}
    head_id: Optional[str] = day_meta.get("head_id")
    if not head_id:
        return {"error": f"第 {day} 天尚未安排"}

    # 走訪鏈結，找第一個符合的 node/place
    prev_id = None
    curr_id = head_id
    target_node = None
    target_place_name = None

    # 用部分比對（大小寫不敏感）
    pattern = re.compile(re.escape(place_name), re.IGNORECASE)

    while curr_id:
        node = node_map.get(curr_id)
        if not node:
            break
        # 檢查 places
        for p in (node.get("places") or []):
            nm = p.get("name") or ""
            if pattern.search(nm):
                target_node = node
                target_place_name = nm  # 抓到實際名稱以精準刪
                break
        if target_node:
            break
        prev_id = curr_id
        curr_id = node.get("next_id")

    if not target_node:
        return {"error": f"找不到景點：{place_name}"}

    places = target_node.get("places") or []
    node_id = target_node.get("node_id")
    next_id = target_node.get("next_id")

    # 情況 A：node 內還有多個 place → 只刪該 place
    if len(places) > 1:
        res = trips_collection.update_one(
            {"trip_id": trip_id, "nodes.node_id": node_id},
            {"$pull": {"nodes.$.places": {"name": target_place_name}}}
        )
        if res.modified_count == 0:
            return {"error": "刪除失敗，可能該地點已被移除"}
        return {"message": f"已刪除：{target_place_name}"}

    # 情況 B：node 內只剩這個 place → 刪整個 node 並修補鏈結
    # B-1) 若刪的是 head：更新 days.$.head_id = next_id
    if prev_id is None:
        res1 = trips_collection.update_one(
            {"trip_id": trip_id, "days.day": int(day)},
            {"$set": {"days.$.head_id": next_id}}
        )
        if res1.matched_count == 0:
            return {"error": "更新 head_id 失敗"}

    # B-2) 若刪的是中間/尾端：把 prev.next_id → 指向 next_id
    else:
        res2 = trips_collection.update_one(
            {"trip_id": trip_id, "nodes.node_id": prev_id},
            {"$set": {"nodes.$.next_id": next_id}}
        )
        if res2.matched_count == 0:
            return {"error": "更新前一節點的 next_id 失敗"}

    # B-3) 從 nodes 陣列移除整個 node
    res3 = trips_collection.update_one(
        {"trip_id": trip_id},
        {"$pull": {"nodes": {"node_id": node_id}}}
    )
    if res3.modified_count == 0:
        return {"error": "移除節點失敗"}

    return {"message": f"已刪除節點（含唯一地點）：{target_place_name}"}

def modify_itinerary(trip_id: str, day: int, place_id: str, new_place):
    """
    以 place_id 精準更新單一筆 place。
    """
    # 嘗試將 trip_id 轉換為 ObjectId
    try:
        trip_id_ob = ObjectId(trip_id)
    except Exception:
        # 如果傳入的不是有效的 ObjectId 字串，則當作普通字串處理
        trip_id_ob = trip_id

    if isinstance(new_place, str):
        update_doc = {
            "$set": {
                "nodes.$[node].places.$[p].name": new_place
            }
        }
    elif isinstance(new_place, dict):
        allowed_keys = {
            "place_id", "name", "category", "stay_minutes", "rating", "reviews",
            "address", "map_url", "open_text", "types", "lat", "lng",
            "source", "raw_name", "_behavior_score" # 增加一個 '_behavior_score'
        }
        set_fields = {f"nodes.$[node].places.$[p].{k}": v
                      for k, v in new_place.items() if k in allowed_keys}
        if not set_fields:
            raise ValueError("new_place(dict) 需至少包含一個允許的欄位")
        update_doc = {"$set": set_fields}
    else:
        raise TypeError("new_place 必須是 str 或 dict")

    # 🚨 關鍵修正：將查詢條件從 {"trip_id": trip_id} 改為 {"_id": trip_id_ob}
    res = trips_collection.update_one(
        {"_id": trip_id_ob},  
        update_doc,
        array_filters=[
            {"node.day": int(day)},
            {"p.place_id": place_id}
        ]
    )

    # 💡 建議輸出結果，方便追蹤
    print(f"DB Update Result: Matched={res.matched_count}, Modified={res.modified_count}")

    return {
        "ok": res.acknowledged,
        "matched": res.matched_count,
        "modified": res.modified_count
    }

def save_recommendation(trip_id, recommendation):
    """儲存建議到 MongoDB"""
    trips_collection.insert_one({
        "trip_id": trip_id,
        "recommendation": recommendation,
        "timestamp": datetime.utcnow()
    })
    return {"message": "建議已儲存"}

def clear_all_data():
    """清除所有 MongoDB 資料 (僅供測試用)"""
    user_collection.delete_many({})
    trips_collection.delete_many({})
    print("✅ 已清除所有使用者和行程資料。")



#-------------------------------#
#存問過的問題
#-------------------------------
def ensure_trip(trip_id: str):
    now = datetime.utcnow()
    chat_question.update_one(
        {"_id": str(trip_id)},
        {"$setOnInsert": {
            "trip_id": str(trip_id),     # 若不想存這欄可拿掉
            "state_by_user": {},
            "created_at": now,
            "updated_at": now
        }},
        upsert=True
    )

def ensure_user_slot(trip_id: str, user_id: str):
    """若該 trip 下的 user 子文件不存在，初始化一份。"""
    ensure_trip(trip_id)
    now = datetime.utcnow()
    # 用聚合式更新初始化（MongoDB 4.2+ 支援）
    chat_question.update_one(
        {"_id": str(trip_id)},
        [
            {"$set": {
                f"state_by_user.{user_id}": {
                    "$ifNull": [ f"$state_by_user.{user_id}", {
                        "asked_keys": [],
                        "last_question_key": None,
                        "selected_values": [],
                        "asked_options_history": {},
                        "known_prefs": {},
                        "updated_at": now
                    }]
                },
                "updated_at": now
            }}
        ]
    )

def get_user_state(trip_id: str, user_id: str) -> dict:
    doc = chat_question.find_one({"_id": str(trip_id)}, {"state_by_user."+user_id: 1, "_id": 0})
    return ((doc or {}).get("state_by_user") or {}).get(user_id) or {
        "asked_keys": [],
        "last_question_key": None,
        "selected_values": [],
        "asked_options_history": {},
        "known_prefs": {}
    }

def get_username(user_id: str):
    """取得使用者名稱"""
    try:
        from mongodb_utils import user_collection
        
        if ObjectId.is_valid(user_id):
            user = user_collection.find_one({"_id": ObjectId(user_id)})
            if user:
                return user.get("username", user.get("email", "Unknown"))
    except:
        pass
    return "Unknown"


def save_message_to_mongodb(trip_id: str, user_id: str, role: str, content: str):
    """
    儲存訊息到 MongoDB (chat_messages collection)
    
    Args:
        trip_id: 行程 ID
        user_id: 使用者 ID
        role: "user" 或 "assistant"
        content: 訊息內容
    
    Returns:
        bool: 儲存是否成功
    """
    try:
        # 取得使用者名稱
        username = get_username(user_id) if role == "user" else "AI助手"
        
        # 建立訊息物件
        message = {
            "message_id": str(uuid.uuid4()),  # 生成唯一 ID
            "user_id": user_id,
            "username": username,
            "role": role,
            "content": content,
            "timestamp": datetime.now()
        }
        
        print(f"💾 儲存訊息: trip_id={trip_id}, [{username}] {content[:30]}...")
        
        # 使用 upsert: 如果文檔不存在就創建,存在就更新
        result = message_collection.update_one(
            {"trip_id": trip_id},  # 查找條件
            {
                "$push": {
                    "chat_history": message  # 將訊息加入陣列
                },
                "$setOnInsert": {
                    "trip_id": trip_id,
                    "created_at": datetime.now()
                },
                "$set": {
                    "updated_at": datetime.now()
                }
            },
            upsert=True  # 如果不存在就創建
        )
        
        if result.matched_count > 0 or result.upserted_id:
            print("result",result)
            print("result.matched_count",result.matched_count)
            print("result.upserted_id",result.upserted_id)
            print(f"✅ 訊息已儲存")
            return True
        else:
            print(f"⚠️ 儲存異常")
            return False
        
    except Exception as e:
        print(f"❌ 儲存訊息失敗: {e}")
        import traceback
        traceback.print_exc()
        return False
    