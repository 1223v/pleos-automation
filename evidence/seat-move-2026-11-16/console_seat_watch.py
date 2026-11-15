"""Record Console seat state while the app's SDK probe runs (test harness, read-only except heartbeat).

Polls GET /vehicle/state every 50 ms and prints one line whenever the driver slide/recline
move command or position changes. Sends the Console page's own browser-session heartbeat.
Usage: python -I console_seat_watch.py <seconds>
"""
import json, sys, threading, time, urllib.request, uuid

C = "http://127.0.0.1:8088"
cid = str(uuid.uuid4())


def post_heartbeat():
    while True:
        try:
            urllib.request.urlopen(urllib.request.Request(f"{C}/browser/session?client={cid}", method="POST", data=b""), timeout=3).read()
        except Exception:
            pass
        time.sleep(1)


threading.Thread(target=post_heartbeat, daemon=True).start()
end = time.time() + float(sys.argv[1])
last = None
while time.time() < end:
    try:
        s = json.loads(urllib.request.urlopen(f"{C}/vehicle/state", timeout=3).read())["seat"]
        cur = (s["moves"]["slide"]["driver"], s["positions"]["slide"]["driver"], s["moves"]["recline"]["driver"], s["positions"]["recline"]["driver"], s["actuator"]["active_count"])
        if cur != last:
            print(f"{time.strftime('%H:%M:%S')}.{int(time.time()*1000)%1000:03d} slide_move={cur[0]} slide_pos={cur[1]} recline_move={cur[2]} recline_pos={cur[3]} active={cur[4]}", flush=True)
            last = cur
    except Exception as e:
        print("poll error", repr(e), flush=True)
    time.sleep(0.05)
