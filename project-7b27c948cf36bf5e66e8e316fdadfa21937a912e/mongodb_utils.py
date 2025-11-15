# mongodb_utils.py
from pymongo import MongoClient
from bson.objectid import ObjectId
from datetime import datetime

# ✅ 使用你的雲端 MongoDB 連線字串
client = MongoClient("mongodb+srv://yu:28@cluster0.g54wj9s.mongodb.net/")
db = client["chat_db"] # 使用你指定的資料庫名稱

# ✅ 重新定義 collection
user_collection = db["users"]
trips_collection = db["test"]
recommendations_collection = db["test"] # 你的 trips 和 recommendations 用的是同一個 collection

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
        
def delete_from_itinerary(trip_id, day, place_name):
    """
    從鏈結串列中刪除指定景點，支援部分名稱匹配。
    """
    trip = get_trip_by_id(trip_id)
    if not trip:
        return {"error": "找不到行程"}
    
    day_data = next((d for d in trip.get("days", []) if d.get("day") == day), None)
    if not day_data:
        return {"error": "找不到指定日期的行程"}

    attractions = day_data.get("attractions", [])
    
    place_to_delete = None
    prev_attraction = None
    
    # 尋找要刪除的景點，並記錄其前一個景點
    for att in attractions:
        if place_name in att.get("name", ""):
            place_to_delete = att
            break
        prev_attraction = att

    if not place_to_delete:
        return {"error": f"找不到景點: {place_name}"}

    # 步驟1: 從 attractions 陣列中移除景點
    result = trips_collection.update_one(
        {"trip_id": trip_id, "days.day": day},
        {"$pull": {"days.$.attractions": {"_id": place_to_delete["_id"]}}}
    )
    
    if result.modified_count == 0:
        return {"error": "景點刪除失敗，請檢查景點是否存在"}

    # 步驟2: 更新鏈結串列
    # 如果刪除的是第一個景點 (head)
    if not prev_attraction:
        new_head_id = place_to_delete.get("next_id")
        trips_collection.update_one(
            {"trip_id": trip_id, "days.day": day},
            {"$set": {"days.$.head": new_head_id}}
        )
    # 如果刪除的是中間或最後一個景點
    else:
        new_next_id = place_to_delete.get("next_id")
        trips_collection.update_one(
            {"trip_id": trip_id, "days.day": day, "days.attractions._id": prev_attraction["_id"]},
            {"$set": {"days.$.attractions.$[attraction].next_id": new_next_id}},
            array_filters=[{"attraction._id": prev_attraction["_id"]}]
        )
            
    return {"message": f"已成功刪除景點: {place_name}"}

def modify_itinerary(trip_id, day, place_name, new_place_name):
    """
    修改指定景點的名稱，支援部分名稱匹配。
    """
    # 尋找包含舊景點名稱的景點
    trips_collection.update_one(
        {"trip_id": trip_id, "days.day": day, "days.attractions.name": {"$regex": place_name, "$options": "i"}},
        {"$set": {"days.$[day].attractions.$[attraction].name": new_place_name}},
        array_filters=[{"day.day": day}, {"attraction.name": {"$regex": place_name, "$options": "i"}}]
    )
    return {"message": f"已將景點 {place_name} 修改為 {new_place_name}"}

def save_recommendation(trip_id, recommendation):
    """儲存建議到 MongoDB"""
    recommendations_collection.insert_one({
        "trip_id": trip_id,
        "recommendation": recommendation,
        "timestamp": datetime.utcnow()
    })
    return {"message": "建議已儲存"}

def clear_all_data():
    """清除所有 MongoDB 資料 (僅供測試用)"""
    user_collection.delete_many({})
    trips_collection.delete_many({})
    recommendations_collection.delete_many({})
    print("✅ 已清除所有使用者和行程資料。")