from flask import Blueprint, render_template, request, jsonify
import bcrypt
from datetime import datetime
from bson import ObjectId

# 導入你的共用函式和資料庫
from mongodb_utils import user_collection
from register_util import make_token, to_user_dto

# 建立 Blueprint
auth_bp = Blueprint("auth", __name__, url_prefix="")

@auth_bp.route("/")
def register_page():
    return render_template("register.html")

@auth_bp.route("/login_page")
def login_page():
    return render_template("login.html")

@auth_bp.route("/register", methods=["POST"])
def register():
    data = request.get_json()
    email = data.get("email")
    password = data.get("password")
    
    if not email or not password:
        return jsonify({"detail": "請輸入完整資訊"}), 400
    
    if user_collection.find_one({"email": email}):
        return jsonify({"detail": "此 Email 已註冊"}), 409
    
    hashed = bcrypt.hashpw(password.encode(), bcrypt.gensalt())
    user_collection.insert_one({
        "email": email, 
        "password": hashed,
        "username": email,
        "created_at": datetime.utcnow()
    })
    return jsonify({"message": "註冊成功"}), 201

@auth_bp.route("/login", methods=["POST"])
def login():
    data = request.get_json(force=True) or {}
    email = data.get("email", "").strip()
    password = data.get("password", "")
    
    user = user_collection.find_one({"email": email})
    if not user or not bcrypt.checkpw(password.encode(), user["password"]):
        return jsonify({"detail": "帳號或密碼錯誤"}), 401
    
    user_id = str(user["_id"])
    token = make_token(user_id)
    
    trip_id = user.get("trip_id")
    redirect_url = f"/chatroom/{trip_id}" if trip_id else None
    msg = "登入成功" if trip_id else "登入成功，但未找到綁定行程"
    
    return jsonify({
        "message": msg,
        "user": to_user_dto(user),
        "token": token,
        "redirect": redirect_url
    }), 200