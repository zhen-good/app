import os
import re
from typing import Any, Dict, List, Set, Tuple, Optional
import googlemaps
from place_util import as_place

# 1) 初始化 Google Maps 客戶端（從環境變數抓 API Key）
from dotenv import find_dotenv, load_dotenv
load_dotenv(find_dotenv(), override=True)
_API_KEY = os.getenv("GOOGLE_API_KEY")
if not _API_KEY:
    raise RuntimeError("環境變數 GOOGLE_API_KEY 未設定")

_gmaps = googlemaps.Client(key=_API_KEY)

# ---------用google_maps找替代景點------------#

from typing import Optional, Tuple, List, Set

def search_candidates(
    query: str,
    near: Optional[Tuple[float, float]] = None,
    radius_m: int = 3000,
    max_results: int = 10,
    *,
    enrich_opening: bool = False,   # 是否補拉營業時間
    enrich_limit: int = 20          # 最多對幾筆打 Place Details
) -> List[dict]:
    """
    用 Text Search 在 near 周邊找與 query 相關的地點，並（可選）用 Place Details 補營業時間。
    回傳已透過 as_place() 標準化後的清單。
    """
    params = {"query": query, "language": "zh-TW"}
    if near:
        params["location"] = near
        params["radius"] = radius_m

    # 1) Text Search
    resp = _gmaps.places(**params)
    results = resp.get("results", [])

    # 翻頁補齊
    while "next_page_token" in resp and len(results) < max_results:
        import time
        time.sleep(2.0)  # 官方建議
        resp = _gmaps.places(query=query, page_token=resp["next_page_token"], language="zh-TW")
        results.extend(resp.get("results", []))

    # 去重（place_id）
    seen = set(); uniq = []
    for r in results:
        pid = r.get("place_id")
        if pid and pid not in seen:
            seen.add(pid); uniq.append(r)
        if len(uniq) >= max_results:
            break

    # 2) 標準化
    normalized = [as_place(r) for r in uniq]
    normalized = [p for p in normalized if p.get("place_id") and p.get("name")]

    # 3) （可選）用 Place Details 補營業時間 periods
    if enrich_opening and normalized:
        for p in normalized[:enrich_limit]:
            pid = p["place_id"]
            try:
                details = _gmaps.place(
                    place_id=pid,
                    language="zh-TW",
                    fields=[
                        "current_opening_hours",
                        "opening_hours",
                        "secondary_opening_hours",
                        "utc_offset",
                    ],
                )
                res = (details or {}).get("result", {}) or {}

                # 先用 current_opening_hours，沒有再用 opening_hours
                oh = res.get("current_opening_hours") or res.get("opening_hours") or {}

                # 收集 keys 方便除錯
                p["_detail_keys"] = list(res.keys())

                # periods（結構化時段）
                p["opening_hours_periods"] = oh.get("periods") or []
                # print("\n確認一下營業時間：", p["opening_hours_periods"])  # 需要時開

                # 文字週表
                p["weekday_text"] = oh.get("weekday_text") or []

                # 即時狀態（若有）
                if "open_now" in oh:
                    p["open_now"] = oh["open_now"]

                # 時區位移（分鐘）
                if "utc_offset" in res:
                    p["utc_offset_minutes"] = res["utc_offset"]

            except Exception as e:
                print(f"[WARN] place details failed for {pid}: {e}")

    return normalized


# ---------將找到的景點作一些篩選----------------#

def _to_min(hhmm: str) -> Optional[int]:
    try:
        h, m = map(int, str(hhmm).strip().split(":"))
        return h * 60 + m
    except Exception:
        return None

def _overlap(slot_start_min: int, slot_end_min: int, open_ranges: List[Tuple[int, int]]) -> bool:
    """任一營業區段與行程時段有交集即 True。"""
    for a, b in open_ranges:
        if not (slot_end_min <= a or slot_start_min >= b):
            return True
    return False

def _hhmm_to_min(hhmm: str) -> Optional[int]:
    if not hhmm:
        return None
    s = str(hhmm).strip()
    if len(s) < 4:
        s = s.zfill(4)
    try:
        return int(s[:2]) * 60 + int(s[2:4])
    except Exception:
        return None

def _merge_ranges(ranges: List[Tuple[int, int]]) -> List[Tuple[int, int]]:
    if not ranges:
        return []
    ranges.sort()
    out = [ranges[0]]
    for a, b in ranges[1:]:
        la, lb = out[-1]
        if a <= lb:  # 相交或相鄰
            out[-1] = (la, max(lb, b))
        else:
            out.append((a, b))
    print("這又是什麼的鬼",out)
    return out

def _periods_to_ranges(periods: List[dict], weekday_py: int) -> List[Tuple[int, int]]:
    """
    將 Google opening_hours.periods 轉成「Python weekday（0=Mon…6=Sun）這一天」的分鐘區段列表。
    - 支援跨日（如 22:00→02:00）：切成 (22:00,24:00) 與 (00:00,02:00)，並只回傳屬於目標日的部分。
    - 支援 truncated：
        open.time==0000 且 open.truncated=True → 視為從前一日延續到本日的開頭
        close.time==2359 且 close.truncated=True → 視為營業直到 24:00（當天取到 1440）
    """

    # 轉成 Google 的 day 座標（0=Sun…6=Sat）
    g_target = (weekday_py + 1) % 7

    day_ranges: List[Tuple[int, int]] = []
    for p in periods or []:
        o = (p.get("open") or {})
        c = (p.get("close") or {})

        if "day" not in o or "time" not in o:
            continue

        od = int(o["day"])
        ot = _hhmm_to_min(o.get("time"))

        cd = c.get("day")
        ct = _hhmm_to_min(c.get("time")) if "time" in c else None

        o_trunc = bool(o.get("truncated"))
        c_trunc = bool(c.get("truncated"))

        if ot is None:
            continue

        # truncated 邊界處理
        if c and ct is not None and c_trunc and ct == 23*60 + 59:
            ct = 24*60
        open_from_prev = bool(o_trunc and ot == 0)

        if c and (cd is not None) and (ct is not None):
            cd = int(cd)
            if od == cd:
                # 同日
                if od == g_target and ct > ot:
                    day_ranges.append((ot, ct))
            else:
                # 跨日（通常 cd == (od+1)%7）
                if od == g_target:
                    day_ranges.append((ot, 24*60))   # 開店日的尾段
                if cd == g_target and ct > 0:
                    day_ranges.append((0, ct))       # 關店日的頭段
        else:
            # 無 close → 視為到 24:00
            if od == g_target:
                day_ranges.append((ot, 24*60))
            # open_from_prev 與多段拆分多由其他 period 補齊，避免重複推斷
    
    return _merge_ranges(day_ranges)

def _parse_open_text_ranges(open_text: str) -> List[Tuple[int, int]]:
    """從 '09:00–17:00、18:00–21:00' 解析區段（fallback 用）。"""
    if not open_text:
        return []
    text = str(open_text).strip().replace("–", "-").replace("～", "-").replace("~", "-")
    parts = re.split(r"[、/，,;]\s*", text)
    out: List[Tuple[int, int]] = []
    for p in parts:
        m = re.search(r"(\d{1,2}:\d{2})\s*-\s*(\d{1,2}:\d{2})", p)
        if not m:
            continue
        a = _to_min(m.group(1)); b = _to_min(m.group(2))
        if a is not None and b is not None and b > a:
            out.append((a, b))
    return out

def _to_hhmm(mins: int) -> str:
    """將分鐘數轉 'HH:MM'。"""
    h = mins // 60
    m = mins % 60
    return f"{h:02d}:{m:02d}"

def _ranges_to_text(ranges: List[Tuple[int, int]]) -> str:
    """將 [(start_min, end_min), ...] 轉成 'HH:MM–HH:MM, ...'。"""
    return ", ".join(f"{_to_hhmm(s)}–{_to_hhmm(e)}" for s, e in ranges)


#成品過濾器：把從search_candidates的結果進行篩選
def gm_alternatives(
    query: str,
    near: Optional[Tuple[float, float]],
    radius_m: int = 2000,
    max_results: int = 5,
    *,
    open_filter: Optional[dict] = None,
    exclude_names: Optional[Set[str]] = None
) -> List[dict]:
    """
    用 Google Maps 搜尋 query，回傳最多 max_results 個候選地點（已過濾）。
    - 會先用 Text Search 抓候選，並以 Place Details enrich 營業時間 (opening_hours.periods)。
    - 若提供 open_filter = {weekday, start_min, end_min, require_open}：
        * 以 periods 優先、weekday_text（字串解析）次之，比對指定時段
        * require_open=True 時，非重疊/未知將被過濾
        * 在輸出加上 `_is_open_match`：True/False/None
    - 輸出額外包含：
        * opening_hours_periods：Google 結構化 periods（原樣）
        * weekday_text：一週營業文字（list[str]）
        * weekday_text_str：一週營業文字（單一字串，'\n' 相連）
        * hours_today_ranges：當天的分鐘區間 list[(start,end)]
        * hours_today_text：當天可讀字串 'HH:MM–HH:MM, ...'
        * open_now：是否營業中（若有）
        * utc_offset_minutes：時區位移（分鐘）（若有）
        * open_text：= weekday_text_str（相容舊流程/前端）
    """
    exclude_names = exclude_names or set()

    # 解析 open_filter
    weekday_py = start_min = end_min = None
    require_open = False
    if isinstance(open_filter, dict):
        weekday_py = open_filter.get("weekday")      # Python weekday (0=Mon…6=Sun)
        start_min  = open_filter.get("start_min")
        end_min    = open_filter.get("end_min")
        require_open = bool(open_filter.get("require_open"))

    # 取候選（同時 enrich 營業時間）
    results = search_candidates(
        query=query,
        near=near,
        radius_m=radius_m,
        max_results=max_results,       # 這裡就取精準數量；想多抓可以自己 *4
        enrich_opening=True,           # 抓營業時間
        enrich_limit=max_results * 4
    )

    cleaned: List[dict] = []
    seen: Set[str] = set()

    for r in results or []:
        name = (r.get("name") or "").strip()
        pid  = r.get("place_id")
        lat  = r.get("lat"); lng = r.get("lng")
        if not name or not pid or lat is None or lng is None:
            continue
        if name in exclude_names or pid in seen:
            continue

        # 先把可用的營業資訊撈出來
        periods: List[Dict[str, Any]] = r.get("opening_hours_periods") or []
        weekday_text_list = r.get("weekday_text") if isinstance(r.get("weekday_text"), list) else []
        weekday_text_str  = "\n".join(weekday_text_list) if weekday_text_list else None

        # 計算「當天」的分鐘區間（優先 periods，沒 periods 再用文字版解析）
        hours_today_ranges: Optional[List[Tuple[int, int]]] = None
        if weekday_py is not None:
            if periods:
                hours_today_ranges = _periods_to_ranges(periods, weekday_py)
            else:
                # fallback：用 weekday_text（list→str）或 open_text（舊欄位）做解析
                src_text = weekday_text_str or r.get("open_text")
                if src_text:
                    hours_today_ranges = _parse_open_text_ranges(src_text)

        hours_today_text = _ranges_to_text(hours_today_ranges) if hours_today_ranges else None

        # 營業時段過濾/標記
        is_open_match = None
        if (weekday_py is not None) and (start_min is not None) and (end_min is not None):
            ranges_for_filter = hours_today_ranges
            if ranges_for_filter:
                is_open_match = _overlap(start_min, end_min, ranges_for_filter)
                if require_open and not is_open_match:
                    continue  # 嚴格模式：不重疊直接丟掉
            else:
                if require_open:
                    continue  # 沒營業資訊且嚴格要求 → 丟掉
                is_open_match = None  # 未知

        seen.add(pid)
        cleaned.append({
            "place_id": pid,
            "name": name,
            "rating": r.get("rating"),
            "reviews": r.get("reviews"),
            "address": r.get("address"),
            "lat": lat,
            "lng": lng,
            "map_url": r.get("map_url"),
            # ---- 營業資訊輸出（新增/統一）----
            "opening_hours_periods": periods,        # 結構化（給計算用）
            "weekday_text": weekday_text_list,       # 一週文字（list）
            "weekday_text_str": weekday_text_str,    # 一週文字（字串，'\n'）
            "hours_today_ranges": hours_today_ranges, # 當天分鐘區間 list[(s,e)]
            "hours_today_text": hours_today_text,    # 當天字串 "HH:MM–HH:MM, ..."
            "open_now": r.get("open_now"),
            "utc_offset_minutes": r.get("utc_offset_minutes"),
            # 相容舊流程：open_text 一律給字串版（UI/舊 parser 直接用）
            "open_text": weekday_text_str,
            # 其他
            "types": r.get("types", []),
            "source": r.get("source", "gm_search"),
            "_is_open_match": is_open_match,
        })

        if len(cleaned) >= max_results:
            break

    return cleaned