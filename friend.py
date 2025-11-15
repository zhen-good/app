from datetime import datetime
from bson import ObjectId
from flask import Blueprint, jsonify, request
from mongodb_utils import save_recommendation, trips_collection, user_collection


# 建立 Blueprint
friends_bp = Blueprint('friends', __name__)

@friends_bp.route('/friend-requests', methods=['POST'])
def send_friend_request():

    data = request.json
    sender_id = data.get('sender_id')
    receiver_id = data.get('receiver_id')

    if not sender_id or not receiver_id:
        return jsonify({'error': '請提供 sender_id 和 receiver_id'}), 400

    if sender_id == receiver_id:
        return jsonify({'error': '不能加自己為好友'}), 400
    try:
        sender_obj_id = ObjectId(sender_id)
        receiver_obj_id = ObjectId(receiver_id)
    except:
        return jsonify({'error': '無效的使用者 ID'}), 400
    sender = user_collection.find_one({'_id': sender_obj_id})
    receiver = user_collection.find_one({'_id': receiver_obj_id})
    if not sender or not receiver:
        return jsonify({'error': '使用者不存在'}), 404

    # 檢查是否已經是好友
    if receiver_obj_id in sender.get('friends', []):
        return jsonify({'error': '你們已經是好友了'}), 400

    # 檢查是否已發送邀請
    receiver_pending = receiver.get('pendingRequests', [])
    for req in receiver_pending:
        if req.get('fromUserId') == sender_obj_id:
            return jsonify({'error': '已經發送過邀請，請等待對方回應'}), 400

    # 建立邀請物件
    request_obj = {
        'fromUserId': sender_obj_id,
        'timestamp': datetime.utcnow()
    }

    # 更新接收者的 pendingRequests
    user_collection.update_one(
        {'_id': receiver_obj_id},
        {'$push': {'pendingRequests': request_obj}}
    )

    # 更新發送者的 sentRequests
    user_collection.update_one(
        {'_id': sender_obj_id},
        {'$push': {'sentRequests': receiver_obj_id}}
    )
    return jsonify({
        'message': '好友邀請已發送',
        'sender': sender['username'],
        'receiver': receiver['username']
    }), 201


# 4. 查看收到的好友邀請

@friends_bp.route('/friend-requests/received/<user_id>', methods=['GET'])
def get_received_requests(user_id):
    try:
        user_obj_id = ObjectId(user_id)
    except:
        return jsonify({'error': '無效的使用者 ID'}), 400
    user = user_collection.find_one({'_id': user_obj_id})
    if not user:
        return jsonify({'error': '使用者不存在'}), 404

    pending_requests = user.get('pendingRequests', [])

    # 取得發送者的詳細資訊
    requests_with_info = []

    for req in pending_requests:
        sender_id = req.get('fromUserId')
        sender = user_collection.find_one({'_id': sender_id})
        if sender:
            requests_with_info.append({
                'sender_id': str(sender_id),
                'sender_username': sender.get('username'),
                'sender_email': sender.get('email'),
                'timestamp': req.get('timestamp').isoformat() if req.get('timestamp') else None
            })

    return jsonify({
        'received_requests': requests_with_info,
        'count': len(requests_with_info)
    }), 200

# 5. 查看發送的好友邀請

@friends_bp.route('/friend-requests/sent/<user_id>', methods=['GET'])
def get_sent_requests(user_id):
    try:
        user_obj_id = ObjectId(user_id)
    except:
        return jsonify({'error': '無效的使用者 ID'}), 400
    user = user_collection.find_one({'_id': user_obj_id})
    if not user:
        return jsonify({'error': '使用者不存在'}), 404
    sent_request_ids = user.get('sentRequests', [])

    # 取得接收者的詳細資訊

    requests_with_info = []
    for receiver_id in sent_request_ids:
        receiver = user_collection.find_one({'_id': receiver_id})
        if receiver:
            requests_with_info.append({

                'receiver_id': str(receiver_id),
                'receiver_username': receiver.get('username'),
                'receiver_email': receiver.get('email')
            })

    return jsonify({

        'sent_requests': requests_with_info,
        'count': len(requests_with_info)
    }), 200

# 6. 接受或拒絕好友邀請
@friends_bp.route('/friend-requests/respond', methods=['PUT'])
def respond_to_request():
    data = request.json
    user_id = data.get('user_id')  # 接收邀請的人
    sender_id = data.get('sender_id')  # 發送邀請的人
    action = data.get('action')  # 'accept' 或 'reject'
    if not user_id or not sender_id or action not in ['accept', 'reject']:
        return jsonify({'error': '請提供正確的參數'}), 400
    try:
        user_obj_id = ObjectId(user_id)
        sender_obj_id = ObjectId(sender_id)
    except:
        return jsonify({'error': '無效的使用者 ID'}), 400
    user = user_collection.find_one({'_id': user_obj_id})
    sender = user_collection.find_one({'_id': sender_obj_id})
    if not user or not sender:
        return jsonify({'error': '使用者不存在'}), 404
    # 從 pendingRequests 中移除這個邀請
    user_collection.update_one(
        {'_id': user_obj_id},
        {'$pull': {'pendingRequests': {'fromUserId': sender_obj_id}}}
    )
    # 從發送者的 sentRequests 中移除
    user_collection.update_one(
        {'_id': sender_obj_id},
        {'$pull': {'sentRequests': user_obj_id}}
    )
    if action == 'accept':
        # 雙方互相加為好友
        user_collection.update_one(
            {'_id': user_obj_id},
            {'$addToSet': {'friends': sender_obj_id}}
        )
        user_collection.update_one(
            {'_id': sender_obj_id},
            {'$addToSet': {'friends': user_obj_id}}
        )
        return jsonify({
            'message': '已接受好友邀請',
            'status': 'accepted'
        }), 200
    else:
        return jsonify({
            'message': '已拒絕好友邀請',
            'status': 'rejected'
        }), 200

# 7. 查看好友列表
@friends_bp.route('/friends/<user_id>', methods=['GET'])
def get_friends(user_id):
    try:
        user_obj_id = ObjectId(user_id)
    except:
        return jsonify({'error': '無效的使用者 ID'}), 400

    user = user_collection.find_one({'_id': user_obj_id})
    if not user:
        return jsonify({'error': '使用者不存在'}), 404
    friend_ids = user.get('friends', [])
    friends_info = []
    for friend_id in friend_ids:
        friend = user_collection.find_one({'_id': friend_id})
        if friend:
            friends_info.append({
                'id': str(friend['_id']),
                'username': friend.get('username'),
                'email': friend.get('email')
            })

    return jsonify(friends_info), 200

# 8. 刪除好友
@friends_bp.route('/friends/<user_id>/<friend_id>', methods=['DELETE'])
def remove_friend(user_id, friend_id):
    try:
        user_obj_id = ObjectId(user_id)
        friend_obj_id = ObjectId(friend_id)
    except:
        return jsonify({'error': '無效的使用者 ID'}), 400

    # 雙方互相從好友列表中移除
    result1 = user_collection.update_one(
        {'_id': user_obj_id},
        {'$pull': {'friends': friend_obj_id}}

    )
    result2 = user_collection.update_one(
        {'_id': friend_obj_id},
        {'$pull': {'friends': user_obj_id}}
    )

    if result1.modified_count == 0 and result2.modified_count == 0:
        return jsonify({'error': '找不到此好友關係'}), 404
    return jsonify({'message': '已刪除好友'}), 200

    

# 9. 搜尋使用者 (根據 username 或 email)
@friends_bp.route('/users/search', methods=['GET'])
def search_users():
    # 1. 取得查詢參數 keyword
    # 這裡的 'keyword' 參數名稱必須與前端 Kotlin 的 @Query("keyword") 匹配
    keyword = request.args.get('keyword') 

    if not keyword or len(keyword.strip()) == 0:
        # 如果沒有提供關鍵字，回傳 400 錯誤
        # 也可以選擇回傳一個空列表 []
        return jsonify({'error': '請提供搜尋關鍵字'}), 400
    
    # 清理關鍵字兩端的空白
    clean_keyword = keyword.strip()
    
    # 2. 執行資料庫模糊查詢
    # '$options': 'i' 確保搜尋不區分大小寫 (case-insensitive)
    # 使用 $or 來同時匹配 username 或 email
    search_query = {
        '$or': [
            {'username': {'$regex': clean_keyword, '$options': 'i'}},
            {'email': {'$regex': clean_keyword, '$options': 'i'}}
        ]
    }
    
    # 執行查詢，並排除密碼等敏感欄位 (雖然您目前沒有密碼欄位，但這是好習慣)
    search_results_cursor = user_collection.find(
        search_query, 
        {'_id': 1, 'username': 1, 'email': 1} # 只投影需要的欄位
    )
    
    users_list = []
    for user in search_results_cursor:
        users_list.append({
            'id': str(user['_id']),
            'username': user.get('username'),
            'email': user.get('email')
        })
        
    # 3. 回傳結果
    # 回傳一個 JSON 數組 []，完美匹配您前端的 List<User> 模型
    return jsonify(users_list), 200