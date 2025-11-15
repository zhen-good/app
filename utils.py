# utils.py
import json
import re
import traceback

from bson import ObjectId

def extract_json(text: str):
    """
    從文字中抽取第一個合法 JSON 區塊，並轉成 Python 物件 (dict 或 list) 回傳。
    """
    cleaned_text = text.replace('\xa0', ' ').strip()
    
    pattern_code_block = re.compile(r'```json\s*(.*?)\s*```', re.DOTALL)
    match = pattern_code_block.search(cleaned_text)
    
    if match:
        json_str = match.group(1).strip()
        try:
            parsed_json = json.loads(json_str)
            print(f"✅ 從 ```json 區塊中成功解析：{json_str[:50]}...")
            return parsed_json
        except json.JSONDecodeError as e:
            print(f"❌ 從 ```json 區塊中解析 JSON 失敗：{e}")
    
    try:
        parsed_json = json.loads(cleaned_text)
        print("✅ 直接解析為 JSON 成功！")
        return parsed_json
    except json.JSONDecodeError:
        print("⚠️ json.loads 直接解析失敗，嘗試尋找最外層的 {} 或 []...")
    
    general_json_matches = re.findall(r'(\[.*\]|\{.*\})', cleaned_text, re.DOTALL)
    if general_json_matches:
        for json_str in general_json_matches:
            try:
                parsed_json = json.loads(json_str)
                print(f"✅ 從一般文本中成功解析：{json_str[:50]}...")
                return parsed_json
            except Exception as e:
                print(f"❌ 解析一般文本區塊失敗：{e}，內容開頭：{json_str[:50]}...")
                continue

    print("❌ 所有 JSON 提取嘗試均失敗。")
    return None


def object_id_to_string(obj_id: ObjectId) -> str:
    return str(obj_id)