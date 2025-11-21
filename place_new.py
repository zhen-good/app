import re
import math
import datetime as dt
import pytz

from place_gmaps import gm_alternatives
from place_node import _anchor_coords, _nodes_of_day

TZ = pytz.timezone("Asia/Taipei")

#--------------------- 小工具 ---------------------#
def _to_min(hhmm: str) -> int | None:
    try:
        h, m = map(int, str(hhmm).split(":"))
        return h * 60 + m
    except Exception:
        return None

def _parse_open_ranges(open_text: str) -> list[tuple[int, int]]:
    """
    從 '09:00–17:00' 或 '11:00–15:00、17:30–21:00' 解析出 [(start_min, end_min), ...]。
    解析不到回空陣列（代表未知）。
    """
    if not open_text:
        return []
    text = str(open_text).strip()
    # 正規化破折號、分隔符
    text = text.replace("–", "-").replace("～", "-").replace("~", "-")
    parts = re.split(r"[、/，,;]\s*", text)
    ranges: list[tuple[int, int]] = []
    for p in parts:
        m = re.search(r"(\d{1,2}:\d{2})\s*-\s*(\d{1,2}:\d{2})", p)
        if not m:
            continue
        a = _to_min(m.group(1))
        b = _to_min(m.group(2))
        if a is not None and b is not None and b > a:
            ranges.append((a, b))
    return ranges

def _overlap(slot_start_min: int, slot_end_min: int, open_ranges: list[tuple[int, int]]) -> bool:
    """判斷任一營業區段與行程時段是否有交集"""
    for a, b in open_ranges:
        if not (slot_end_min <= a or slot_start_min >= b):
            return True
    return False

def _slot_time_from_trip(trip_doc: dict, day: int, slot: str) -> tuple[int, int] | None:
    """從 trip_doc 取該日該 slot 的 (start_min, end_min)。找不到回 None。"""
    for n in (trip_doc.get("nodes") or []):
        if n.get("day") == day and n.get("slot") == slot:
            s = _to_min(n.get("start") or "00:00")
            e = _to_min(n.get("end") or "00:00")
            if s is not None and e is not None and e > s:
                return (s, e)
    return None

def _slot_datetime_range(trip_doc: dict, day: int, slot: str):
    """
    把 trip 的 day+slot 轉當地時間 (start_dt, end_dt) 與 (start_min, end_min)、weekday。
    weekday = Monday 0 ... Sunday 6
    """
    slot_range = _slot_time_from_trip(trip_doc, day, slot)
    day_obj = next((d for d in trip_doc.get("days", []) if d.get("day") == day), None)
    if not day_obj or not slot_range:
        return None, None, None, None
    date_str = day_obj.get("date")  # e.g. "2025-09-18"
    base = dt.datetime.strptime(date_str, "%Y-%m-%d")
    start_dt = TZ.localize(base + dt.timedelta(minutes=slot_range[0]))
    end_dt   = TZ.localize(base + dt.timedelta(minutes=slot_range[1]))
    weekday = start_dt.weekday()  # 0=Mon ... 6=Sun
    return start_dt, end_dt, weekday, slot_range

def _existing_names_in_slot(trip_doc: dict, day: int, slot: str) -> set[str]:
    """抓出該日該 slot 已存在的地點名稱，避免重複推薦"""
    names = set()
    for n in _nodes_of_day(trip_doc, day):
        if n.get("slot") != slot:
            continue
        for p in (n.get("places") or []):
            nm = (p.get("name") or "").strip()
            if nm:
                names.add(nm)
    return names

def _haversine_km(a: tuple[float, float] | None, b: tuple[float, float] | None) -> float:
    if not a or not b:
        return 1e9
    lat1, lon1 = a; lat2, lon2 = b
    R = 6371.0
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    x = math.sin(dlat / 2) ** 2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlon / 2) ** 2
    return 2 * R * math.asin(min(1, math.sqrt(x)))

#---------------- 主流程：找替代地點 ----------------#
def find_alternative_places_for_rec(
    rec: dict,
    trip_doc: dict,
    desired: int = 10,
    default_radius: int = 2000,
    radius_multipliers=(1.0, 1.5, 2.0, 3.0),
    prefer_open: bool = True,   # True: 開放者加權，未知/不重疊者不至於被完全剔除
    require_open: bool = False  # True: 嚴格要求開放，不重疊直接濾掉
) -> list[dict]:
    """
    依 rec 中的 by_place.contains 關鍵字、當日當段 slot 的座標與時間，搜尋替代地點。
    - 先把時段條件傳給資料源（若支援）；回來後再本地以營業時間做保險過濾/排序。
    - 依優先度排序：是否營業重疊 -> 距離 -> 評分 -> 評論數
    """
    day = rec.get("day")
    slot = rec.get("slot")
    target = rec.get("target") or {}
    by_place = target.get("by_place") or {}
    contains = by_place.get("contains") or []
    if not contains or not isinstance(contains, list):
        return []

    # 查詢關鍵字
    query = " ".join([str(x).strip() for x in contains if str(x).strip()])

    # 搜尋中心與半徑
    near_hint = rec.get("near_hint")
    base_near = _anchor_coords(trip_doc, day, slot, near_hint)  # (lat, lng) or None
    base_radius = rec.get("radius_m") or default_radius

    # 該段時間（分鐘、weekday）
    _, _, weekday, slot_range = _slot_datetime_range(trip_doc, day, slot)

    # 避免選到同名
    names_in_slot = _existing_names_in_slot(trip_doc, day, slot)

    bag: dict[str, dict] = {}
    for mul in radius_multipliers:
        radius = int(base_radius * mul)

        rows = gm_alternatives(
            query=query,
            near=base_near,
            radius_m=radius,
            max_results=desired,
            open_filter={
                "weekday": weekday,
                "start_min": slot_range[0] if slot_range else None,
                "end_min":   slot_range[1] if slot_range else None,
                "require_open": require_open,
            }
        )

        for r in rows or []:
            name = (r.get("name") or "").strip()
            pid  = r.get("place_id")
            lat  = r.get("lat"); lng = r.get("lng")
            if not name or not pid or lat is None or lng is None:
                continue
            if name in names_in_slot:
                continue

            # 客端保險：用 open_text 判交集（若 rows 已含結構化開放資訊，可在 gm_alternatives 先行處理）
            is_open_match = None
            if slot_range:
                open_ranges = _parse_open_ranges(r.get("open_text"))
                if open_ranges:
                    is_open_match = _overlap(slot_range[0], slot_range[1], open_ranges)

            # 嚴格模式：不重疊就剔除
            if require_open and (is_open_match is False):
                continue

            r["_is_open_match"] = is_open_match
            bag[pid] = r

        if len(bag) >= desired:
            break

    items = list(bag.values())

    # 排序：開放重疊優先 → 距離近 → 評分高 → 評論多
    def _score(x: dict):
        rt = x.get("rating") or 0.0
        rv = x.get("reviews") or 0
        dist = _haversine_km(base_near, (x.get("lat"), x.get("lng"))) if base_near else 9999

        if prefer_open:
            flag = x.get("_is_open_match")
            if flag is True:
                open_penalty = 0        # 完全重疊最優
            elif flag is False:
                open_penalty = 1000     # 明確不重疊往後
            else:
                open_penalty = 100      # 未知小幅往後
        else:
            open_penalty = 0

        return (open_penalty, dist, -float(rt), -int(rv))

    items.sort(key=_score)
    print("這有值習到ㄇ阿",items)
    return items[:desired]
