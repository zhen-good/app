# app.py
import json
from dotenv import find_dotenv, load_dotenv
from chat_nature import coerce_to_json_dict, handle_extra_chat
from place_gmaps import search_candidates
from place_node import _anchor_coords
from flask import jsonify, request
import re

import traceback
from flask import Flask, render_template, request, jsonify, session
from flask_socketio import SocketIO, emit, join_room
from bson import ObjectId
import string, random, os
from friend import friends_bp
from register import auth_bp
import socket 
from config import user_chains

# 🔧 工具與模組
from chat_manager import (
    decide_location_placement,
    display_trip_by_trip_id,
    analyze_active_users_preferences,
    detect_add_location_intent,
)
from config import pending_add_location
from preference import update_user_preferences, extract_preferences_from_text
from mongodb_utils import (
    trips_collection,  # 💡 使用新的 trips_collection
    get_trip_by_id,
    add_to_itinerary,
    delete_from_itinerary,
    modify_itinerary,
    save_message_to_mongodb #將題目存進mongodb
)
from utils import get_user_chain

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


# 在 app.py 顶部加入
def get_local_ip():
    """获取本地网络 IP"""
    try:
        # 连接到外部地址（不会真的连接）来确定本机IP
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception as e:
        return f"无法获取IP: {e}"


#=========api========

app = Flask(__name__)
app.register_blueprint(friends_bp)
app.register_blueprint(auth_bp)

app.config["SECRET_KEY"] = "your_secret_key"
socketio = SocketIO(
    app, 
    cors_allowed_origins="*",  # 允許所有來源
    async_mode='gevent',    # 或 'eventlet'
    logger=True,               # 開啟 log
    engineio_logger=True       # 開啟詳細 log
)

pending_recommendations = {}


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

    emit("chat_message", {"user_id": "系統", "message": f"{user_name} 已加入聊天室 {trip_id}"}, room=trip_id)

    
    # doc = trips_collection.find_one({"_id": trip_id_ob}, {"_id": 0, "nodes": 1})
    #這邊是一開始會先傳一個trip的行程給使用者看
    trip_text = display_trip_by_trip_id(trip_id_ob)
    print(trip_text)
    emit("trip", {"user_id": "系統", "message": trip_text}, room=trip_id)
    emit("chat_message", {"user_id": "系統", "message": f"請跟我說說你對本次行程的看法吧~"}, room=trip_id)


# app.py (修正後的 handle_user_message 函式)

@socketio.on("user_message")
def handle_user_message(data):
    """
    處理使用者訊息的主要函式
    優先順序：特殊指令 → pending 狀態 → 新指令 → 意圖偵測 → 一般對話
    """
    print("使用者資料\n", data)
    
    # ========== 基本驗證和初始化 ==========
    user_id = data.get("user_id")
    trip_id = data.get("trip_id")
    raw_message = data.get("message", "").strip()
    payload = data.get("payload") or {}


    if not user_id or not trip_id:
        return
    
    save_message_to_mongodb(trip_id, user_id, "user", raw_message)
    trip_id_ob = ObjectId(trip_id)

    # 定義關鍵字
    accept_keywords = {"是", "好", "接受", "確認", "加入", "同意"}
    reject_keywords = {"否", "略過", "不要", "取消"}

    # ========== 1. 特殊指令：查看行程 ==========
    if raw_message in {"行程", "我的行程", "查看行程"}:
        try:
            doc = trips_collection.find_one({"_id": trip_id_ob}, {"_id": 0, "nodes": 1})
            nodes = (doc or {}).get("nodes", [])

            if not nodes:
                emit("ai_response", {"message": "❗ 找不到此行程（trip_id 不存在或已被刪除）。"}, room=trip_id)
                return

            trip_text = display_trip_by_trip_id(trip_id_ob)
            emit("trip", {"user_id": "系統", "message": trip_text}, room=trip_id)
            emit("ai_response", {"message": "🧭 已送出目前行程資訊到畫面。"}, room=trip_id)

        except Exception as e:
            traceback.print_exc()
            emit("ai_response", {"message": f"❗ 讀取行程時發生錯誤：{e}"}, room=trip_id)
        return

    # ========== 2. 處理待新增景點的回覆 ==========
    if user_id in pending_add_location:
        place_to_add = pending_add_location[user_id]
        
        if raw_message in accept_keywords:
            try:
                placement_result = decide_location_placement(user_id, trip_id_ob, place_to_add)
                day = placement_result.get("day")
                period = placement_result.get("period")
                
                if day and period:
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
                    return
                    
                pending_add_location.pop(user_id)
                
            except Exception as e:
                traceback.print_exc()
                emit("ai_response", {"message": f"❗ 新增景點時發生錯誤：{e}"}, room=trip_id)
                pending_add_location.pop(user_id)
            return
            
        elif raw_message in reject_keywords:
            pending_add_location.pop(user_id)
            emit("ai_response", {"message": "👌 好的，已取消新增景點。"}, room=trip_id)
            return
            
        # 處理指定天數的回覆
        day_match = re.match(r'[Dd]ay(\d+)', raw_message)
        if day_match:
            try:
                day = int(day_match.group(1))
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
            return
        
        # 其他情況，重新提示
        emit("ai_response", {
            "message": f"🤔 請回覆「加入」、「略過」，或指定天數如「Day1」來新增「{place_to_add}」。"
        }, room=trip_id)
        return

    # ========== 3. 處理待處理的「行程修改」建議 ==========
    if user_id in pending_recommendations and pending_recommendations[user_id]:
        recommendations = pending_recommendations[user_id]
        current_rec = recommendations[0]

        # 處理 modify 建議的回覆
        if current_rec["type"] == "modify":
            original_place_name = current_rec.get('place') 
            original_place_id = current_rec.get('place_id')
            suggested_places = current_rec.get('new_places', [])
            
            user_choice = None
            
            # 檢查是否為數字編號回覆
            try:
                choice_index = int(raw_message) - 1
                if 0 <= choice_index < len(suggested_places):
                    user_choice = suggested_places[choice_index]
            except ValueError:
                pass
                
            # 檢查是否為地點名稱
            if not user_choice:
                for cand in suggested_places:
                    if isinstance(cand, dict):
                        name = str(cand.get("name", "")).lower()
                    else:
                        name = str(cand).lower()

                    if raw_message.lower() == name or raw_message.lower() in name or name in raw_message.lower():
                        user_choice = cand
                        break
            
            # 處理「略過」回覆
            if raw_message.lower() in ("略過", "skip", "pass"):
                emit("ai_response", {
                    "message": f"✅ 已略過 Day{current_rec['day']} 對「{original_place_name}」的修改建議。"
                }, room=trip_id)
                
                recommendations.pop(0)
                
                if recommendations:
                    next_rec = recommendations[0]
                    if next_rec.get('type') == 'modify':
                        emit_ai_response_with_buttons(trip_id, next_rec)
                    else:
                        next_prompt = generate_recommendation_prompt(next_rec)
                        emit("ai_response", {"message": next_prompt}, room=trip_id)
                else:
                    pending_recommendations.pop(user_id)
                    emit("ai_response", {"message": "✅ 所有建議已處理完畢。"}, room=trip_id)
                return

            # 處理成功的選擇
            if user_choice:
                try:
                    print(f"🔧 嘗試修改：trip_id={trip_id}, day={current_rec['day']}, old_place={original_place_name}, new_place={user_choice}")
                    
                    success = modify_itinerary(trip_id, current_rec["day"], original_place_id, user_choice)
                    
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
                        
                    recommendations.pop(0)
                    
                    if recommendations:
                        next_rec = recommendations[0]
                        if next_rec.get('type') == 'modify':
                            emit_ai_response_with_buttons(trip_id, next_rec)
                        else:
                            next_prompt = generate_recommendation_prompt(next_rec)
                            emit("ai_response", {"message": next_prompt}, room=trip_id)
                    else:
                        pending_recommendations.pop(user_id)
                        emit("ai_response", {"message": "✅ 所有建議已處理完畢。"}, room=trip_id)
                        
                except Exception as e:
                    print(f"❌ 處理修改建議時發生錯誤: {e}")
                    emit("ai_response", {"message": f"伺服器錯誤：無法處理您的選擇。錯誤：{e}"}, room=trip_id)
                return
                    
            else:
                # 處理無效回覆
                emit("ai_response", {"message": "⚠️ 無效的選擇，請點擊按鈕或回覆數字編號 (如: 1) 或 略過。"}, room=trip_id)
                emit_ai_response_with_buttons(trip_id, current_rec)
                return

        # 處理 add 或 delete 建議的回覆
        elif current_rec["type"] in ["add", "delete"]:
            if raw_message in accept_keywords:
                try:
                    success = False
                    if current_rec["type"] == "delete":
                        success = delete_from_itinerary(trip_id_ob, current_rec["day"], current_rec["ori_place"])
                        if success:
                            emit("ai_response", {"message": f"✅ 已從 Day{current_rec['day']} 刪除「{current_rec['ori_place']}」。"}, room=trip_id)
                        else:
                            emit("ai_response", {"message": f"❗ 刪除「{current_rec['place']}」時發生錯誤。"}, room=trip_id)
                            
                    elif current_rec["type"] == "add":
    
                        # 1. 從 current_rec 提取必要的參數 (所有參數都來自 _process_add_recommendation 的輸出)
                        # 由於我們已經在 _process_add_recommendation 中限制了 new_places 只有一個景點，
                        # 我們可以直接取用它。
                        new_places = current_rec.get("new_places", [])
                        if not new_places:
                            emit("ai_response", {"message": "❗ 新增景點時發生錯誤：景點資料缺失。"}, room=trip_id)
                            return
                            
                        place_to_add = new_places[0]
                        
                        day_to_add = current_rec.get("recommend_day")
                        action_to_use = current_rec.get("recommend_action")
                        node_id_ref_to_use = current_rec.get("recommend_node_id")
                        
                        # 2. 呼叫修正後的 add_to_itinerary 函式
                        result = add_to_itinerary(
                            trip_id_ob, 
                            day_to_add, 
                            place_to_add, # 傳遞完整的景點資料
                            action_to_use, 
                            node_id_ref_to_use # 傳遞 LLM 建議的插入位置參考
                        )

                        # 3. 處理結果
                        if result and "error" not in result:
                            emit("ai_response", {"message": f"✅ 已將「{place_to_add.get('name')}」新增到 Day{day_to_add}。"}, room=trip_id)
                        else:
                            emit("ai_response", {"message": f"❗ 新增「{place_to_add.get('name')}」時發生錯誤：{result.get('error', '未知錯誤')}。"}, room=trip_id)

                    if success:
                        recommendations.pop(0)
                        
                        if recommendations:
                            next_rec = recommendations[0]
                            if next_rec.get('type') == 'modify':
                                emit_ai_response_with_buttons(trip_id, next_rec)
                            else:
                                next_prompt = generate_recommendation_prompt(next_rec)
                                emit("ai_response", {"message": next_prompt}, room=trip_id)
                        else:
                            pending_recommendations.pop(user_id)
                            emit("ai_response", {"message": "✅ 所有建議已處理完畢。"}, room=trip_id)
                        
                except Exception as e:
                    traceback.print_exc()
                    emit("ai_response", {"message": f"❗ 處理建議時發生錯誤：{e}"}, room=trip_id)
                return

            elif raw_message in reject_keywords:
                emit("ai_response", {"message": "👌 已略過此建議。"}, room=trip_id)
                
                recommendations.pop(0)
                
                if recommendations:
                    next_rec = recommendations[0]
                    if next_rec.get('type') == 'modify':
                        emit_ai_response_with_buttons(trip_id, next_rec)
                    else:
                        next_prompt = generate_recommendation_prompt(next_rec)
                        emit("ai_response", {"message": next_prompt}, room=trip_id)
                else:
                    pending_recommendations.pop(user_id)
                    emit("ai_response", {"message": "✅ 所有建議已處理完畢。"}, room=trip_id)
                return

    # ========== 4. 特殊指令：分析 ==========
    if raw_message in {"分析", "更換"}:
        try:
            print("找一下trip_id", trip_id)
            
            # 清空所有 pending 狀態
            if user_id in pending_recommendations:
                pending_recommendations.pop(user_id)
            if user_id in pending_add_location:
                pending_add_location.pop(user_id)
            
            recommendations_list = analyze_active_users_preferences(user_id,user_chains, trip_id_ob)
            
            if recommendations_list:
                pending_recommendations[user_id] = recommendations_list
                first_rec = recommendations_list[0]
                
                if first_rec.get('type') == 'modify':
                    emit_ai_response_with_buttons(trip_id, first_rec)
                    print("有發出button嗎")
                else:
                    first_prompt = generate_recommendation_prompt(first_rec)
                    
                    payload = {
                        "message": first_prompt,
                        "recommendation": {
                            "type": first_rec['type'],
                            "day": first_rec['day'],
                            "place": first_rec['place'],
                            "reason": first_rec['reason'],
                            "new_places": first_rec.get('new_places', [])
                        }
                    }
                    
                    emit("ai_response", payload, room=trip_id)
            else:
                emit("ai_response", {"message": "👌 我已仔細評估過您的行程，目前看來規劃得非常符合您的偏好，沒有需要修改的地方！"}, room=trip_id)
                
        except Exception as e:
            traceback.print_exc()
            emit("ai_response", {"message": f"❗ 分析與優化失敗：{e}"}, room=trip_id)
        return

    # ========== 5. 處理新增地點意圖 ==========
    try:
        intent = detect_add_location_intent(raw_message)
        if intent["add_location"] and intent["place_name"]:
            place = intent["place_name"]

            trip_doc = get_trip_by_id(trip_id) or {}
            near = _anchor_coords(trip_doc, day=None, slot=None, near_hint="slot_node")

            candidates = search_candidates(
                query=place,
                near=near,
                radius_m=15000,
                max_results=5,
                enrich_opening=False
            ) or []

            if candidates:
                top = candidates[0]
                canonical_name = top.get("name") or place

                if user_id in pending_recommendations:
                    pending_recommendations.pop(user_id)

                pending_add_location[user_id] = canonical_name

                addr = top.get("address") or f"{top.get('lat')},{top.get('lng')}"
                url = top.get("map_url") or ""
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

    # ========== 6. 處理偏好擷取 ==========
    try:
        prefs = extract_preferences_from_text(raw_message)
        if prefs["prefer"] or prefs["avoid"]:
            update_user_preferences(
                user_id=user_id,
                trip_id=trip_id,
                prefer_add=prefs.get("prefer"),
                avoid_add=prefs.get("avoid"),
            )
            
            if user_id in pending_recommendations:
                pending_recommendations.pop(user_id)
            if user_id in pending_add_location:
                pending_add_location.pop(user_id)
                
            print(f"✅ 已更新 {user_id} 的偏好：", prefs)
            
    except Exception as e:
        print(f"⚠️ 偏好擷取失敗：{e}")
        traceback.print_exc()

    # ========== 7. 一般對話（Fallback） ==========
    try:
        emit("chat_message", {
            "user_id": user_id,
            "message": raw_message
        }, room=trip_id)
        print("處理一般對話")
        
        out = handle_extra_chat(user_id, trip_id_ob, raw_message)
        
        if out:
            print("成功")
            emit_reply_and_question(user_id, trip_id, out)
        else:
            socketio.emit("ai_response", {"message": str(out)}, room=trip_id)
            
    except Exception as e:
        print(f"❌ 一般對話處理錯誤: {e}")
        traceback.print_exc()
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
        print("看一下要新增的景點是哪一個", recommendation)
        
        # 1. 提取唯一的推薦景點
        new_places = recommendation.get("new_places", [])
        
        # 🚨 確保 new_places 至少有一個元素 (由於上游邏輯的保證，理論上只有一個)
        if not new_places:
            place_info = "（景點資訊缺失）"
            place_name = "（建議地點）"
        else:
            # 取出列表中的第一個（也是唯一一個）景點物件
            place_data = new_places[0] 
            
            # 使用 _present_place_for_prompt 格式化詳細資訊
            place_info = _present_place_for_prompt(place_data)
            place_name = place_data.get("name", "（建議地點）")

        # 2. 構建給使用者的提示訊息
        return (
            f"🌟 **建議新增景點：{place_name}**\n\n"
            f"📍 建議新增至：Day{day} 的 {recommendation.get('recommend_slot', '合適時段')}\n"
            f"✅ 建議原因：{reason_text}\n"
            f"ℹ️ 詳細資訊：{place_info}\n\n"
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
def emit_ai_response_with_buttons(trip_id, recommendation_data):
    new_places = recommendation_data.get('new_places', [])
    print(f"DEBUG - new_places type: {type(new_places)}")
    print(f"DEBUG - new_places length: {len(new_places) if isinstance(new_places, list) else 'NOT A LIST'}")
    
    if new_places and isinstance(new_places, list):
        print(f"DEBUG - first item: {json.dumps(new_places[0], ensure_ascii=False, default=str)[:200]}")
    
    buttons = []
    
    for i, place in enumerate(new_places[:3]):
        print(f"DEBUG - processing place {i}: {type(place)}")
        
        if isinstance(place, dict):
            place_name = place.get('name', '替代地點')
        else:
            place_name = str(place)
        
        label = f"{i+1}. {place_name}"
        buttons.append({"label": label, "value": str(i + 1)})
    
    buttons.append({"label": "略過", "value": "略過"})
    
    print(f"DEBUG - buttons: {json.dumps(buttons, ensure_ascii=False)}")
    
    text_message = generate_recommendation_prompt(recommendation_data)

    payload = {
        "message": text_message,
        "recommendation": {
            "type": recommendation_data.get('type'),
            "day": recommendation_data.get('day'),
            "place": recommendation_data.get('place'),
            "city": recommendation_data.get('city'),
            "reason": recommendation_data.get('reason'),
        },
        "buttons": buttons
    }
    
    print(f"DEBUG - final payload buttons: {json.dumps(payload['buttons'], ensure_ascii=False)}")
    
    socketio.emit("ai_response", payload, room=trip_id)

# ---------- 🚀 Run ----------
# 或者在启动时直接显示
if __name__ == "__main__":
    local_ip = get_local_ip()
    print("=" * 50)
    print(f"🌐 本地网络 IP: {local_ip}")
    print(f"📍 服务器地址: http://{local_ip}:8001")
    print("=" * 50)
    socketio.run(app, host="0.0.0.0", port=8001, debug=True, use_reloader=False)
