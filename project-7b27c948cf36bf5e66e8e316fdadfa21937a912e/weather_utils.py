# ✅ weather_utils.py
import requests

OPENWEATHER_API_KEY = "23ee3453126d5c9f9e30112b66b3e5db"  # 建議用 dotenv 管理

# 擴充版：台灣常見縣市中文 ➜ OpenWeather 對應城市名
CITY_TRANSLATIONS = {
    "台北": "Taipei",
    "新北": "New Taipei",
    "桃園": "Taoyuan",
    "台中": "Taichung",
    "台南": "Tainan",
    "高雄": "Kaohsiung",
    "基隆": "Keelung",
    "新竹": "Hsinchu",
    "嘉義": "Chiayi",

    "新竹縣": "Zhubei",
    "苗栗": "Miaoli",
    "苗栗縣": "Miaoli",
    "彰化": "Changhua",
    "彰化縣": "Changhua",
    "南投": "Nantou",
    "南投縣": "Nantou",
    "雲林": "Douliu",
    "雲林縣": "Douliu",
    "嘉義縣": "Chiayi",
    "屏東": "Pingtung",
    "屏東縣": "Pingtung",
    "宜蘭": "Yilan",
    "宜蘭縣": "Yilan",
    "花蓮": "Hualien",
    "花蓮縣": "Hualien",
    "台東": "Taitung",
    "台東縣": "Taitung",
    "澎湖": "Magong",
    "澎湖縣": "Magong",
    "金門": "Jincheng",
    "金門縣": "Jincheng",
    "連江": "Nangan",
    "連江縣": "Nangan"
}

def get_weather(city: str) -> str:
    original_city = city.strip()
    city_translated = CITY_TRANSLATIONS.get(original_city, original_city)
    city_query = f"{city_translated},TW"

    url = "https://api.openweathermap.org/data/2.5/weather"
    params = {
        "q": city_query,
        "appid": OPENWEATHER_API_KEY,
        "lang": "zh_tw",
        "units": "metric"
    }

    res = requests.get(url, params=params).json()

    if res.get("cod") != 200:
        return f"⚠️ 找不到「{original_city}」的天氣資訊（查詢關鍵字：{city_query}）。請確認地名或嘗試主要城市名稱。"

    weather = res["weather"][0]["description"]
    temp = res["main"]["temp"]
    feels_like = res["main"]["feels_like"]
    humidity = res["main"]["humidity"]
    wind_speed = res["wind"]["speed"]

    return f"""
🌤️ {original_city} 的天氣資訊：
- 天氣狀況：{weather}
- 氣溫：{temp}°C（體感：{feels_like}°C）
- 濕度：{humidity}%
- 風速：{wind_speed} m/s
"""