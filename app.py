# app.py
from dotenv import find_dotenv, load_dotenv
from chat_nature import coerce_to_json_dict, handle_extra_chat
from place_gmaps import search_candidates
from place_node import _anchor_coords
from datetime import datetime, timedelta
from bson import ObjectId,json_util
import jwt
from flask import jsonify, request
import json
import re
from threading import Thread
import traceback
from flask import Flask, render_template, request, jsonify, session
from flask_socketio import SocketIO, emit, join_room
from bson import ObjectId
import bcrypt, string, random, os
from friend import friends_bp
from register import auth_bp



# 🔧 工具與模組
from chat_manager import (
    decide_location_placement,
    display_trip_by_trip_id,
    get_user_chain,
    update_and_save_memory,
    analyze_active_users_preferences,
    detect_add_location_intent,
    pending_add_location,
    user_chains
)
from convert_trip import convert_trip_to_prompt
from optimizer import summarize_recommendations, ask_to_add_place, suggest_trip_modifications
from place_util import get_opening_hours, search_places_by_tag
from preference import update_user_preferences, extract_preferences_from_text
from weather_utils import get_weather, CITY_TRANSLATIONS
from mongodb_utils import (
    user_collection,
    trips_collection,  # 💡 使用新的 trips_collection
    get_trip_by_id,
    add_to_itinerary,
    delete_from_itinerary,
    modify_itinerary,
    save_message_to_mongodb #將題目存進mongodb
)


load_dotenv(find_dotenv(), override=True)

# 🔍 加入這段除錯代碼
print("=" * 50)
print("🔍 檢查環境變數")
print("=" * 50)
openai_key = os.getenv("OPENAI_API_KEY")
if openai_key:
    print(f"✅ OPENAI_API_KEY 已載入")
    print(f"   前 10 個字元: {openai_key[:10]}")
    print(f"   後 4 個字元: ...{openai_key[-4:]}")
    print(f"   總長度: {len(openai_key)}")
else:
    print("❌ OPENAI_API_KEY 未找到!")
    print(f"   .env 檔案位置: {find_dotenv()}")
print("=" * 50)


def generate_trip_id(length=6):
    """Generate a random alphanumeric trip ID."""
    characters = string.ascii_lowercase + string.digits
    return ''.join(random.choices(characters, k=length))


app = Flask(__name__)
app.register_blueprint(friends_bp)
app.register_blueprint(auth_bp)

app.config["SECRET_KEY"] = "your_secret_key"
socketio = SocketIO(
    app, 
    cors_allowed_origins="*",  # 允許所有來源
    async_mode='threading',    # 或 'eventlet'
    logger=True,               # 開啟 log
    engineio_logger=True       # 開啟詳細 log
)

pending_recommendations = {}


# ---------- 🧭 Trip Routes ----------
@app.route("/create_trip", methods=["POST"])
def create_trip():
    data = request.get_json()
    creator = data.get("creator")
    if not creator:
        return jsonify({"error": "缺少主揪資訊"}), 400

    trip_id = generate_trip_id()
    while trips_collection.find_one({"_id": trip_id}):
        trip_id = generate_trip_id()

    # 💡 將新生成的 trip_id 存入 creator 的使用者文件中
    user_collection.update_one(
        {"_id": ObjectId(creator)},
        {"$set": {"trip_id": trip_id}}
    )

    trips_collection.insert_one({
        "trip_id": trip_id,
        "creator": creator,
        "members": [creator],
        "days": []
    })

    return jsonify({
        "message": "行程聊天室已建立",
        "trip_id": trip_id,
        "chatroom_url": f"/chatroom/{trip_id}"
    })


# ---------- 📺 Frontend Routes ----------
@app.route("/index")
def index_page():
    return render_template("index.html")


@app.route("/chatroom/<trip_id>")
def chatroom_page(trip_id):
    return render_template("chatroom.html", trip_id=trip_id)


# ---------- 💬 Socket.IO ----------
@socketio.on("connect")
def handle_connect():
    print("✅ 使用者連線成功")


@socketio.on("join")
def handle_join(data):
    user_id = data.get("user_id")
    trip_id = data.get("trip_id")
    user_name = data.get("name")

    trip_id_ob = ObjectId(trip_id)

    session["user_id"] = user_id
    session["trip_id"] = trip_id

    join_room(trip_id)

    emit("chat_message", {"user_id": "系統", "message": f"{user_id} 已加入聊天室 {trip_id}"}, room=trip_id)

    
    # doc = trips_collection.find_one({"_id": trip_id_ob}, {"_id": 0, "nodes": 1})
    #這邊是一開始會先傳一個trip的行程給使用者看
    trip_text = display_trip_by_trip_id(trip_id_ob)
    emit("trip", {"user_id": "系統", "message": trip_text}, room=trip_id)
    emit("chat_message", {"user_id": "系統", "message": f"請跟我說說你對本次行程的看法吧~"}, room=trip_id)


# app.py (修正後的 handle_user_message 函式)

@socketio.on("user_message")
def handle_user_message(data):
    user_id = data.get("user_id")
    trip_id = data.get("trip_id")
    raw_message = data.get("message", "").strip()
    payload = data.get("payload") or {}

    if not user_id or not trip_id:
        return
    
    save_message_to_mongodb(trip_id,user_id, "user", raw_message)

    trip_id_ob = ObjectId(trip_id)

    if raw_message != "分析":
    
        try:
            # 2. 發送給前端
            emit("chat_message", {
                "user_id": user_id,
                "message": raw_message
            }, room=trip_id)
            print("這裡有錯嗎")
        except Exception as e:
            print(f"❌ Error emitting chat_message to client: {e}")
        
        # 呼叫你的 GPT 產生單題輸出
        out = handle_extra_chat(user_id, trip_id, raw_message)  # 應回上面的單題 dict

        print("確認題目格式：", out)

        if(out):
            print("成功")
            emit_reply_and_question(user_id, trip_id, out)
        else:
            socketio.emit("ai_response", {"message": str(out)}, room=trip_id)

        return

    # emit("ai_response", {"message":raw_message}, room=trip_id)

    accept_keywords = {"是", "好", "接受", "確認", "加入", "同意"}
    reject_keywords = {"否", "略過", "不要", "取消"}

    
    # 🔎 特殊指令：查看行程
    if raw_message in {"行程", "我的行程", "查看行程"}:
        try:
            # 這裡用你已從 mongodb_utils 匯入的 trips_collection
            # 其實就是 db["structured_itineraries"]
            doc = trips_collection.find_one({"_id": trip_id_ob}, {"_id": 0, "nodes": 1})
            nodes = (doc or {}).get("nodes", [])  # 沒有就給空陣列

            if not nodes:
                emit("ai_response", {"message": "❗ 找不到此行程（trip_id 不存在或已被刪除）。"}, room=trip_id)
                return

            trip_text = display_trip_by_trip_id(trip_id_ob)
            emit("trip", {"user_id": "系統", "message": trip_text}, room=trip_id)
            # 同時給一個人性化訊息
            emit("ai_response", {"message": "🧭 已送出目前行程資訊到畫面。"}, room=trip_id)

        except Exception as e:
            traceback.print_exc()
            emit("ai_response", {"message": f"❗ 讀取行程時發生錯誤：{e}"}, room=trip_id)
        return  # 🔚 結束本次處理

    # 1. 🔥 處理待新增景點的回覆

    if user_id in pending_add_location:
        place_to_add = pending_add_location[user_id]
        
        if raw_message in accept_keywords:
            try:
                placement_result = decide_location_placement(user_id, trip_id, place_to_add)
                day = placement_result.get("day")
                period = placement_result.get("period")
                
                if day and period:
                    # 💡 實際呼叫資料庫新增函式
                    success = add_to_itinerary(trip_id, day, "??:??", "??:??", place_to_add, after_place=None)
                    if success:
                        emit("ai_response", {
                            "message": f"✅ 已將「{place_to_add}」新增到 Day{day} 的{period}！"
                        }, room=trip_id)
                    else:
                        emit("ai_response", {
                            "message": f"❗ 新增「{place_to_add}」時發生錯誤，請再試一次。"
                        }, room=trip_id)
                else:
                    emit("ai_response", {
                        "message": f"🤔 請問您希望將「{place_to_add}」安排在哪一天呢？請回覆如「Day1」、「Day2」等。"
                    }, room=trip_id)
                    return  # 🚨 重要：保持 pending 狀態，直接返回
                    
                pending_add_location.pop(user_id)
                
            except Exception as e:
                traceback.print_exc()
                emit("ai_response", {"message": f"❗ 新增景點時發生錯誤：{e}"}, room=trip_id)
                pending_add_location.pop(user_id)
            return  # 🚨 重要：處理完就直接返回
            
        elif raw_message in reject_keywords:
            pending_add_location.pop(user_id)
            emit("ai_response", {"message": "👌 好的，已取消新增景點。"}, room=trip_id)
            return  # 🚨 重要：處理完就直接返回
            
        # 處理指定天數的回覆
        day_match = re.match(r'[Dd]ay(\d+)', raw_message)
        if day_match:
            try:
                day = int(day_match.group(1))
                # 💡 實際呼叫資料庫新增函式
                success = add_to_itinerary(trip_id, day, "??:??", "??:??", place_to_add, after_place=None)
                
                if success:
                    emit("ai_response", {
                        "message": f"✅ 已將「{place_to_add}」新增到 Day{day}！"
                    }, room=trip_id)
                else:
                    emit("ai_response", {
                        "message": f"❗ 新增「{place_to_add}」時發生錯誤，請再試一次。"
                    }, room=trip_id)
                
                pending_add_location.pop(user_id)
            except Exception as e:
                traceback.print_exc()
                emit("ai_response", {"message": f"❗ 新增景點時發生錯誤：{e}"}, room=trip_id)
                pending_add_location.pop(user_id)
            return  # 🚨 重要：處理完就直接返回
        
        # 其他情況，重新提示
        emit("ai_response", {
            "message": f"🤔 請回覆「加入」、「略過」，或指定天數如「Day1」來新增「{place_to_add}」。"
        }, room=trip_id)
        return  # 🚨 重要：處理完就直接返回

    # 2. 🔥 處理待處理的「行程修改」建議
    if user_id in pending_recommendations and pending_recommendations[user_id]:
        recommendations = pending_recommendations[user_id]
        current_rec = recommendations[0]

        # 處理 modify 建議的回覆
        if current_rec["type"] == "modify":
            # 💡 關鍵修正一：統一使用 AI 輸出的 'place' 鍵名 (假設已在其他地方修正了 AI 的 JSON)
            original_place_name = current_rec.get('place') 
            original_place_id = current_rec.get('place_id') # 假設您也儲存了原始地點的 ID
            
            suggested_places = current_rec.get('new_places', [])
            
            user_choice = None
            
            # --- 新增的邏輯：檢查是否為數字編號回覆 ---
            try:
                choice_index = int(raw_message) - 1
                if 0 <= choice_index < len(suggested_places):
                    user_choice = suggested_places[choice_index]
            except ValueError:
                # 如果不是數字，則執行原本的邏輯：檢查是否為地點名稱或關鍵字
                pass 
                
            # --- 保留原本的邏輯：檢查是否為地點名稱 (數字回覆優先處理) ---
            if not user_choice:
                # 檢查使用者是否選擇了其中一個替代景點（用名稱比對）
                for cand in suggested_places:
                    # cand 可能是 dict 或 str；都轉成可以比的字串
                    if isinstance(cand, dict):
                        name = str(cand.get("name", "")).lower()
                    else:
                        name = str(cand).lower()

                    if raw_message.lower() == name or raw_message.lower() in name or name in raw_message.lower():
                        user_choice = cand  # 保留原物件（如果是 dict，後面可取 place_id）
                        break
            
            # --- 處理「略過」回覆 ---
            if raw_message.lower() in ("略過", "skip", "pass"):
                emit("ai_response", {
                    "message": f"✅ 已略過 Day{current_rec['day']} 對「{original_place_name}」的修改建議。"
                }, room=trip_id)
                # 🚨 關鍵修正：移除已處理的建議 (略過)
                recommendations.pop(0)
                # 💡 檢查並發送下一個建議 (與下方成功邏輯相同)
                if recommendations:
                    next_rec = recommendations[0]
                    # 💡 注意：這裡需要您提供 generate_recommendation_prompt 函式的實現
                    next_prompt = generate_recommendation_prompt(next_rec) 
                    emit("ai_response", {"message": next_prompt}, room=trip_id)
                else:
                    pending_recommendations.pop(user_id)
                    emit("ai_response", {"message": "✅ 所有建議已處理完畢。"}, room=trip_id)
                return # 處理完畢，結束函式

            # --- 處理成功的選擇 (數字或名稱) ---
            if user_choice:
                try:
                    # 💡 關鍵修正：確保使用正確的鍵名
                    print(f"🔧 嘗試修改：trip_id={trip_id}, day={current_rec['day']}, old_place={original_place_name}, new_place={user_choice}")
                    
                    # 💡 關鍵修正：使用您實際儲存的原始地點 ID
                    success = modify_itinerary(trip_id, current_rec["day"], original_place_id, user_choice) 
                    
                    # ... (後續的 success/fail 判斷和發送訊息邏輯保持不變) ...
                    
                    if success:
                        emit("ai_response", {
                            "message": f"✅ 已將 Day{current_rec['day']} 的「{original_place_name}」修改為「{user_choice}」。"
                        }, room=trip_id)
                        print(f"✅ 資料庫修改成功：{original_place_name} -> {user_choice}")
                    else:
                        emit("ai_response", {
                            "message": f"❗ 修改「{original_place_name}」為「{user_choice}」時發生錯誤，請再試一次。"
                        }, room=trip_id)
                        print(f"❌ 資料庫修改失敗：{original_place_name} -> {user_choice}")
                        
                    # 🚨 關鍵修正：移除已處理的建議
                    recommendations.pop(0)
                    
                    # 檢查是否還有其他建議
                    if recommendations:
                        next_rec = recommendations[0]
                        # 💡 注意：這裡需要您提供 generate_recommendation_prompt 函式的實現
                        next_prompt = generate_recommendation_prompt(next_rec) 
                        emit("ai_response", {"message": next_prompt}, room=trip_id)
                    else:
                        # 🚨 關鍵修正：所有建議處理完畢，清空 pending 狀態
                        pending_recommendations.pop(user_id)
                        emit("ai_response", {"message": "✅ 所有建議已處理完畢。"}, room=trip_id)
                        
                except Exception as e:
                    # 處理例外情況
                    print(f"❌ 處理修改建議時發生錯誤: {e}")
                    emit("ai_response", {"message": f"伺服器錯誤：無法處理您的選擇。錯誤：{e}"}, room=trip_id)
                    
            else:
                # 處理無效回覆
                # 💡 關鍵修正二：給出帶有按鈕提示的回覆
                # 這裡假設您的 `generate_recommendation_prompt` 會生成一個包含按鈕的完整訊息
                
                # 重新發送建議，提示使用者點擊按鈕或輸入正確的數字
                next_prompt = generate_recommendation_prompt(current_rec)
                emit("ai_response", {"message": "⚠️ 無效的選擇，請點擊按鈕或回覆數字編號 (如: 1) 或 略過。"}, room=trip_id)
                # 重新發送建議的 UI（發送 `ai_response` 事件，**內含 `buttons` 結構**，這是前端渲染按鈕的關鍵）
                emit_ai_response_with_buttons(trip_id, current_rec)

        # 處理 add 或 delete 建議的回覆
        elif current_rec["type"] in ["add", "delete"]:
            if raw_message in accept_keywords:
                try:
                    success = False
                    if current_rec["type"] == "delete":
                        # 💡 實際呼叫資料庫刪除函式
                        success = delete_from_itinerary(trip_id, current_rec["day"], current_rec["ori_place"])
                        if success:
                            emit("ai_response", {"message": f"✅ 已從 Day{current_rec['day']} 刪除「{current_rec['ori_place']}」。"}, room=trip_id)
                        else:
                            emit("ai_response", {"message": f"❗ 刪除「{current_rec['place']}」時發生錯誤。"}, room=trip_id)
                            
                    elif current_rec["type"] == "add":
                        # 💡 實際呼叫資料庫新增函式
                        success = add_to_itinerary(trip_id, current_rec["day"], "??:??", "??:??", current_rec["ori_place"], after_place=None)
                        if success:
                            emit("ai_response", {"message": f"✅ 已將「{current_rec['place']}」新增到 Day{current_rec['day']}。"}, room=trip_id)
                        else:
                            emit("ai_response", {"message": f"❗ 新增「{current_rec['place']}」時發生錯誤。"}, room=trip_id)

                    # 💡 只有在操作成功時才繼續下一個建議
                    if success:
                        # 🚨 關鍵修正：移除已處理的建議
                        recommendations.pop(0)
                        
                        if recommendations:
                            next_rec = recommendations[0]
                            next_prompt = generate_recommendation_prompt(next_rec)
                            emit("ai_response", {"message": next_prompt}, room=trip_id)
                        else:
                            # 🚨 關鍵修正：所有建議處理完畢，清空 pending 狀態
                            pending_recommendations.pop(user_id)
                            emit("ai_response", {"message": "✅ 所有建議已處理完畢。"}, room=trip_id)
                        
                except Exception as e:
                    traceback.print_exc()
                    emit("ai_response", {"message": f"❗ 處理建議時發生錯誤：{e}"}, room=trip_id)
                return  # 🚨 重要：處理完就直接返回

            elif raw_message in reject_keywords:
                emit("ai_response", {"message": "👌 已略過此建議。"}, room=trip_id)
                
                # 🚨 關鍵修正：移除已處理的建議
                recommendations.pop(0)
                
                if recommendations:
                    next_rec = recommendations[0]
                    next_prompt = generate_recommendation_prompt(next_rec)
                    emit("ai_response", {"message": next_prompt}, room=trip_id)
                else:
                    # 🚨 關鍵修正：所有建議處理完畢，清空 pending 狀態
                    pending_recommendations.pop(user_id)
                    emit("ai_response", {"message": "✅ 所有建議已處理完畢。"}, room=trip_id)
                return  # 🚨 重要：處理完就直接返回

    # 3. 特殊指令：分析 or 更換

    if raw_message in {"分析", "更換"}:
        try:
            print("找一下trip_id",trip_id)
            # 🚨 關鍵修正：在開始新的分析前，清空所有 pending 狀態
            if user_id in pending_recommendations:
                pending_recommendations.pop(user_id)
            if user_id in pending_add_location:
                pending_add_location.pop(user_id)
                
            recommendations_list = analyze_active_users_preferences(user_chains, trip_id_ob)
            
            if recommendations_list:
                pending_recommendations[user_id] = recommendations_list
                first_rec = recommendations_list[0]
                
                # 💡 關鍵修正：判斷是否為 modify 建議，並使用 emit_ai_response_with_buttons
                if first_rec.get('type') == 'modify':
                    # 🚀 使用新函式發送，內含 buttons
                    emit_ai_response_with_buttons(trip_id, first_rec)
                    print("有發出button嗎")
                else:
                    # 處理非 modify 建議 (add/delete)，發送不含 buttons 的結構化數據
                    
                    # 1. 生成 AI 提示文本
                    first_prompt = generate_recommendation_prompt(first_rec)
                    
                    # 2. 構建包含結構化數據的 Payload (不含 buttons)
                    payload = {
                        "message": first_prompt,
                        # 🚨 整合結構化數據
                        "recommendation": {
                            "type": first_rec['type'],
                            "day": first_rec['day'],
                            "place": first_rec['place'],
                            "reason": first_rec['reason'],
                            # 只有 modify 建議需要 new_places 列表 (但這裡還是放著，讓前端DTO保持一致)
                            "new_places": first_rec.get('new_places', []) 
                        }
                    }
                    
                    # 3. 發送 (只含文字和 recommendation)
                    emit("ai_response", payload, room=trip_id)
            else:
                emit("ai_response", {"message": "👌 我已仔細評估過您的行程，目前看來規劃得非常符合您的偏好，沒有需要修改的地方！"}, room=trip_id)
                
        except Exception as e:
            traceback.print_exc()
            emit("ai_response", {"message": f"❗ 分析與優化失敗：{e}"}, room=trip_id)
        return  # 🚨 重要：處理完就直接返回

    # 4. 處理新增地點意圖（優先於偏好擷取）
    try:
        intent = detect_add_location_intent(raw_message)
        if intent["add_location"] and intent["place_name"]:
            place = intent["place_name"]

            # 取得行程中心點，避免跨縣市
            trip_doc = get_trip_by_id(trip_id) or {}
            near = _anchor_coords(trip_doc, day=None, slot=None, near_hint="slot_node")

            # 用 Text Search 搜地點（限制在行程範圍附近）
            # 半徑可視你的場景：城市 5–15km；縣市 20–40km
            candidates = search_candidates(
                query=place,
                near=near,            # None 時就會成為全球性偏好 → 建議務必給
                radius_m=15000,
                max_results=5,
                enrich_opening=False  # 這裡只是驗證存在，不必打 details
            ) or []

            # （可選）排除需要事先購票才有意義的場館
            # candidates = [c for c in candidates if not _is_ticketed_venue(c)]

            if candidates:
                # 取第一個最像的（或你可以列清單給使用者選）
                top = candidates[0]
                canonical_name = top.get("name") or place

                # 清掉其他 pending
                if user_id in pending_recommendations:
                    pending_recommendations.pop(user_id)

                pending_add_location[user_id] = canonical_name

                # 顯示「地名 / 地址 / 地圖連結」
                addr = top.get("address") or f"{top.get('lat')},{top.get('lng')}"
                url  = top.get("map_url") or ""
                emit("ai_response", {
                    "message": (
                        f"📍 找到「{canonical_name}」\n"
                        f"   📌 地址：{addr}\n"
                        f"   🔗 地圖：{url}\n"
                        f"要把它加入行程嗎？請回覆「加入」或「略過」。"
                    )
                }, room=trip_id)
            else:
                emit("ai_response", {
                    "message": f"❗ 很抱歉，在行程範圍內找不到「{place}」，請再確認名稱或提供更明確的位置。"
                }, room=trip_id)
            return
    except Exception as e:
        print(f"⚠️ 意圖偵測或搜尋失敗：{e}")
        traceback.print_exc()

    # 5. 處理偏好擷取
    try:
        prefs = extract_preferences_from_text(raw_message)
        if prefs["prefer"] or prefs["avoid"]:
            update_user_preferences(
            user_id=user_id,
            prefer_add=prefs.get("prefer"),
            avoid_add=prefs.get("avoid"),
            trip_id=trip_id,       # None = 全域偏好；有值 = 該行程專屬偏好
        )
            
            # 🚨 關鍵修正：清空所有 pending 狀態
            if user_id in pending_recommendations:
                pending_recommendations.pop(user_id)
            if user_id in pending_add_location:
                pending_add_location.pop(user_id)
                
            print(f"✅ 已更新 {user_id} 的偏好：", prefs)
            # emit("ai_response", {"message": f"好的，已將您的偏好：{'、'.join(prefs['prefer'])} 加入考量，並避免 {'、'.join(prefs['avoid'])}。"}, room=trip_id)
            return  # 🚨 重要：處理完就直接返回
    except Exception as e:
        print(f"⚠️ 偏好擷取失敗：{e}")
        traceback.print_exc()

    # 6. 一般對話（持續記憶）
    # try:
    #     chain = get_user_chain(user_id)
    #     result = chain.invoke(raw_message)
    #     reply = result.content if hasattr(result, "content") else str(result)
    #     update_and_save_memory(user_id, chain)
    #     socketio.emit("ai_response", {"message": reply}, room=trip_id)
    except Exception as e:
        socketio.emit("ai_response", {"message": f"❗ AI 回應錯誤：{e}"}, room=trip_id)

def _present_place_for_prompt(row: dict | str) -> str:
    """
    將候選地點轉成單行可讀字串：
    1) 支援 dict 與 str 兩種型別（相容舊流程）
    2) 欄位優先序：
       - 時間：hours_today_text > weekday_text_str > 無
       - 地址：address > "lat,lng" > 無
       - 連結：map_url（若無則不顯示）
    """
    if isinstance(row, str):
        return f"🏛️ {row}"

    name = row.get("name") or "（未命名）"
    time_text = row.get("hours_today_text") or row.get("weekday_text_str")
    address = row.get("address")
    lat = row.get("lat"); lng = row.get("lng")
    if not address and isinstance(lat, (int, float)) and isinstance(lng, (int, float)):
        address = f"{lat:.6f}, {lng:.6f}"
    link = row.get("map_url")

    parts = [f"🏛️ {name}"]
    if time_text:
        parts.append(f"🕒 {time_text}")
    if address:
        parts.append(f"📍 {address}")
    if link:
        parts.append(f"🔗 {link}")
    return "｜".join(parts)


def generate_recommendation_prompt(recommendation: dict) -> str:
    """
    根據建議類型生成對應的提示文字（增強說明版）
    - modify：會列出候選地點（地名／時間／地址／連結）
    - add / delete：沿用原說明，但讓 reason 更健壯（支援 dict.reason.summary）
    """
    rec_type = recommendation.get("type")
    day = recommendation.get("day")
    ori_place = recommendation.get("place")
    # 支援 reason 可能是字串或物件（{summary, evidence, ...}）
    reason_obj = recommendation.get("reason") or {}
    reason_text = (
        reason_obj.get("summary") if isinstance(reason_obj, dict) else reason_obj
    ) or "（無法取得原因摘要）"

    if rec_type == "delete":
        return (
            f"🤔 **建議刪除景點**\n\n"
            f"📍 地點：Day{day} 的「{ori_place}」\n"
            f"❌ 建議原因：{reason_text}\n\n"
            f"💭 詳細說明：這個景點與您的偏好或動線不夠契合，刪除後可留出更彈性的時間。\n\n"
            f"您是否接受這個建議？請回覆「是」或「否」。"
        )

    if rec_type == "add":
        return (
            f"🌟 **建議新增景點**\n\n"
            f"📍 建議新增至：Day{day}\n"
            f"✅ 建議原因：{reason_text}\n\n"
            f"💭 詳細說明：此類型更符合您的偏好並補齊當段主題。\n\n"
            f"您是否接受這個建議？請回覆「是」或「否」。"
        )

    if rec_type == "modify":
        new_places = recommendation.get("new_places", [])
        if new_places:
            # 只顯示前 5 筆，避免洗版
            lines = []
            for i, row in enumerate(new_places[:5], start=1):
                lines.append(f"{i}. {_present_place_for_prompt(row)}")
            places_list = "\n".join(lines)

            return (
                f"🔄 **建議替換景點**\n\n"
                f"📍 原景點：Day{day} 的「{ori_place}」\n"
                f"🔍 替換原因：{reason_text}\n\n"
                f"🎯 **推薦替代選項：**\n{places_list}\n\n"
                f"請回覆想選擇的編號（例如：1），或回覆「略過」。"
            )
        else:
            return (
                f"🔄 **建議修改景點**\n\n"
                f"📍 地點：Day{day} 的「{ori_place}」\n"
                f"🔍 建議原因：{reason_text}\n\n"
                f"目前沒有找到合適的替代選項，您可以告訴我偏好，我再精調搜尋。"
            )

    return f"🤔 我有一個關於 Day{day} 「{ori_place}」的建議：{reason_text}"





#送題目(自然語言)給前端
def emit_reply_and_question(user_id: str, trip_id: str, data):
    # 允許字串，轉 dict
    if not isinstance(data, dict):
        data = coerce_to_json_dict(data)
        if data is None:
            socketio.emit("ai_response", {"message": "格式錯誤：非 JSON"}, room=trip_id)
            return
        

    # 假設 data 就是你貼的那包
    reply_text = (data.get("reply_text") or "").strip()
    if reply_text:
        socketio.emit("chat_message", {"user_id": "系統", "message": reply_text}, room=trip_id)

    qs = data.get("questions") or []
    if qs:
        print("到底是傳什麼")
        q = qs[0]
        choices = q.get("choices") or {}
        options = [
            {
                "choice": letter,                       # "A" / "B" / ...
                "label": (meta or {}).get("label"),
                "value": (meta or {}).get("value"),
                "key":   (meta or {}).get("key", "")
            }
            for letter, meta in choices.items()
        ]

        v2_payload = {
            "schema_version": 2,
            "question_id": "pace-1",          # 沒有也行，前端會補
            "type": "single_choice",
            "text": qs,
            "options": options
        }
        socketio.emit("ai_question_v2", {"user_id": "系統", "message": v2_payload}, room=trip_id)
        print("題目有傳出去ㄇ？")
        print("[EMIT] ai_question_v2 sent to room:", trip_id)


# 💡 【新增函式】將推薦建議轉為包含 buttons 結構的 payload
#     讓前端可以渲染可點擊的按鈕
def emit_ai_response_with_buttons(trip_id, recommendation_data):
    """
    根據 modify 建議的資料，構建包含 message, recommendation 和 buttons 的 payload，
    並發送 ai_response 事件。
    """
    new_places = recommendation_data.get('new_places', [])
    buttons = []
    
    # 構建替代地點的按鈕
    # 限制最多只顯示 5 個選項，與 generate_recommendation_prompt 保持一致
    for i, place_name in enumerate(new_places[:5]):
        # 標籤顯示編號和名稱
        # ⚠️ 注意：這裡假設 place_name 是字串。如果它是字典，需要調整 _present_place_for_prompt 的行為
        if isinstance(place_name, dict):
             # 僅使用名稱作為按鈕標籤
             label = f"{i+1}. {place_name.get('name', '替代地點')}"
        else:
             label = f"{i+1}. {place_name}"
             
        # 值為編號，與 handle_user_message 中 int(raw_message) - 1 的邏輯對應
        value = str(i + 1)
        buttons.append({"label": label, "value": value})
        
    # 加入「略過」按鈕
    buttons.append({"label": "略過", "value": "略過"})
    
    # 使用現有的函式生成純文字提示
    text_message = generate_recommendation_prompt(recommendation_data)

    payload = {
        "message": text_message, # 純文字提示（包含建議與替代選項列表）
        "recommendation": recommendation_data,
        "buttons": buttons      # 讓前端渲染按鈕
    }
    
    # 透過 socket.io 發送事件
    socketio.emit("ai_response", payload, room=trip_id)
    print(f"✅ EMIT ai_response with {len(buttons)} buttons for modify recommendation.")

# ---------- 🚀 Run ----------
if __name__ == "__main__":
    socketio.run(app, host="0.0.0.0", port=8001, debug=True, use_reloader=False)


from asgiref.wsgi import WsgiToAsgi

# 將你的 Flask app (WSGI) 包裝成 Uvicorn (ASGI) 看得懂的樣子
app = WsgiToAsgi(app)
# python -m uvicorn app:app --reload --env-file .env --port 8001