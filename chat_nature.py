import json
import os
import re

from bson import ObjectId
from langchain_openai import ChatOpenAI

from mongodb_utils import get_user_state
from preference import extract_preferences_from_text, load_preferences_by_trip_id, update_user_preferences,load_preferences_by_trip_id

#聊天模型

def get_chat_llm_openai():
    """
    只給『聊天/引導偏好』用的 ChatGPT。
    其他模組仍用你原本的 Gemini。
    """
    # 🔍 加入除錯
    api_key = os.getenv("OPENAI_API_KEY")
    
    print("=" * 60)
    print("🔍 chat_nature.py - 檢查 OPENAI_API_KEY")
    print("=" * 60)
    
    if api_key:
        print(f"✅ API Key 已找到")
        print(f"   前 10 字元: {api_key[:10]}")
        print(f"   後 4 字元: ...{api_key[-4:]}")
        print(f"   長度: {len(api_key)}")
    else:
        print("❌ API Key 未找到!")
        print(f"   請檢查 .env 檔案是否存在")
        print(f"   目前工作目錄: {os.getcwd()}")
        print(f"   環境變數 OPENAI_API_KEY: {api_key}")
        raise RuntimeError("缺少 OPENAI_API_KEY 環境變數")
    
    print("=" * 60)
    
    return ChatOpenAI(
        model="gpt-4o",
        api_key=api_key,  # ✅ 明確傳入 API Key
        temperature=0.3,
        max_tokens=768,
    )

PREF_TRIGGERS = [
    "喜歡", "偏好", "想吃", "不吃", "不要", "不想", "怕", "過敏",
    "帶小孩", "帶長輩", "行動不便", "走太多", "想放鬆", "太趕",
    "預算", "太貴", "便宜", "素食", "清真", "辣", "甜", "不喝酒",
]

def should_extract_preferences(text: str) -> bool:
    t = text.strip()
    if not t: return False
    if any(k in t for k in PREF_TRIGGERS): return True
    # 也可加入 NER / 情感判斷等
    return False


#自然地跟使用者對話並引導他提供旅遊偏好

def handle_extra_chat(user_id: str, trip_id: str, user_message: str):
    # 1) 讀現有偏好
    chat_llm = get_chat_llm_openai()
    trip_id_ob = ObjectId(trip_id)
    pref = load_preferences_by_trip_id(trip_id_ob) or {}
    def j(key): return "、".join(sorted(set(pref.get(key, [])))) or "無"

    st = get_user_state(trip_id, user_id)
    known_prefs        = st.get("known_prefs", {})
    known_prefs_keys   = list(known_prefs.keys())
    last_question_key  = st.get("last_question_key")
    asked_options_hist = st.get("asked_options_history", {})
    selected_values    = st.get("selected_values", [])

    # 2) 是否觸發擷取
    just_updated = False
    newly = {}
    if should_extract_preferences(user_message):
        # 你現有的擷取器：回傳 {prefer:[], avoid:[], diet:[], ...}
        extracted = extract_preferences_from_text(user_message) or {}
        # 寫入（你已有 upsert 方法）
        if any(extracted.get(k) for k in ["prefer","avoid"]):
            newly = update_user_preferences(
                user_id=user_id, trip_id=trip_id,
                prefer_add=extracted.get("prefer"),
                avoid_add=extracted.get("avoid")
            ) or {}
            pref = load_preferences_by_trip_id(trip_id_ob) or {}
            just_updated = True

    # 3) 建 Prompt 丟 Chat LLM（引導）
    like, avoid = j("prefer"), j("avoid")
    prompt = f"""
    你是一位幽默又貼心的旅遊顧問，回答要自然、專業、輕鬆（可微微搞笑但不要過火）。請用繁體中文。

    已知偏好（可為空）：
    - 喜歡：{like}
    - 避免：{avoid}

    使用者訊息：{user_message}
    本輪是否剛更新偏好：{"true" if just_updated else "false"}
    若剛更新，更新了：{", ".join([k for k,v in newly.items() if v]) or "無"}

    # 已知哪些 key 已經有值（避免重問）
    known_prefs_keys = {known_prefs_keys}   # 例如 ["diet","budget"]

    # 上一輪問過的題目 key（避免馬上重複同一題）
    last_question_key = {last_question_key!r}  # 例如 "pace" 或 None

    # 對各 key 已經出過/選過的選項（用於避免選項重複）
    asked_options_history = {asked_options_hist}
    selected_values = {selected_values}

    任務：
    1) reply_text：
    - 最多兩句。若有更新，第一句用「✅ 已記下：…」確認；第二句承接/延伸使用者訊息並自然過渡。
    - 若沒有更新，也請承接使用者訊息，避免直接跳題。

    2) 出題規則（最多 1 題）：
    - 優先「根據使用者訊息延伸或追問」。只有當無明顯延伸點時，才從尚未決定的 key 中挑題：
    - 可選 key 範圍：["diet","pace","budget","prefer","avoid","time_windows","mobility","weather_plan"]
    - 排除：known_prefs_keys，並避免與 last_question_key 相同。
    - 題目要像心理測驗/小遊戲，使用過渡語開頭（例如：「說到這個…」「既然你喜歡…那…」）。
    - 問題 ≤ 18 字；選項 label ≤ 12 字；value 為乾淨英文。
    - **選項去重（重要）**：
    - 同一題內的 label/value 不能重複。
    - 不得重用 asked_options_history[key] 裡的 label/value。
    - 不得出現 selected_values 中的 value。
    - 若獨特選項不足 3 個，請輸出 "questions":[]（不要硬湊）。

    3) 輸出格式：
    - 必須只輸出有效 JSON，不能有 ```json 標籤或多餘文字。
    - 頂層必含 "reply_text" 與 "questions"。
    - reply_text：字串，最多兩句。
    - questions：陣列，空或只含一題。
    - 每題物件必含：
    - key：字串（英數底線，例如 diet、pace、budget）
    - question：字串（短句，顯示在 UI）
    - choices：物件（鍵為 "A"～"E" 的子集合），每個鍵的值是物件，必含：
        - label：字串（按鈕顯示文字）
        - value：字串（直接寫入資料庫的值）
    """

    reply = chat_llm.invoke(prompt).content
    return reply




def coerce_to_json_dict(out):
    s = out.strip()

    # 移除 ```json ... ``` 圍欄
    if s.startswith("```"):
        s = re.sub(r"^```[a-zA-Z0-9]*\s*", "", s)
        s = re.sub(r"\s*```$", "", s)

    # 直接 parse
    try:
        data = json.loads(s)
        if isinstance(data, dict):
            return data
    except Exception:
        # 退一步：抓第一個 {...} 區塊再 parse
        m = re.search(r"\{.*\}", s, flags=re.DOTALL)
        if m:
            try:
                data = json.loads(m.group(0))
                if isinstance(data, dict):
                    return data
            except Exception:
                pass

