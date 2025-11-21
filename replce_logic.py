
import json
import os
from langchain_google_genai import ChatGoogleGenerativeAI

from config import MEMORY_FOLDER
from utils import display_trip_by_trip_id, extract_json, get_user_chain


