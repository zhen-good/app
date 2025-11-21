import os
from datetime import date
from dotenv import load_dotenv


load_dotenv()

# ============================= #
# 這個檔案是用來放一些初始化的東西 #
# ============================= #

# ========== 环境配置 ==========
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
GOOGLE_API_KEY = os.getenv("GOOGLE_API_KEY")

# ========== 文件夹配置 ==========
MEMORY_FOLDER = "memories"
os.makedirs(MEMORY_FOLDER, exist_ok=True)

# ========== 时间配置 ==========
TODAY = date.today().strftime("%Y年%m月%d日")

# ========== 全局变量 ==========
pending_add_location = {}
user_chains = {}      # 用户对话链缓存
last_analysis = {}    # 最后分析结果缓存

# =============== 意圖偵測 Prompt ===============
INTENT_PROMPT = """
你是一位專門處理旅遊行程的助理。

請根據使用者的發言判斷：使用者是否**表達了明確想新增一個景點到旅遊行程中**的意圖？

**請務必只回傳一個符合 JSON Schema 的 JSON 程式碼區塊，不要包含任何額外的文字或說明。**
JSON 必須包含在 ````json` 和 ```` 之間。

JSON 格式如下：
```json
{{
    "add_location": true,
    "place_name": ""
}}
使用者說：
「{text}」
"""