from place_gmaps import search_candidates

alts = search_candidates("咖啡廳", near=(22.6269, 120.2868), radius_m=2000, max_results=5)
for a in alts:
    print(a["name"], a["rating"], a["map_url"])
