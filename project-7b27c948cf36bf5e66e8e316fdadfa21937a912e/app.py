# # app.py
# import re
# from threading import Thread
# import traceback
# from flask import Flask, render_template, request, jsonify, session
# from flask_socketio import SocketIO, emit, join_room
# from bson import ObjectId
# import bcrypt, string, random, os
# from datetime import datetime

# # 🔧 工具與模組
# from chat_manager import (
#     decide_location_placement,
#     get_user_chain,
#     update_and_save_memory,
#     analyze_active_users_preferences,
#     detect_add_location_intent,
#     pending_add_location,
#     user_chains
# )
# from convert_trip import convert_trip_to_prompt
# from optimizer import summarize_recommendations, ask_to_add_place, suggest_trip_modifications
# from place_util import get_opening_hours, search_places_by_tag
# from preference import extract_preferences_from_text, update_user_preferences, load_user_preferences
# from weather_utils import get_weather, CITY_TRANSLATIONS
# from mongodb_utils import (
#     user_collection,
#     trips_collection,  # 💡 使用新的 trips_collection
#     get_trip_by_id,
#     add_to_itinerary,
#     delete_from_itinerary,
#     modify_itinerary
# )


# def generate_trip_id(length=6):
#     """Generate a random alphanumeric trip ID."""
#     characters = string.ascii_lowercase + string.digits
#     return ''.join(random.choices(characters, k=length))


# app = Flask(__name__)
# app.config["SECRET_KEY"] = "your_secret_key"
# socketio = SocketIO(app, cors_allowed_origins="*")

# pending_recommendations = {}

# # ---------- 🔒 Auth Routes ----------
# @app.route("/")
# def register_page():
#     return render_template("register.html")


# @app.route("/login_page")
# def login_page():
#     return render_template("login.html")


# @app.route("/register", methods=["POST"])
# def register():
#     data = request.get_json()
#     email = data.get("email")
#     password = data.get("password")
#     if not email or not password:
#         return jsonify({"detail": "請輸入完整資訊"}), 400
#     if user_collection.find_one({"email": email}):
#         return jsonify({"detail": "此 Email 已註冊"}), 409
#     hashed = bcrypt.hashpw(password.encode(), bcrypt.gensalt())
#     user_collection.insert_one({"email": email, "password": hashed})
#     return jsonify({"message": "註冊成功"})


# @app.route("/login", methods=["POST"])
# def login():
#     data = request.get_json()
#     email = data.get("email")
#     password = data.get("password")
#     user = user_collection.find_one({"email": email})

#     if not user or not bcrypt.checkpw(password.encode(), user["password"]):
#         return jsonify({"detail": "帳號或密碼錯誤"}), 401

#     # 💡 修改點 1：從使用者資料中尋找 trip_id
#     user_trip_id = user.get("trip_id")
#     if not user_trip_id:
#         return jsonify({
#             "message": "登入成功，但未找到綁定行程",
#             "user_id": str(user["_id"]),
#             "redirect": "/index"  # 💡 導向到一個讓使用者選擇或創建行程的頁面
#         })

#     # 💡 修改點 2：使用動態的 trip_id
#     redirect_url = f"/chatroom/{user_trip_id}"
#     return jsonify({
#         "message": "登入成功",
#         "user_id": str(user["_id"]),
#         "redirect": redirect_url
#     })


# # ---------- 🧭 Trip Routes ----------
# @app.route("/create_trip", methods=["POST"])
# def create_trip():
#     data = request.get_json()
#     creator = data.get("creator")
#     if not creator:
#         return jsonify({"error": "缺少主揪資訊"}), 400

#     trip_id = generate_trip_id()
#     while trips_collection.find_one({"trip_id": trip_id}):
#         trip_id = generate_trip_id()

#     # 💡 將新生成的 trip_id 存入 creator 的使用者文件中
#     user_collection.update_one(
#         {"_id": ObjectId(creator)},
#         {"$set": {"trip_id": trip_id}}
#     )

#     trips_collection.insert_one({
#         "trip_id": trip_id,
#         "creator": creator,
#         "members": [creator],
#         "days": []
#     })

#     return jsonify({
#         "message": "行程聊天室已建立",
#         "trip_id": trip_id,
#         "chatroom_url": f"/chatroom/{trip_id}"
#     })


# # ---------- 📺 Frontend Routes ----------
# @app.route("/index")
# def index_page():
#     return render_template("index.html")


# @app.route("/chatroom/<trip_id>")
# def chatroom_page(trip_id):
#     return render_template("chatroom.html", trip_id=trip_id)


# # ---------- 💬 Socket.IO ----------
# @socketio.on("connect")
# def handle_connect():
#     print("✅ 使用者連線成功")


# @socketio.on("join")
# def handle_join(data):
#     user_id = data.get("user_id")
#     trip_id = data.get("trip_id")
#     session["user_id"] = user_id
#     session["trip_id"] = trip_id
#     join_room(trip_id)
#     emit("chat_message", {"user_id": "系統", "message": f"{user_id} 已加入聊天室"}, room=trip_id)


# @socketio.on("user_message")
# def handle_user_message(data):
#     user_id = data.get("user_id")
#     trip_id = data.get("trip_id")
#     raw_message = data.get("message", "").strip()

#     if not user_id or not trip_id:
#         return

#     emit("chat_message", {"user_id": user_id, "message": raw_message}, room=trip_id)

#     accept_keywords = {"是", "好", "接受", "確認", "加入", "同意"}
#     reject_keywords = {"否", "略過", "不要", "取消"}

#     # 1. 🔥 處理待新增景點的回覆
#     if user_id in pending_add_location:
#         place_to_add = pending_add_location[user_id]
        
#         if raw_message in accept_keywords:
#             try:
#                 placement_result = decide_location_placement(user_id, trip_id, place_to_add)
#                 day = placement_result.get("day")
#                 period = placement_result.get("period")
                
#                 if day and period:
#                     # 💡 實際呼叫資料庫新增函式
#                     success = add_to_itinerary(trip_id, day, "??:??", "??:??", place_to_add, after_place=None)
#                     if success:
#                         emit("ai_response", {
#                             "message": f"✅ 已將「{place_to_add}」新增到 Day{day} 的{period}！"
#                         }, room=trip_id)
#                     else:
#                         emit("ai_response", {
#                             "message": f"❗ 新增「{place_to_add}」時發生錯誤，請再試一次。"
#                         }, room=trip_id)
#                 else:
#                     emit("ai_response", {
#                         "message": f"🤔 請問您希望將「{place_to_add}」安排在哪一天呢？請回覆如「Day1」、「Day2」等。"
#                     }, room=trip_id)
#                     return  # 🚨 重要：保持 pending 狀態，直接返回
                    
#                 pending_add_location.pop(user_id)
                
#             except Exception as e:
#                 traceback.print_exc()
#                 emit("ai_response", {"message": f"❗ 新增景點時發生錯誤：{e}"}, room=trip_id)
#                 pending_add_location.pop(user_id)
#             return  # 🚨 重要：處理完就直接返回
            
#         elif raw_message in reject_keywords:
#             pending_add_location.pop(user_id)
#             emit("ai_response", {"message": "👌 好的，已取消新增景點。"}, room=trip_id)
#             return  # 🚨 重要：處理完就直接返回
            
#         # 處理指定天數的回覆
#         day_match = re.match(r'[Dd]ay(\d+)', raw_message)
#         if day_match:
#             try:
#                 day = int(day_match.group(1))
#                 # 💡 實際呼叫資料庫新增函式
#                 success = add_to_itinerary(trip_id, day, "??:??", "??:??", place_to_add, after_place=None)
                
#                 if success:
#                     emit("ai_response", {
#                         "message": f"✅ 已將「{place_to_add}」新增到 Day{day}！"
#                     }, room=trip_id)
#                 else:
#                     emit("ai_response", {
#                         "message": f"❗ 新增「{place_to_add}」時發生錯誤，請再試一次。"
#                     }, room=trip_id)
                
#                 pending_add_location.pop(user_id)
#             except Exception as e:
#                 traceback.print_exc()
#                 emit("ai_response", {"message": f"❗ 新增景點時發生錯誤：{e}"}, room=trip_id)
#                 pending_add_location.pop(user_id)
#             return  # 🚨 重要：處理完就直接返回
        
#         # 其他情況，重新提示
#         emit("ai_response", {
#             "message": f"🤔 請回覆「加入」、「略過」，或指定天數如「Day1」來新增「{place_to_add}」。"
#         }, room=trip_id)
#         return  # 🚨 重要：處理完就直接返回

#     # 2. 🔥 處理待處理的「行程修改」建議
#     if user_id in pending_recommendations and pending_recommendations[user_id]:
#         recommendations = pending_recommendations[user_id]
#         current_rec = recommendations[0]

#         # 處理 modify 建議的回覆
#         if current_rec["type"] == "modify":
#             suggested_places = current_rec.get('new_places', [])
            
#             # 檢查使用者是否選擇了其中一個替代景點
#             user_choice = None
#             for place in suggested_places:
#                 if (raw_message == place or 
#                     raw_message in place or 
#                     place in raw_message):
#                     user_choice = place
#                     break
            
#             if user_choice:
#                 try:
#                     # 💡 關鍵修正：實際呼叫資料庫修改函式並檢查結果
#                     print(f"🔧 嘗試修改：trip_id={trip_id}, day={current_rec['day']}, old_place={current_rec['place']}, new_place={user_choice}")
                    
#                     success = modify_itinerary(trip_id, current_rec["day"], current_rec["place"], user_choice)
                    
#                     if success:
#                         emit("ai_response", {
#                             "message": f"✅ 已將 Day{current_rec['day']} 的「{current_rec['place']}」修改為「{user_choice}」。"
#                         }, room=trip_id)
#                         print(f"✅ 資料庫修改成功：{current_rec['place']} -> {user_choice}")
#                     else:
#                         emit("ai_response", {
#                             "message": f"❗ 修改「{current_rec['place']}」為「{user_choice}」時發生錯誤，請再試一次。"
#                         }, room=trip_id)
#                         print(f"❌ 資料庫修改失敗：{current_rec['place']} -> {user_choice}")
                    
#                     # 🚨 關鍵修正：移除已處理的建議
#                     recommendations.pop(0)
                    
#                     # 檢查是否還有其他建議
#                     if recommendations:
#                         next_rec = recommendations[0]
#                         next_prompt = generate_recommendation_prompt(next_rec)
#                         emit("ai_response", {"message": next_prompt}, room=trip_id)
#                     else:
#                         # 🚨 關鍵修正：所有建議處理完畢，清空 pending 狀態
#                         pending_recommendations.pop(user_id)
#                         emit("ai_response", {"message": "✅ 所有建議已處理完畢。"}, room=trip_id)
                        
#                 except Exception as e:
#                     traceback.print_exc()
#                     emit("ai_response", {"message": f"❗ 處理建議時發生錯誤：{e}"}, room=trip_id)
#                     print(f"❌ 修改行程時發生例外：{e}")
#                 return  # 🚨 重要：處理完就直接返回

#             elif raw_message in reject_keywords:
#                 emit("ai_response", {"message": "👌 已略過此建議。"}, room=trip_id)
                
#                 # 🚨 關鍵修正：移除已處理的建議
#                 recommendations.pop(0)
                
#                 if recommendations:
#                     next_rec = recommendations[0]
#                     next_prompt = generate_recommendation_prompt(next_rec)
#                     emit("ai_response", {"message": next_prompt}, room=trip_id)
#                 else:
#                     # 🚨 關鍵修正：所有建議處理完畢，清空 pending 狀態
#                     pending_recommendations.pop(user_id)
#                     emit("ai_response", {"message": "✅ 所有建議已處理完畢。"}, room=trip_id)
#                 return  # 🚨 重要：處理完就直接返回
#             else:
#                 # 用戶回覆不明確，重新提示
#                 places_list = "、".join([f"{i+1}. {place}" for i, place in enumerate(suggested_places)])
#                 prompt_text = (
#                     f"🤔 請從以下選項中選擇一個來替換「{current_rec['place']}」：\n"
#                     f"{places_list}\n"
#                     f"請直接回覆景點名稱，或回覆「略過」跳過此建議。"
#                 )
#                 emit("ai_response", {"message": prompt_text}, room=trip_id)
#                 return  # 🚨 重要：處理完就直接返回

#         # 處理 add 或 delete 建議的回覆
#         elif current_rec["type"] in ["add", "delete"]:
#             if raw_message in accept_keywords:
#                 try:
#                     success = False
#                     if current_rec["type"] == "delete":
#                         # 💡 實際呼叫資料庫刪除函式
#                         success = delete_from_itinerary(trip_id, current_rec["day"], current_rec["place"])
#                         if success:
#                             emit("ai_response", {"message": f"✅ 已從 Day{current_rec['day']} 刪除「{current_rec['place']}」。"}, room=trip_id)
#                         else:
#                             emit("ai_response", {"message": f"❗ 刪除「{current_rec['place']}」時發生錯誤。"}, room=trip_id)
                            
#                     elif current_rec["type"] == "add":
#                         # 💡 實際呼叫資料庫新增函式
#                         success = add_to_itinerary(trip_id, current_rec["day"], "??:??", "??:??", current_rec["place"], after_place=None)
#                         if success:
#                             emit("ai_response", {"message": f"✅ 已將「{current_rec['place']}」新增到 Day{current_rec['day']}。"}, room=trip_id)
#                         else:
#                             emit("ai_response", {"message": f"❗ 新增「{current_rec['place']}」時發生錯誤。"}, room=trip_id)

#                     # 💡 只有在操作成功時才繼續下一個建議
#                     if success:
#                         # 🚨 關鍵修正：移除已處理的建議
#                         recommendations.pop(0)
                        
#                         if recommendations:
#                             next_rec = recommendations[0]
#                             next_prompt = generate_recommendation_prompt(next_rec)
#                             emit("ai_response", {"message": next_prompt}, room=trip_id)
#                         else:
#                             # 🚨 關鍵修正：所有建議處理完畢，清空 pending 狀態
#                             pending_recommendations.pop(user_id)
#                             emit("ai_response", {"message": "✅ 所有建議已處理完畢。"}, room=trip_id)
                        
#                 except Exception as e:
#                     traceback.print_exc()
#                     emit("ai_response", {"message": f"❗ 處理建議時發生錯誤：{e}"}, room=trip_id)
#                 return  # 🚨 重要：處理完就直接返回

#             elif raw_message in reject_keywords:
#                 emit("ai_response", {"message": "👌 已略過此建議。"}, room=trip_id)
                
#                 # 🚨 關鍵修正：移除已處理的建議
#                 recommendations.pop(0)
                
#                 if recommendations:
#                     next_rec = recommendations[0]
#                     next_prompt = generate_recommendation_prompt(next_rec)
#                     emit("ai_response", {"message": next_prompt}, room=trip_id)
#                 else:
#                     # 🚨 關鍵修正：所有建議處理完畢，清空 pending 狀態
#                     pending_recommendations.pop(user_id)
#                     emit("ai_response", {"message": "✅ 所有建議已處理完畢。"}, room=trip_id)
#                 return  # 🚨 重要：處理完就直接返回

#     # 3. 特殊指令：分析 or 更換
#     if raw_message in {"分析", "更換"}:
#         try:
#             # 🚨 關鍵修正：在開始新的分析前，清空所有 pending 狀態
#             if user_id in pending_recommendations:
#                 pending_recommendations.pop(user_id)
#             if user_id in pending_add_location:
#                 pending_add_location.pop(user_id)
                
#             recommendations_list = analyze_active_users_preferences(user_chains, trip_id)
#             if recommendations_list:
#                 pending_recommendations[user_id] = recommendations_list
#                 first_rec = recommendations_list[0]
#                 first_prompt = generate_recommendation_prompt(first_rec)
#                 emit("ai_response", {"message": first_prompt}, room=trip_id)
#             else:
#                 emit("ai_response", {"message": "👌 我已仔細評估過您的行程，目前看來規劃得非常符合您的偏好，沒有需要修改的地方！"}, room=trip_id)
#         except Exception as e:
#             traceback.print_exc()
#             emit("ai_response", {"message": f"❗ 分析與優化失敗：{e}"}, room=trip_id)
#         return  # 🚨 重要：處理完就直接返回

#     # 4. 處理新增地點意圖（優先於偏好擷取）
#     try:
#         intent = detect_add_location_intent(raw_message)
#         if intent["add_location"] and intent["place_name"]:
#             place = intent["place_name"]
#             places = search_places_by_tag(place)
#             if places:
#                 # 🚨 關鍵修正：清空其他 pending 狀態
#                 if user_id in pending_recommendations:
#                     pending_recommendations.pop(user_id)
                    
#                 pending_add_location[user_id] = place
#                 emit("ai_response", {
#                     "message": f"📍 已在 Google 地圖找到「{place}」，是否要將此地點加入您的行程？請回覆「加入」或「略過」。"
#                 }, room=trip_id)
#             else:
#                 emit("ai_response", {
#                     "message": f"❗ 很抱歉，Google 地圖上找不到「{place}」，請確認地點名稱或再試一次。"
#                 }, room=trip_id)
#             return  # 🚨 重要：處理完就直接返回
#     except Exception as e:
#         print(f"⚠️ 意圖偵測失敗：{e}")
#         traceback.print_exc()

#     # 5. 處理偏好擷取
#     try:
#         prefs = extract_preferences_from_text(raw_message)
#         if prefs["prefer"] or prefs["avoid"]:
#             update_user_preferences(user_id, prefs)
            
#             # 🚨 關鍵修正：清空所有 pending 狀態
#             if user_id in pending_recommendations:
#                 pending_recommendations.pop(user_id)
#             if user_id in pending_add_location:
#                 pending_add_location.pop(user_id)
                
#             print(f"✅ 已更新 {user_id} 的偏好：", prefs)
#             emit("ai_response", {"message": f"好的，已將您的偏好：{'、'.join(prefs['prefer'])} 加入考量，並避免 {'、'.join(prefs['avoid'])}。"}, room=trip_id)
#             return  # 🚨 重要：處理完就直接返回
#     except Exception as e:
#         print(f"⚠️ 偏好擷取失敗：{e}")
#         traceback.print_exc()

#     # 6. 一般對話（持續記憶）
#     try:
#         chain = get_user_chain(user_id)
#         result = chain.invoke(raw_message)
#         reply = result.content if hasattr(result, "content") else str(result)
#         update_and_save_memory(user_id, chain)
#         socketio.emit("ai_response", {"message": reply}, room=trip_id)
#     except Exception as e:
#         socketio.emit("ai_response", {"message": f"❗ AI 回應錯誤：{e}"}, room=trip_id)

# def generate_recommendation_prompt(recommendation: dict) -> str:
#     """
#     根據建議類型生成對應的提示文字（增強說明版）
#     """
#     rec_type = recommendation["type"]
#     day = recommendation.get("day")
#     place = recommendation.get("place")
#     reason = recommendation.get("reason", "")
    
#     if rec_type == "delete":
#         return (
#             f"🤔 **建議刪除景點**\n\n"
#             f"📍 地點：Day{day} 的「{place}」\n"
#             f"❌ 建議原因：{reason}\n\n"
#             f"💭 詳細說明：根據您的偏好分析，這個景點可能不太符合您的旅遊喜好或需求。"
#             f"刪除後可以讓行程更輕鬆，也有更多時間深度體驗其他景點。\n\n"
#             f"您是否接受這個建議？請回覆「是」或「否」。"
#         )
        
#     elif rec_type == "add":
#         return (
#             f"🌟 **建議新增景點**\n\n"
#             f"📍 地點：Day{day} 新增「{place}」\n"
#             f"✅ 建議原因：{reason}\n\n"
#             f"💭 詳細說明：這個景點很符合您提到的偏好，加入後能讓您的行程更豐富多元。\n\n"
#             f"您是否接受這個建議？請回覆「是」或「否」。"
#         )
        
#     elif rec_type == "modify":
#         new_places = recommendation.get('new_places', [])
#         if new_places:
#             places_list = "\n".join([f"   {i+1}. 🏛️ {place}" for i, place in enumerate(new_places)])
            
#             return (
#                 f"🔄 **建議替換景點**\n\n"
#                 f"📍 原景點：Day{day} 的「{place}」\n"
#                 f"🔍 替換原因：{reason}\n\n"
#                 f"💭 **為什麼建議替換？**\n"
#                 f"根據您的偏好分析，「{place}」可能與您的旅遊風格不太匹配。"
#                 f"為了讓您有更棒的旅遊體驗，我為您精選了以下更符合您喜好的替代景點：\n\n"
#                 f"🎯 **推薦替代選項：**\n"
#                 f"{places_list}\n\n"
#                 f"🤝 這些景點都考慮了您的偏好設定，相信能帶給您更滿意的旅遊體驗！\n\n"
#                 f"請直接回覆您想選擇的景點名稱，或回覆「略過」跳過此建議。"
#             )
#         else:
#             return (
#                 f"🔄 **建議修改景點**\n\n"
#                 f"📍 地點：Day{day} 的「{place}」\n"
#                 f"🔍 建議原因：{reason}\n\n"
#                 f"💭 **為什麼需要修改？**\n"
#                 f"經過分析，這個景點可能不完全符合您的旅遊偏好。建議您考慮調整或替換為更適合的選項。\n\n"
#                 f"很抱歉目前找不到具體的替代景點，您可以告訴我您的想法！"
#             )
    
#     return f"🤔 我有一個關於 Day{day} 「{place}」的建議：{reason}"


# # ---------- 🚀 Run ----------
# if __name__ == "__main__":
#     socketio.run(app, host="0.0.0.0", port=5000, debug=True, use_reloader=False)
