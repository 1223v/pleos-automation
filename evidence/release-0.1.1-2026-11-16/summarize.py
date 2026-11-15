"""Summarize rounds/R??.json into rounds/summary.md (T36-style table)."""
import glob, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
rows = []
for path in sorted(glob.glob(os.path.join(HERE, "rounds", "R??.json"))):
    r = json.load(open(path, encoding="utf-8"))
    v = r["verdict_inputs"]
    b, a = r["before"], r["after"]
    cause = next((h for h in r["history"] if h.startswith("시작 원인")), "")
    cond = next((h for h in r["history"] if h.startswith("당시 시작 조건")), "")
    vehicle_ok = (v["hvac"] == v["brightness"] == v["color"] == "VERIFIED"
                  and a["driver_temp"] == 23.0 and a["brightness"] == 30 and (a["color"] or "").lower() == "#4b85db"
                  and b["driver_temp"] != 23.0 and b["brightness"] != 30 and (b["color"] or "").lower() != "#4b85db")
    audio_ok = v["audio"] == "VERIFIED" and any("state:started" in x for x in r.get("audio_players_running", [])) and not r.get("audio_players_after_stop")
    timer_ok = v["timer"] == "PENDING" and any("남은" in x for x in r.get("running_banners", [])) and "CANCELLED" in (v["run"] or "")
    seat_skip_ok = v["seat_fore_aft"] == v["seat_backrest"] == "SKIPPED" and v["seat_positions_unchanged"]
    start_ok = ("직접 실행" in cause) if r["mode"] == "MANUAL" else ("자동 실행" in cause and "기어 = P" in cond)
    executable_pass = vehicle_ok and audio_ok and timer_ok and seat_skip_ok and start_ok and bool(r.get("saved"))
    rows.append((r["round"], r["mode"], cause.replace("시작 원인: ", ""), r.get("start_at"),
                 f'{b["driver_temp"]}→{a["driver_temp"]}°C / {b["brightness"]}→{a["brightness"]}% / {b["color"]}→{a["color"]}',
                 f'{v["hvac"]}/{v["brightness"]}/{v["color"]}', f'{v["seat_fore_aft"]}/{v["seat_backrest"]}',
                 f'{v["audio"]}, 타이머 {v["timer"]} → {v["run"].split(" · ")[1] if v["run"] else "?"}',
                 "PASS" if executable_pass else "FAIL"))

out = ["| 회차 | 시작 방식 | 기록된 시작 원인 | 시작 시각 | Console 전→후 (공조/밝기/색상) | 앱 판정 공조/밝기/색상 | 시트 앞뒤/등받이 | 음원·타이머→종료 | 실행 가능 항목 |",
       "|---|---|---|---|---|---|---|---|---|"]
out += ["| " + " | ".join(map(str, row)) + " |" for row in rows]
passed = sum(1 for r in rows if r[-1] == "PASS")
out.append("")
out.append(f"실행 가능 항목 통과: {passed}/{len(rows)}")
open(os.path.join(HERE, "rounds", "summary.md"), "w", encoding="utf-8").write("\n".join(out) + "\n")
sys.stdout.reconfigure(encoding="utf-8")
print("\n".join(out))
