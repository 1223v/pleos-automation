"""Release 0.1.1 repeated-flow driver (test harness, not product code).

Drives the installed release APK on emulator-5580 through the UI (adb input + uiautomator)
and drives/observes the vehicle through the Pleos Virtual Console HTTP API on localhost:8088
(forwarded from the AVD). The Console backend only activates while a browser session sends
heartbeats; this harness sends the same heartbeat the Console page sends (POST
/browser/session every second) so a hidden browser tab cannot stall vehicle writes.

Per round: reset Console baseline -> load "휴식 예시" -> (even: set CONDITION_ENTER gear=P,
simulate) -> named save -> start (odd: 지금 실행, even: activate + gear D->P) -> confirm the
seat-exclusion dialog -> observe Console state -> stop -> read run history.

Usage: python -I run_rounds.py <first> <last>
"""
import json, os, re, subprocess, sys, threading, time, uuid, urllib.parse, urllib.request
import xml.etree.ElementTree as ET

ROOT = r"C:\pleos-automation"
ADB = os.path.join(ROOT, r".tools\android-sdk\platform-tools\adb.exe")
SERIAL = "emulator-5580"
CONSOLE = "http://127.0.0.1:8088"
OUT = os.path.join(ROOT, r"evidence\release-0.1.1-2026-11-16\rounds")
PKG = "com.pleos.routine"
TARGET = {"temp": 23.0, "brightness": 30, "color": "#4b85db"}
BASELINES = [(20.0, 0, "#00e5ff"), (21.0, 70, "#aa00ff"), (18.5, 50, "#ffffff"), (25.0, 10, "#2979ff"), (16.0, 100, "#00e5ff")]


def log(*a):
    print(time.strftime("%H:%M:%S"), *a, flush=True)


def adb(*args, binary=False, timeout=60):
    r = subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True, timeout=timeout)
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")


# ---------------------------------------------------------------- Console
def http(method, path, **params):
    url = CONSOLE + path + ("?" + urllib.parse.urlencode(params) if params else "")
    req = urllib.request.Request(url, method=method, data=b"" if method in ("PUT", "POST") else None)
    with urllib.request.urlopen(req, timeout=10) as r:
        body = r.read()
    return json.loads(body) if body[:1] in (b"{", b"[") else body


def state():
    s = http("GET", "/vehicle/state")
    return {"gear": (s.get("gear") or {}).get("actual", {}).get("value"),
            "driver_temp": (s.get("hvac") or {}).get("driver", {}).get("temp_c"),
            "passenger_temp": (s.get("hvac") or {}).get("passenger", {}).get("temp_c"),
            "brightness": (s.get("body") or {}).get("ambient_light", {}).get("brightness_pct"),
            "color": (s.get("body") or {}).get("ambient_light", {}).get("color"),
            "seat_slide_driver": (s.get("seat") or {}).get("positions", {}).get("slide", {}).get("driver"),
            "seat_recline_driver": (s.get("seat") or {}).get("positions", {}).get("recline", {}).get("driver"),
            "browser": s.get("browser"), "at": time.strftime("%Y-%m-%dT%H:%M:%S")}


_client = str(uuid.uuid4())


def heartbeat():
    while True:
        try:
            http("POST", "/browser/session", client=_client)
        except Exception:
            pass
        time.sleep(1)


def gear(value):
    http("PUT", "/vehicle/gear", gear=value)


# ---------------------------------------------------------------- UI
def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    raw = adb("exec-out", "cat", "/sdcard/ui.xml")
    raw = raw[raw.find("<?xml"):] if "<?xml" in raw else raw
    try:
        root = ET.fromstring(raw)
    except ET.ParseError:
        return []
    out = []
    for n in root.iter("node"):
        t = n.get("text") or n.get("content-desc") or ""
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
        if t and m:
            x1, y1, x2, y2 = map(int, m.groups())
            out.append((t, (x1 + x2) // 2, (y1 + y2) // 2))
    return out


def texts():
    return [t for t, _, _ in nodes()]


def find(text, exact=True, index=0, wait=8.0):
    end = time.time() + wait
    while True:
        hits = [n for n in nodes() if (n[0] == text if exact else text in n[0])]
        if len(hits) > index:
            return hits[index]
        if time.time() > end:
            return None
        time.sleep(0.7)


def tap(text, exact=True, index=0, wait=8.0, settle=1.2):
    n = find(text, exact, index, wait)
    if not n:
        raise RuntimeError(f"not found: {text}")
    adb("shell", "input", "tap", str(n[1]), str(n[2]))
    time.sleep(settle)
    return n


def shot(name):
    with open(os.path.join(OUT, name), "wb") as f:
        f.write(adb("exec-out", "screencap", "-p", binary=True))


def foreground():
    top = adb("shell", "dumpsys", "activity", "activities")
    m = re.search(r"topResumedActivity=ActivityRecord\{\S+ \S+ (\S+)", top)
    return m.group(1) if m else ""


def ensure_app():
    if not foreground().startswith(PKG):
        adb("shell", "am", "start", "--user", "10", "-n", f"{PKG}/.MainActivity")
        time.sleep(2.5)


APP_UID = "1010178"  # user 10, appId 10178 on this AVD


def audio_players():
    """AudioPlaybackConfiguration lines owned by the app (system view, independent of the app UI)."""
    return [l.strip() for l in adb("shell", "dumpsys", "audio").splitlines() if f"u/pid:{APP_UID}" in l][:4]


def rename(name):
    field = find("잠깐 쉬어가기", exact=True)
    adb("shell", "input", "tap", str(field[1]), str(field[2]))
    time.sleep(0.5)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    adb("shell", "input", "keyevent", *(["KEYCODE_DEL"] * 14))
    adb("shell", "input", "text", name.replace(" ", "%s"))
    time.sleep(0.8)


def history_lines():
    """Expand the newest record and collect its step lines, scrolling until nothing new appears."""
    tap("실행 이력")
    first = find("실제 실행 ·", exact=False)
    adb("shell", "input", "tap", "2474", str(first[2] - 17))
    time.sleep(1.5)
    seen, order = set(), []
    for _ in range(8):
        new = 0
        for t in texts():
            if t not in seen:
                seen.add(t); order.append(t); new += 1
        if any(re.match(r"#\d+ · .*30분 휴식", t) for t in order) and any("AI 실행 설명" in t for t in order):
            break
        adb("shell", "input", "swipe", "1700", "1150", "1700", "550", "500")
        time.sleep(1.0)
        if new == 0:
            break
    return order


# ---------------------------------------------------------------- round
def run_round(n):
    mode = "MANUAL" if n % 2 == 1 else "CONDITION_ENTER"
    tag = f"R{n:02d}"
    rec = {"round": tag, "mode": mode, "notes": []}
    adb("logcat", "-c")
    ensure_app()
    # 1. baseline
    t, b, c = BASELINES[(n - 1) % len(BASELINES)]
    gear("Park")
    http("PUT", "/vehicle/hvac", driver_temp=t)
    http("PUT", "/vehicle/body", ambient_brightness=b)
    http("PUT", "/vehicle/body", ambient_color=c)
    time.sleep(2.5)
    rec["before"] = state()
    log(tag, mode, "before", rec["before"])
    # 2. load template and review
    tap("루틴 만들기"); tap("01 생성"); tap("휴식 예시", settle=2)
    if not find("잠깐 쉬어가기", wait=5):
        raise RuntimeError("rest template not loaded")
    rec["review_issues"] = [x for x in texts() if x.startswith("확인할 항목")]
    if mode == "CONDITION_ENTER":
        tap("조건 바꾸기")
        tap("시작 · 직접 실행")
        tap("상태 조건이 새로 충족될 때")
        tap("비교할 상태 ·", exact=False)
        tap("기어")
        rec["trigger_editor"] = [x for x in texts() if " · " in x][:4]
        tap("시작 조건 적용", settle=2)
        tap("04 테스트")
        tap("이 상황으로 테스트", settle=3)
        rec["simulation"] = [x for x in texts() if re.search(r"COMPLETED|SIMULATED|BLOCKED|FAILED|시뮬레이션", x)][:6]
    # 3. named save
    tap("검토한 루틴 저장")
    rename(f"Rest {tag}")
    tap("저장", settle=2)
    saved = find("비활성으로 저장했습니다", exact=False, wait=5)
    rec["saved"] = saved[0] if saved else None
    # 4. start
    tap("내 루틴")
    if mode == "MANUAL":
        tap("지금 실행", index=0, settle=0.5)
        rec["start_at"] = time.strftime("%H:%M:%S")
    else:
        tap("활성화", index=0)
        tap("확인", settle=3.0)  # "자동 실행 활성화" confirmation
        rec["activated"] = [x for x in texts() if x in ("끄기", "활성화")][:2]
        gear("Drive"); rec["gear_drive_at"] = time.strftime("%H:%M:%S")
        time.sleep(5)
        rec["fg_in_drive"] = foreground()
        gear("Park"); rec["start_at"] = rec["gear_park_at"] = time.strftime("%H:%M:%S")
        time.sleep(1.0)
        rec["fg_after_park"] = foreground()
        ensure_app()
    # 5. seat-exclusion consent
    dlg = find("시트 단계 제외 실행 확인", wait=35)
    if not dlg:
        rec["notes"].append("seat-exclusion dialog did not appear")
        rec["screen"] = texts()[:40]
        shot(f"{tag}-no-dialog.png")
        raise RuntimeError("no consent dialog")
    rec["consent_text"] = [x for x in texts() if "시트" in x or "미실행" in x][:6]
    tap("확인", settle=0.3)
    rec["consent_at"] = time.strftime("%H:%M:%S")
    # 6. observe vehicle through the Console
    t0 = time.time(); after = state()
    while time.time() - t0 < 25:
        after = state()
        if after["driver_temp"] == TARGET["temp"] and after["brightness"] == TARGET["brightness"] and (after["color"] or "").lower() == TARGET["color"]:
            break
        time.sleep(0.5)
    rec["after"] = after
    rec["vehicle_reflected_s"] = round(time.time() - t0, 1)
    time.sleep(2)
    running = texts()
    rec["running_banners"] = [x for x in running if "음원" in x or "남은" in x or "RUNNING" in x or "미실행 단계" in x][:6]
    rec["audio_players_running"] = audio_players()
    shot(f"{tag}-running.png")
    # 7. stop
    if find("실행 중지", wait=3):
        tap("실행 중지", settle=2.5)
        rec["stopped_at"] = time.strftime("%H:%M:%S")
    else:
        rec["notes"].append("실행 중지 button not found")
    rec["after_stop_banners"] = [x for x in texts() if "음원" in x or "남은" in x or "실행 중지" in x][:6]
    rec["audio_players_after_stop"] = audio_players()
    rec["state_after_stop"] = state()
    # 8. history
    lines = history_lines()
    rec["history"] = lines
    shot(f"{tag}-history.png")
    steps = [l for l in lines if re.match(r"#\d+ · ", l)]
    rec["steps"] = steps
    head = next((l for l in lines if l.startswith("실제 실행 ·")), "")
    rec["run_status"] = head
    if mode == "CONDITION_ENTER":
        tap("내 루틴")
        if find("끄기", wait=3):
            tap("끄기", index=0, settle=1.5)
            rec["disabled_after"] = True
    # logcat evidence of SDK property events
    lc = adb("logcat", "-d")
    rec["logcat_props"] = [l for l in lc.splitlines() if "TemperatureControlView: onPropertyStateChanged property ID: HVAC_TEMPERATURE_SET" in l
                           or ("[Car-Context-Observe]" in l and ("HVAC_TEMPERATURE_SET" in l or "AMBIENT" in l))][:12]
    rec["verdict_inputs"] = judge(rec)
    with open(os.path.join(OUT, f"{tag}.json"), "w", encoding="utf-8") as f:
        json.dump(rec, f, ensure_ascii=False, indent=2)
    log(tag, "done", json.dumps(rec["verdict_inputs"], ensure_ascii=False))
    return rec


def judge(rec):
    steps = rec.get("steps", [])
    def status(label):
        hits = [s.split(" · ")[2] for s in steps if label in s]
        return hits[-1] if hits else None
    b, a = rec.get("before", {}), rec.get("after", {})
    return {
        "hvac": status("공조 목표 23°C"), "brightness": status("앰비언트 밝기 30%"), "color": status("앰비언트 파랑"),
        "seat_fore_aft": status("시트 앞뒤"), "seat_backrest": status("등받이"), "audio": status("휴식 음원"),
        "timer": status("30분 휴식"), "run": rec.get("run_status"),
        "console_changed": {"temp": [b.get("driver_temp"), a.get("driver_temp")], "brightness": [b.get("brightness"), a.get("brightness")], "color": [b.get("color"), a.get("color")]},
        "seat_positions_unchanged": (b.get("seat_slide_driver"), b.get("seat_recline_driver")) == (a.get("seat_slide_driver"), a.get("seat_recline_driver")),
    }


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    threading.Thread(target=heartbeat, daemon=True).start()
    first, last = int(sys.argv[1]), int(sys.argv[2])
    for i in range(first, last + 1):
        try:
            run_round(i)
        except Exception as e:
            log(f"R{i:02d}", "ERROR", repr(e))
            shot(f"R{i:02d}-error.png")
            with open(os.path.join(OUT, f"R{i:02d}-error.txt"), "w", encoding="utf-8") as f:
                f.write(repr(e) + "\n" + "\n".join(texts()))
            raise
