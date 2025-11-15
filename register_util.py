#註冊用的小工具：生成憑證(但我也不是很了解)
from datetime import datetime, timedelta

import jwt


JWT_SECRET = "replace_me_with_a_strong_secret"
JWT_ALG = "HS256"
JWT_EXPIRE_MIN = 7  # days


def make_token(user_id: str):
    now = datetime.utcnow()
    payload = {
        "sub": user_id,
        "iat": now,
        "exp": now + timedelta(days=JWT_EXPIRE_MIN),
    }
    return jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALG)

def to_user_dto(user_doc: dict):
    return {
        "_id": str(user_doc["_id"]),
        "username": user_doc.get("username") or user_doc.get("name") or "",
        "email": user_doc.get("email") or "",
    }
