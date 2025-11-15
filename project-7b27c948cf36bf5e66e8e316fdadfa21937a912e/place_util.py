# place_utils.py
import requests
import json
import os

# 建議從 .env 或 config 檔讀取 API Key，避免硬編碼
# GOOGLE_PLACES_API_KEY = os.environ.get("GOOGLE_PLACES_API_KEY", "YOUR_DEFAULT_API_KEY")
GOOGLE_PLACES_API_KEY = "AIzaSyChTAIrJyT-0MMGliX23Kt0G3-_C5aSTyg"  # 您的 API Key

# 台灣中心點座標 (大約在台北市中心附近，可以覆蓋大部分主要城市)
TAIWAN_CENTER_LAT = 25.0330
TAIWAN_CENTER_LNG = 121.5654
# 搜尋半徑 (公尺)，約 200 公里，足以覆蓋台灣主要島嶼
TAIWAN_SEARCH_RADIUS = 50000 # 💡 修改：調整為最大允許值 50 公里

def get_opening_hours(place_name: str) -> str:
    """
    使用 Google Places API 獲取地點的營業時間。
    先用文字搜尋取得 place_id，再用 place_id 獲取詳細資訊。
    """
    # 1. 用文字搜尋取得 place_id
    search_url = "https://maps.googleapis.com/maps/api/place/textsearch/json"
    search_params = {
        "query": place_name,
        "key": GOOGLE_PLACES_API_KEY,
        "language": "zh-TW",
        "region": "tw", # 仍然保留 region 參數作為偏好
        "location": f"{TAIWAN_CENTER_LAT},{TAIWAN_CENTER_LNG}", # 限制搜尋中心
        "radius": TAIWAN_SEARCH_RADIUS # 限制搜尋半徑
    }

    try:
        response = requests.get(search_url, params=search_params)
        response.raise_for_status() # 如果狀態碼是 4xx 或 5xx，會拋出 HTTPError

        res = response.json() # 嘗試解析 JSON

        if not res.get("results"):
            print(f"🔍 搜尋 '{place_name}' 無結果或 API 狀態非 OK: {res.get('status', '未知狀態')}")
            return f"找不到『{place_name}』的資訊或沒有相關結果。"

        if not res["results"]:
            print(f"🔍 搜尋 '{place_name}' 返回空結果列表。")
            return f"找不到『{place_name}』的資訊。"
            
        place_id = res["results"][0]["place_id"]
        print(f"✅ 成功獲取 '{place_name}' 的 place_id: {place_id}")

    except requests.exceptions.RequestException as e:
        print(f"❌ 呼叫 Google Places Text Search API 時發生網路錯誤：{e}")
        return f"查詢『{place_name}』時發生網路問題。"
    except json.JSONDecodeError:
        print(f"❌ Google Places Text Search API 回應不是有效的 JSON：{response.text[:100]}...")
        return f"查詢『{place_name}』時 API 回應格式錯誤。"
    except IndexError:
        print(f"❌ Google Places Text Search API 回應中 'results' 列表為空或格式不符：{res}")
        return f"找不到『{place_name}』的資訊。"
    except Exception as e:
        print(f"❌ 獲取 '{place_name}' 的 place_id 時發生未知錯誤：{e}")
        return f"查詢『{place_name}』時發生錯誤。"

    # 2. 用 place_id 拿營業時間
    detail_url = "https://maps.googleapis.com/maps/api/place/details/json"
    detail_params = {
        "place_id": place_id,
        "fields": "name,opening_hours", # 只請求需要的欄位
        "key": GOOGLE_PLACES_API_KEY,
        "language": "zh-TW"
    }

    try:
        response = requests.get(detail_url, params=detail_params)
        response.raise_for_status() # 如果狀態碼是 4xx 或 5xx，會拋出 HTTPError
        
        detail_res = response.json()
        result = detail_res.get("result", {})

        if not result:
            print(f"🔍 獲取 place_id '{place_id}' 詳細資訊無結果或 API 狀態非 OK: {detail_res.get('status', '未知狀態')}")
            return f"找不到『{place_name}』的詳細營業時間資訊。"

        if "opening_hours" in result and result["opening_hours"].get("weekday_text"):
            weekday_text = "\n".join(result["opening_hours"]["weekday_text"])
            print(f"✅ 成功獲取 '{result.get('name', place_name)}' 的營業時間。")
            return f"{result.get('name', place_name)} 的營業時間如下：\n{weekday_text}"
        else:
            print(f"🔍 '{result.get('name', place_name)}' 沒有提供營業時間資料。")
            return f"{result.get('name', place_name)} 沒有提供營業時間資料"

    except requests.exceptions.RequestException as e:
        print(f"❌ 呼叫 Google Places Details API 時發生網路錯誤：{e}")
        return f"獲取『{place_name}』詳細資訊時發生網路問題。"
    except json.JSONDecodeError:
        print(f"❌ Google Places Details API 回應不是有效的 JSON：{response.text[:100]}...")
        return f"獲取『{place_name}』詳細資訊時 API 回應格式錯誤。"
    except Exception as e:
        print(f"❌ 獲取 '{place_name}' 營業時間時發生未知錯誤：{e}")
        return f"獲取『{place_name}』營業時間時發生錯誤。"

def search_places_by_tag(query: str, location="台北") -> list:
    """
    使用 Google Places API (Text Search) 搜尋地點。
    返回一個地點列表，每個地點包含名稱、地址等基本資訊。
    """
    url = "https://maps.googleapis.com/maps/api/place/textsearch/json"
    params = {
        "query": query,
        "key": GOOGLE_PLACES_API_KEY,
        "language": "zh-TW",
        "region": "tw", # 仍然保留 region 參數作為偏好
        "location": f"{TAIWAN_CENTER_LAT},{TAIWAN_CENTER_LNG}", # 💡 新增：限制搜尋中心
        "radius": TAIWAN_SEARCH_RADIUS # 💡 新增：限制搜尋半徑
    }

    try:
        response = requests.get(url, params=params)
        response.raise_for_status() # 如果狀態碼是 4xx 或 5xx，會拋出 HTTPError

        result = response.json() # 嘗試解析 JSON

        if result.get("status") == "OK":
            print(f"✅ 成功從 Google 地圖搜尋到 {len(result.get('results', []))} 個結果，查詢：'{query}'")
            return result.get("results", [])
        elif result.get("status") == "ZERO_RESULTS":
            print(f"🔍 Google 地圖搜尋結果：'{query}' 無結果。")
            return []
        else:
            # 處理其他 API 狀態，例如 "OVER_QUERY_LIMIT", "REQUEST_DENIED" 等
            error_message = result.get("error_message", "未知錯誤")
            print(f"❌ Google 地圖搜尋 API 錯誤：狀態碼 {result.get('status')} - {error_message}")
            return []

    except requests.exceptions.RequestException as e:
        # 捕捉所有 requests 相關的錯誤 (連線失敗, 超時, HTTP 錯誤等)
        print(f"❌ 呼叫 Google Maps Text Search API 時發生網路錯誤：{e}")
        return []
    except json.JSONDecodeError:
        # 捕捉 JSON 解析錯誤
        print(f"❌ Google Maps Text Search API 回應不是有效的 JSON：{response.text[:100]}...")
        return []
    except Exception as e:
        # 捕捉其他未預期的錯誤
        print(f"❌ Google Maps 查詢時發生未知錯誤：{e}")
        return []
