from datetime import datetime

from mongodb_utils import ensure_user_slot, get_user_state, chat_question


def mark_asked_key(trip_id: str, user_id: str, qkey: str):
    ensure_user_slot(trip_id, user_id)
    now = datetime.utcnow()
    chat_question.update_one(
        {"_id": str(trip_id)},
        {"$addToSet": {f"state_by_user.{user_id}.asked_keys": qkey},
         "$set": {f"state_by_user.{user_id}.last_question_key": qkey,
                  f"state_by_user.{user_id}.updated_at": now,
                  "updated_at": now}}
    )

def add_asked_options(trip_id: str, user_id: str, qkey: str, options: list[dict]):
    """options: [{'label':..., 'value':...}, ...]"""
    ensure_user_slot(trip_id, user_id)
    now = datetime.utcnow()
    values = [ (o.get("value") or "").strip().lower() for o in options if o.get("value") ]
    labels = [ (o.get("label") or "").strip()          for o in options if o.get("label") ]

    # 先確保該 key 的歷史節點存在
    chat_question.update_one(
        {"_id": str(trip_id)},
        [
            {"$set": {
                f"state_by_user.{user_id}.asked_options_history.{qkey}": {
                    "$ifNull": [ f"$state_by_user.{user_id}.asked_options_history.{qkey}",
                                 {"values": [], "labels": []}]
                },
                f"state_by_user.{user_id}.updated_at": now,
                "updated_at": now
            }}
        ]
    )
    if values:
        chat_question.update_one(
            {"_id": str(trip_id)},
            {"$addToSet": {f"state_by_user.{user_id}.asked_options_history.{qkey}.values": {"$each": values}},
             "$set": {f"state_by_user.{user_id}.updated_at": now, "updated_at": now}}
        )
    if labels:
        chat_question.update_one(
            {"_id": str(trip_id)},
            {"$addToSet": {f"state_by_user.{user_id}.asked_options_history.{qkey}.labels": {"$each": labels}},
             "$set": {f"state_by_user.{user_id}.updated_at": now, "updated_at": now}}
        )

def mark_selected_value(trip_id: str, user_id: str, value: str):
    if not value: return
    ensure_user_slot(trip_id, user_id)
    now = datetime.utcnow()
    chat_question.update_one(
        {"_id": str(trip_id)},
        {"$addToSet": {f"state_by_user.{user_id}.selected_values": value.lower()},
         "$set": {f"state_by_user.{user_id}.updated_at": now, "updated_at": now}}
    )

def update_known_pref(trip_id: str, user_id: str, key: str, value: str):
    ensure_user_slot(trip_id, user_id)
    now = datetime.utcnow()
    chat_question.update_one(
        {"_id": str(trip_id)},
        {"$set": {f"state_by_user.{user_id}.known_prefs.{key}": value,
                  f"state_by_user.{user_id}.updated_at": now,
                  "updated_at": now}}
    )

def filter_dedupe_choices(trip_id: str, user_id: str, qkey: str, choices_list: list[dict]) -> list[dict]:
    st = get_user_state(trip_id, user_id)
    hist = (st.get("asked_options_history", {}) or {}).get(qkey, {})
    used_vals = set([v.lower() for v in hist.get("values", [])])
    used_labs = set(hist.get("labels", []))
    used_vals |= set([v.lower() for v in st.get("selected_values", [])])

    out, seen_vals, seen_labs = [], set(), set()
    for o in choices_list:
        lab = (o.get("label") or "").strip()
        val = (o.get("value") or "").strip()
        if not lab or not val: 
            continue
        lv = val.lower()
        if lab in used_labs or lv in used_vals: 
            continue
        if lab in seen_labs or lv in seen_vals: 
            continue
        seen_labs.add(lab); seen_vals.add(lv)
        out.append({"label": lab, "value": val})
        if len(out) >= 5: break

    # 至少 3 個選項才出題；否則這輪就不問
    return out if len(out) >= 3 else []
