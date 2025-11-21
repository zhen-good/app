# preference.py
import json
from langchain_google_genai import ChatGoogleGenerativeAI
from utils import extract_json
from bson import ObjectId
from mongodb_utils import user_collection,preferences_collection  # 從這裡取得連線設定
from langchain_openai import ChatOpenAI
import os

#這個檔案用來
#1.獲取偏好
#2.載入偏好
#3.儲存使用者偏好
#4.更新使用者偏好(避免衝突)

# 🔖 偏好分析 Prompt
PREF_PROMPT = """
你是一個旅遊偏好分析師，請從使用者的發言中提取他們的偏好與不喜歡的點，格式如下：
{{"prefer": [...], "avoid": [...]}}

使用者發言：
{input}
"""

# 🧠 用 Gemini 擷取偏好
def extract_preferences_from_text(text: str) -> dict:
    # 使用 OpenAI

    llm = ChatOpenAI(
        model="gpt-4o-mini",  # 用 mini 版本更便宜
        api_key=os.getenv("OPENAI_API_KEY"),
        temperature=0.3,
        max_tokens=256
    )
    prompt = PREF_PROMPT.format(input=text)
    print("🧠 呼叫 prompt:\n", prompt)
    response = llm.invoke(prompt).content
    print("📩 Gemini 回應：", response)

    extracted = extract_json(response)
    if extracted and isinstance(extracted, dict):
        return {
            "prefer": list(set(extracted.get("prefer", []))),
            "avoid": list(set(extracted.get("avoid", [])))
        }
    return {"prefer": [], "avoid": []}



# 📂 根據 trip_id 載入相關使用者的偏好
def load_preferences_by_trip_id(trip_id: str) -> dict:
    """
    根據 trip_id 載入該行程中所有使用者的偏好
    回傳格式：{"prefer": [...], "avoid": [...]}
    """
    from mongodb_utils import trips_collection, preferences_collection
    from bson import ObjectId

    try:
        trip_id_ob = ObjectId(trip_id)
        
        # 先找到該行程的所有成員
        trip = trips_collection.find_one({"_id": trip_id_ob})
        if not trip:
            print(f"❌ 找不到 trip_id: {trip_id} 的行程")
            return {"prefer": [], "avoid": []}
        
        members = trip.get("member", [])
        print(f"🔍 行程 {trip_id} 的成員：{members}")
        
        # 合併所有成員的偏好
        all_prefer = []
        all_avoid = []
        
        for member_id in members:
            doc = preferences_collection.find_one({"user_id": member_id})
            if doc:
                # ✅ 改成查询正确的字段结构
                trips_data = doc.get("trips", {})
                trip_prefs = trips_data.get(trip_id, {})  # 根据 trip_id 查询
                
                member_prefer = trip_prefs.get("prefer", [])
                member_avoid = trip_prefs.get("avoid", [])
                
                all_prefer.extend(member_prefer)
                all_avoid.extend(member_avoid)
                print(f"👤 成員 {member_id} 偏好：喜歡 {member_prefer}，避免 {member_avoid}")
            else:
                print(f"⚠️ 找不到成員 {member_id} 的偏好資料")
        
        # 去除重複
        combined_preferences = {
            "prefer": list(set(all_prefer)),
            "avoid": list(set(all_avoid))
        }
        
        print(f"✅ 行程 {trip_id} 合併偏好：{combined_preferences}")
        return combined_preferences
        
    except Exception as e:
        print(f"❌ 根據 trip_id 載入偏好時發生錯誤：{e}")
        import traceback
        traceback.print_exc()
        return {"prefer": [], "avoid": []}
    


# 💾（可選）一次性覆蓋所有資料
def save_user_preferences(data: dict):
    print("⚠️ 警告: `save_user_preferences` 函式未根據新的結構完全重寫，請謹慎使用。")
    for user_id, val in data.items():
        preferences_collection.update_one(
            {"user_id": user_id},
            {"$set": {
                "form.preferences": list(set(val.get("prefer", []))),
                "form.exclude": list(set(val.get("avoid", [])))
            }},
            upsert=False
        )


def update_user_preferences(
    user_id: str, 
    trip_id: str, 
    prefer_add: list = None, 
    avoid_add: list = None
):
    """
    更新指定使用者在特定行程的偏好
    """
    try:
        uid_key = str(user_id)
        trip_key = str(trip_id)
        
        print(f"🔄 更新偏好: user_id={uid_key}, trip_id={trip_key}")
        print(f"   新增喜好: {prefer_add}")
        print(f"   新增避免: {avoid_add}")
        
        # 🔧 檢查集合是否可用
        if preferences_collection is None:
            print("❌ preferences_collection 未初始化")
            return {"prefer": [], "avoid": []}
        
        # 查找文檔
        doc = preferences_collection.find_one({"user_id": uid_key})
        print(f"📄 找到的文檔: {doc is not None}")
        
        if not doc:
            # 創建新文檔
            print(f"📝 創建新的偏好文檔")
            new_doc = {
                "user_id": uid_key,
                "trips": {
                    trip_key: {
                        "prefer": prefer_add or [],
                        "avoid": avoid_add or []
                    }
                }
            }
            result = preferences_collection.insert_one(new_doc)
            print(f"✅ 已創建新文檔，ID: {result.inserted_id}")
            
            return {
                "prefer": prefer_add or [],
                "avoid": avoid_add or []
            }
        
        # 取得現有的行程偏好
        trips_data = doc.get("trips", {})
        trip_prefs = trips_data.get(trip_key, {})
        
        print(f"📊 現有偏好: {trip_prefs}")
        
        # 取得舊的偏好
        old_prefer = set(trip_prefs.get("prefer", []))
        old_avoid = set(trip_prefs.get("avoid", []))
        
        # 取得新的偏好
        new_prefer = set(prefer_add or [])
        new_avoid = set(avoid_add or [])
        
        # 移除衝突
        old_avoid -= new_prefer
        old_prefer -= new_avoid
        
        # 合併偏好
        updated_prefer = (old_prefer | new_prefer)
        updated_avoid = (old_avoid | new_avoid)
        
        # 確保沒有衝突
        updated_prefer -= updated_avoid
        updated_avoid -= updated_prefer
        
        # 轉換為排序的列表
        prefer_list = sorted(list(updated_prefer))
        avoid_list = sorted(list(updated_avoid))
        
        print(f"🎯 準備更新:")
        print(f"   喜歡: {prefer_list}")
        print(f"   避免: {avoid_list}")
        
        # 🔧 改用更安全的更新方式
        update_result = preferences_collection.update_one(
            {"user_id": uid_key},
            {"$set": {
                f"trips.{trip_key}": {
                    "prefer": prefer_list,
                    "avoid": avoid_list
                }
            }},
            upsert=True  # 如果不存在則創建
        )
        
        print(f"✅ 更新結果:")
        print(f"   匹配數量: {update_result.matched_count}")
        print(f"   修改數量: {update_result.modified_count}")
        print(f"   Upserted ID: {update_result.upserted_id}")
        
        # 🔧 驗證更新
        updated_doc = preferences_collection.find_one({"user_id": uid_key})
        if updated_doc:
            actual_prefs = updated_doc.get("trips", {}).get(trip_key, {})
            print(f"✅ 驗證: {actual_prefs}")
        
        return {
            "prefer": prefer_list,
            "avoid": avoid_list
        }
        
    except Exception as e:
        print(f"❌ 更新偏好失敗: {e}")
        import traceback
        traceback.print_exc()
        return {"prefer": [], "avoid": []}
    


