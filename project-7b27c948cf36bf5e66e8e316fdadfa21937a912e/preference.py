# # preference.py
# import json
# from langchain_google_genai import ChatGoogleGenerativeAI
# from utils import extract_json
# from bson import ObjectId
# from mongodb_utils import user_collection  # 從這裡取得連線設定
# from langchain_openai import ChatOpenAI
# import os

# # 明確指定 collection = form_test
# preferences_collection = user_collection.database["form_test"]

# # 🔖 偏好分析 Prompt
# PREF_PROMPT = """
# 你是一個旅遊偏好分析師，請從使用者的發言中提取他們的偏好與不喜歡的點，格式如下：
# {{"prefer": [...], "avoid": [...]}}

# 使用者發言：
# {input}
# """

# # 🧠 用 Gemini 擷取偏好
# def extract_preferences_from_text(text: str) -> dict:
#     # 使用 OpenAI


#     llm = ChatOpenAI(
#         model="gpt-4o-mini",  # 用 mini 版本更便宜
#         api_key=os.getenv("OPENAI_API_KEY"),
#         temperature=0.3,
#         max_tokens=256
#     )
#     prompt = PREF_PROMPT.format(input=text)
#     print("🧠 呼叫 prompt:\n", prompt)
#     response = llm.invoke(prompt).content
#     print("📩 Gemini 回應：", response)

#     extracted = extract_json(response)
#     if extracted and isinstance(extracted, dict):
#         return {
#             "prefer": list(set(extracted.get("prefer", []))),
#             "avoid": list(set(extracted.get("avoid", [])))
#         }
#     return {"prefer": [], "avoid": []}

# # 📂 載入所有偏好（修正版）
# def load_user_preferences() -> dict:
#     """
#     載入所有使用者偏好，回傳格式：
#     {
#         "user_id_1": {"prefer": [...], "avoid": [...]},
#         "user_id_2": {"prefer": [...], "avoid": [...]},
#     }
#     """
#     prefs = {}
#     try:
#         # 查詢 form_test collection 中的所有文件
#         for doc in preferences_collection.find({}):
#             user_id = doc.get("user_id")
#             if user_id:
#                 form_data = doc.get("form", {})
#                 prefs[user_id] = {
#                     "prefer": form_data.get("preferences", []),
#                     "avoid": form_data.get("exclude", [])
#                 }
#         print(f"✅ 成功載入 {len(prefs)} 個使用者的偏好資料")
#         print(f"🔍 載入的偏好資料：{prefs}")
#     except Exception as e:
#         print(f"❌ 載入偏好資料時發生錯誤：{e}")
#     return prefs

# # 📂 根據 trip_id 載入相關使用者的偏好
# def load_preferences_by_trip_id(trip_id: str) -> dict:
#     """
#     根據 trip_id 載入該行程中所有使用者的偏好
#     回傳格式：{"prefer": [...], "avoid": [...]}
#     """
#     from mongodb_utils import trips_collection
    
#     try:
#         # 先找到該行程的所有成員
#         trip = trips_collection.find_one({"trip_id": trip_id})
#         if not trip:
#             print(f"❌ 找不到 trip_id: {trip_id} 的行程")
#             return {"prefer": [], "avoid": []}
        
#         members = trip.get("members", [])
#         print(f"🔍 行程 {trip_id} 的成員：{members}")
        
#         # 合併所有成員的偏好
#         all_prefer = []
#         all_avoid = []
        
#         for member_id in members:
#             doc = preferences_collection.find_one({"user_id": str(member_id)})
#             if doc:
#                 form_data = doc.get("form", {})
#                 member_prefer = form_data.get("preferences", [])
#                 member_avoid = form_data.get("exclude", [])
                
#                 all_prefer.extend(member_prefer)
#                 all_avoid.extend(member_avoid)
#                 print(f"👤 成員 {member_id} 偏好：喜歡 {member_prefer}，避免 {member_avoid}")
        
#         # 去除重複
#         combined_preferences = {
#             "prefer": list(set(all_prefer)),
#             "avoid": list(set(all_avoid))
#         }
        
#         print(f"✅ 行程 {trip_id} 合併偏好：{combined_preferences}")
#         return combined_preferences
        
#     except Exception as e:
#         print(f"❌ 根據 trip_id 載入偏好時發生錯誤：{e}")
#         return {"prefer": [], "avoid": []}

# # 💾（可選）一次性覆蓋所有資料
# def save_user_preferences(data: dict):
#     print("⚠️ 警告: `save_user_preferences` 函式未根據新的結構完全重寫，請謹慎使用。")
#     for user_id, val in data.items():
#         preferences_collection.update_one(
#             {"user_id": user_id},
#             {"$set": {
#                 "form.preferences": list(set(val.get("prefer", []))),
#                 "form.exclude": list(set(val.get("avoid", [])))
#             }},
#             upsert=False
#         )

# # 🔁 更新單一使用者偏好（修正版）
# def update_user_preferences(user_id: str, new_prefs: dict):
#     """
#     更新指定使用者的偏好
#     """
#     try:
#         uid_key = str(user_id)
        
#         doc = preferences_collection.find_one({"user_id": uid_key})
#         if not doc:
#             print(f"❌ 找不到 user_id: {uid_key} 的文件，嘗試建立新文件")
#             # 如果找不到文件，建立一個新的
#             preferences_collection.insert_one({
#                 "user_id": uid_key,
#                 "form": {
#                     "preferences": new_prefs.get("prefer", []),
#                     "exclude": new_prefs.get("avoid", [])
#                 }
#             })
#             print(f"✅ 為 user_id: {uid_key} 建立新的偏好文件")
#             return

#         form_data = doc.get("form", {})
#         old_prefer = set(form_data.get("preferences", []))
#         old_avoid = set(form_data.get("exclude", []))

#         new_prefer = set(new_prefs.get("prefer", []))
#         new_avoid = set(new_prefs.get("avoid", []))

#         # 移除衝突
#         old_avoid -= new_prefer
#         old_prefer -= new_avoid

#         updated_prefer = (old_prefer | new_prefer)
#         updated_avoid = (old_avoid | new_avoid)

#         # 確保偏好和避免之間沒有衝突
#         updated_prefer -= updated_avoid
#         updated_avoid -= updated_prefer

#         # 更新資料庫
#         preferences_collection.update_one(
#             {"user_id": uid_key},
#             {"$set": {
#                 "form.preferences": sorted(list(updated_prefer)),
#                 "form.exclude": sorted(list(updated_avoid))
#             }}
#         )
        
#         print(f"✅ 成功更新 user_id: {uid_key} 的偏好")
#         print(f"   喜歡：{sorted(list(updated_prefer))}")
#         print(f"   避免：{sorted(list(updated_avoid))}")
        
#     except Exception as e:
#         print(f"❌ 更新使用者偏好時發生錯誤：{e}")