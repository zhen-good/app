#這裡有點複雜，主要是在抓時間節點，找到該行程的上下景點，以搜尋方圓內的景點

def _nodes_of_day(trip_doc, day):
    nodes = [n for n in (trip_doc.get("nodes") or []) if n.get("day") == day]
    # 依開始時間排序，避免 near_hint=prev/next 取錯
    def _to_min(t): 
        try:
            h, m = map(int, (t or "00:00").split(":")); 
            return h*60 + m
        except: 
            return 0
    nodes.sort(key=lambda n: _to_min(n.get("start")))
    return nodes

def _anchor_coords(trip_doc, day, slot, near_hint):
    nodes = _nodes_of_day(trip_doc, day)
    if not nodes: 
        return None

    # 找目標 slot 的索引
    idx = None
    for i, n in enumerate(nodes):
        if n.get("slot") == slot:
            idx = i; break

    # 根據 near_hint 取得 anchor node
    target = None
    if near_hint == "slot_node" and idx is not None:
        target = nodes[idx]
    elif near_hint == "prev_node" and idx not in (None, 0):
        target = nodes[idx-1]
    elif near_hint == "next_node" and idx is not None and idx+1 < len(nodes):
        target = nodes[idx+1]

    # 取該 node 第一個 place 的座標
    p = (target or {}).get("places") or []
    if p and p[0].get("lat") is not None and p[0].get("lng") is not None:
        return (p[0]["lat"], p[0]["lng"])
    return None
