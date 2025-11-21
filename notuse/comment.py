# from langchain_google_genai import ChatGoogleGenerativeAI
# from utils import extract_json

# COMMENT_TAGGING_PROMPT = """
# 請閱讀以下評論，找出評論中提到的喜好類型標籤，例如「自然」、「美食」、「購物」、「歷史」、「藝術」、「夜生活」、「親子」、「戶外」、「休閒」、「文化」等常見旅遊偏好類型。請以 JSON 格式回答：

# 評論：
# {input}

# 請輸出格式如下：
# {{"tags": ["自然", "美食"]}}
# """


# def extract_tags_from_comment(comment: str) -> list:
#     prompt = COMMENT_TAGGING_PROMPT.format(input=comment)
#     llm = ChatGoogleGenerativeAI(model="gemini-1.5-flash")
#     response = llm.invoke(prompt).content
#     print("🔍 分析評論產生的回應：", response)
#     result = extract_json(response)
#     tags = result.get("tags", []) if isinstance(result, dict) else []
#     print("🏷️ 擷取到的標籤：", tags)
#     return tags


# def generate_search_query(tag: str, location: str = "台北") -> str:
#     return f"{location} {tag} 景點"


####目前沒有用到