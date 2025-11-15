def convert_trip_to_prompt(trip: dict) -> str:
    lines = []
    schedules = trip.get("schedules", [])
    if not schedules:
        return "目前尚未規劃行程"

    for s in schedules:
        lines.append(f"Day {s.get('day')}：{s.get('activity')}（{s.get('startTime')} ~ {s.get('endTime')}），交通方式：{s.get('transportation')}，備註：{s.get('note', '')}")
    
    return "\n".join(lines)
