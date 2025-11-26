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


from bson.objectid import ObjectId # 確保有導入

def add_to_itinerary(trip_id_ob, day, new_place_data, action, node_id_ref=None):
    """
    新增景點到特定行程、特定日期的鏈結串列中。
    - new_place_data: 包含 name, place_id, lat, lng 等完整景點資料的字典。
    - action: 插入動作 ("APPEND", "BEFORE", "AFTER")。
    - node_id_ref: 參考景點的 _id (MongoDB ObjectId)。
    """ 
    
    # 🔍 診斷信息
    print(f"\n{'='*60}")
    print(f"🔍 開始新增景點")
    print(f"{'='*60}")
    print(f"   trip_id_ob: {trip_id_ob} (類型: {type(trip_id_ob)})")
    print(f"   day: {day}")
    print(f"   action: {action}")
    print(f"   景點名稱: {new_place_data.get('name')}")
    
    # 1️⃣ 查詢行程
    trip = trips_collection.find_one({"_id": trip_id_ob})
    
    print(f"\n1️⃣ 查詢結果：")
    if trip:
        print(f"   ✅ 找到行程")
        print(f"   行程標題: {trip.get('title')}")
        print(f"   天數: {len(trip.get('days', []))}")
    else:
        print(f"   ❌ 找不到行程")
        # 🔍 額外診斷
        print(f"\n   診斷：嘗試其他查詢方式...")
        
        # 嘗試用字串查詢
        trip_by_str = trips_collection.find_one({"_id": str(trip_id_ob)})
        if trip_by_str:
            print(f"   ⚠️ 用字串可以找到！MongoDB 的 _id 可能是字串格式")
            trip = trip_by_str
        else:
            # 檢查資料庫中有沒有任何文檔
            sample = trips_collection.find_one()
            if sample:
                print(f"   資料庫中有文檔，_id 類型: {type(sample.get('_id'))}")
                print(f"   _id 值: {sample.get('_id')}")
            else:
                print(f"   資料庫是空的")
            
            return {"error": "找不到行程"}

    # 2️⃣ 找到對應的 day
    day_data = next((d for d in trip.get("days", []) if d.get("day") == day), None)
    
    print(f"\n2️⃣ 查找 Day {day}：")
    if day_data:
        print(f"   ✅ 找到 Day {day}")
        print(f"   城市: {day_data.get('city')}")
        print(f"   現有景點數: {len(day_data.get('attractions', []))}")
    else:
        print(f"   ❌ 找不到 Day {day}")

    # 3️⃣ 構建新的景點物件
    new_attraction_id = ObjectId()
    new_attraction = {
        "_id": new_attraction_id,
        "name": new_place_data.get("name"),
        "place_id": new_place_data.get("place_id"),
        "address": new_place_data.get("address"),
        "lat": new_place_data.get("lat"),
        "lng": new_place_data.get("lng"),
        "start_time": "??:??", 
        "end_time": "??:??",
        "next_id": None
    }
    
    print(f"\n3️⃣ 新景點資料：")
    print(f"   _id: {new_attraction_id}")
    print(f"   名稱: {new_attraction['name']}")
    print(f"   地址: {new_attraction['address']}")
    
    # === 如果找不到當天行程，建立新的一天 ===
    if not day_data:
        print(f"\n4️⃣ 建立新的 Day {day}")
        
        result = trips_collection.update_one(
            {"_id": trip_id_ob},
            {"$push": {
                "days": {
                    "day": day,
                    "date": trip.get("start_date"),  # 你可能需要計算正確的日期
                    "city": new_place_data.get("city", "未知城市"),
                    "head_id": str(new_attraction_id),  # 根據你的結構，head_id 是字串
                    "head": new_attraction_id,  # head 是 ObjectId
                    "attractions": [new_attraction]
                }
            }}
        )
        
        print(f"   更新結果: matched={result.matched_count}, modified={result.modified_count}")
        
        if result.modified_count > 0:
            print(f"   ✅ 成功建立新天數並新增景點")
        else:
            print(f"   ❌ 更新失敗")
        
        return {"message": "已新增新的天數與景點", "attraction_id": str(new_attraction_id)}

    # === 處理已存在的天數 ===
    attractions = day_data.get("attractions", [])
    head_id = day_data.get("head")  # 這是 ObjectId
    
    print(f"\n4️⃣ 處理已存在的 Day {day}")
    print(f"   head_id: {head_id}")
    print(f"   現有景點數: {len(attractions)}")

    # 1. 行程為空，設定 head
    if not attractions:
        print(f"\n   情況：行程為空")
        
        result = trips_collection.update_one(
            {"_id": trip_id_ob, "days.day": day},
            {
                "$set": {
                    "days.$.head": new_attraction_id,
                    "days.$.head_id": str(new_attraction_id)
                },
                "$push": {"days.$.attractions": new_attraction}
            }
        )
        
        print(f"   更新結果: matched={result.matched_count}, modified={result.modified_count}")
        
        if result.modified_count > 0:
            print(f"   ✅ 成功新增景點到空行程")
        
        return {"message": "已新增景點到空行程", "attraction_id": str(new_attraction_id)}

    # 2. 決定 prev_id 和 target_next_id
    prev_id = None
    target_next_id = None
    
    print(f"\n5️⃣ 決定插入位置 (action={action})")
    
    # action: BEFORE (插入到 node_id_ref 之前)
    if action == "BEFORE" and node_id_ref:
        print(f"   插入到 {node_id_ref} 之前")
        
        if node_id_ref == head_id:
            # 插入到 head 之前，更新 head
            print(f"   → 插入到 head 之前")
            new_attraction["next_id"] = node_id_ref
            
            trips_collection.update_one(
                {"_id": trip_id_ob, "days.day": day},
                {
                    "$set": {
                        "days.$.head": new_attraction_id,
                        "days.$.head_id": str(new_attraction_id)
                    }
                }
            )
        else:
            # 遍歷找到前一個節點
            current_id = head_id
            while current_id:
                current_attraction = next((attr for attr in attractions if attr.get("_id") == current_id), None)
                if not current_attraction: 
                    break
                
                if current_attraction.get("next_id") == node_id_ref:
                    prev_id = current_id
                    target_next_id = node_id_ref
                    print(f"   → 找到前一個節點: {prev_id}")
                    break
                current_id = current_attraction.get("next_id")

    # action: AFTER (插入到 node_id_ref 之後)
    elif action == "AFTER" and node_id_ref:
        print(f"   插入到 {node_id_ref} 之後")
        
        ref_attraction = next((attr for attr in attractions if attr.get("_id") == node_id_ref), None)
        if ref_attraction:
            prev_id = node_id_ref
            target_next_id = ref_attraction.get("next_id")
            print(f"   → prev_id: {prev_id}, target_next_id: {target_next_id}")
            
    # action: APPEND (新增到末尾)
    else:
        print(f"   新增到末尾")
        
        current_id = head_id
        while current_id:
            current_attraction = next((attr for attr in attractions if attr.get("_id") == current_id), None)
            if not current_attraction: 
                break
            
            if not current_attraction.get("next_id"):
                # 找到末尾
                prev_id = current_id
                target_next_id = None
                print(f"   → 找到末尾節點: {prev_id}")
                break
            current_id = current_attraction.get("next_id")

    # 3. 執行更新操作
    print(f"\n6️⃣ 執行更新")
    
    # 設置新節點的 next_id
    new_attraction["next_id"] = target_next_id
    print(f"   新景點的 next_id: {target_next_id}")
    
    # A. 增加新的景點到 attractions 陣列
    result1 = trips_collection.update_one(
        {"_id": trip_id_ob, "days.day": day},
        {"$push": {"days.$.attractions": new_attraction}}
    )
    
    print(f"   步驟 A - 新增景點: matched={result1.matched_count}, modified={result1.modified_count}")

    # B. 更新前一個景點的 next_id (如果 prev_id 存在)
    if prev_id:
        print(f"   步驟 B - 更新前一個景點 {prev_id} 的 next_id")
        
        # 使用 Array Filters 進行精確更新
        result2 = trips_collection.update_one(
            {"_id": trip_id_ob, "days.day": day},
            {"$set": {"days.$[day].attractions.$[attraction].next_id": new_attraction_id}},
            array_filters=[
                {"day.day": day}, 
                {"attraction._id": prev_id}
            ]
        )
        
        print(f"   更新結果: matched={result2.matched_count}, modified={result2.modified_count}")
    
    # 7️⃣ 驗證更新
    print(f"\n7️⃣ 驗證更新")
    updated_trip = trips_collection.find_one({"_id": trip_id_ob})
    if updated_trip:
        updated_day = next((d for d in updated_trip.get("days", []) if d.get("day") == day), None)
        if updated_day:
            new_count = len(updated_day.get("attractions", []))
            print(f"   ✅ 更新後景點數: {new_count}")
            
            # 找到新增的景點
            added = next((a for a in updated_day.get("attractions", []) if a.get("_id") == new_attraction_id), None)
            if added:
                print(f"   ✅ 找到新增的景點: {added.get('name')}")
            else:
                print(f"   ⚠️ 找不到新增的景點")
    
    print(f"{'='*60}\n")
    
    return {
        "message": f"已在 Day {day} 新增景點",
        "attraction_id": str(new_attraction_id),
        "prev_id": str(prev_id) if prev_id else None
    }
        
import re
from typing import Dict, Any, Optional

def delete_from_itinerary(trip_id_ob, day: int, place_name: str) -> Dict[str, Any]:
    """
    依新結構刪除地點：
    - 先以 day 找到該日 head_id，沿著 nodes 的鏈結（next_id）走訪
    - 找到第一個其 places[*].name 包含 place_name 的 node
      * 若該 node 的 places > 1：只 pull 這個 place
      * 若 places == 1：刪除整個 node，並修補鏈結
    回傳：{"message": "..."} 或 {"error": "..."}
    """
    trip = trips_collection.find_one({"trip_id": trip_id_ob}, {"days": 1, "nodes": 1})
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
            {"trip_id": trip_id_ob, "nodes.node_id": node_id},
            {"$pull": {"nodes.$.places": {"name": target_place_name}}}
        )
        if res.modified_count == 0:
            return {"error": "刪除失敗，可能該地點已被移除"}
        return {"message": f"已刪除：{target_place_name}"}

    # 情況 B：node 內只剩這個 place → 刪整個 node 並修補鏈結
    # B-1) 若刪的是 head：更新 days.$.head_id = next_id
    if prev_id is None:
        res1 = trips_collection.update_one(
            {"trip_id": trip_id_ob, "days.day": int(day)},
            {"$set": {"days.$.head_id": next_id}}
        )
        if res1.matched_count == 0:
            return {"error": "更新 head_id 失敗"}

    # B-2) 若刪的是中間/尾端：把 prev.next_id → 指向 next_id
    else:
        res2 = trips_collection.update_one(
            {"trip_id": trip_id_ob, "nodes.node_id": prev_id},
            {"$set": {"nodes.$.next_id": next_id}}
        )
        if res2.matched_count == 0:
            return {"error": "更新前一節點的 next_id 失敗"}

    # B-3) 從 nodes 陣列移除整個 node
    res3 = trips_collection.update_one(
        {"trip_id": trip_id_ob},
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
    