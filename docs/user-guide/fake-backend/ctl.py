"""Starts, stops and controls the fake backend. Python 3.7+, standard library only.

    python ctl.py up [--port 8089]     start it in the background (log: fake.log)
    python ctl.py down                 stop it
    python ctl.py status               is it running, which language and scenario
    python ctl.py lang fr              serve the data in French (en, fr, es, ru, ar)
    python ctl.py scenario empty       default | empty | error | validation | slow | offline
    python ctl.py reset                forget what was uploaded
"""
import json
import os
import subprocess
import sys
import time
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
PORT = int(os.environ.get("FAKE_PORT", "8089"))


def call(path, quiet=False):
    try:
        with urllib.request.urlopen("http://127.0.0.1:%d/__control/%s" % (PORT, path), timeout=5) as response:
            body = json.loads(response.read().decode("utf-8"))
            if not quiet:
                print(json.dumps(body))
            return body
    except Exception:
        return None


def up(args):
    global PORT
    if "--port" in args:
        PORT = int(args[args.index("--port") + 1])
    if call("state", quiet=True) is not None:
        print("already running on port %d" % PORT)
        return 0
    log = open(os.path.join(HERE, "fake.log"), "w")
    flags = 0
    if os.name == "nt":
        flags = 0x00000008 | 0x00000200  # DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP
    subprocess.Popen([sys.executable, os.path.join(HERE, "server.py"), "--port", str(PORT)], stdout=log, stderr=log, cwd=HERE, creationflags=flags)
    for _ in range(30):
        if call("state", quiet=True) is not None:
            print("fake backend up on http://127.0.0.1:%d/openmrs (emulator: http://10.0.2.2:%d/openmrs)" % (PORT, PORT))
            return 0
        time.sleep(0.3)
    print("it did not start: see fake.log")
    return 1


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    cmd, args = sys.argv[1], sys.argv[2:]
    if cmd == "up":
        return up(args)
    if cmd == "down":
        print("stopped" if call("shutdown", quiet=True) is not None else "not running")
        return 0
    if cmd == "status":
        body = call("state", quiet=True)
        print(json.dumps(body) if body else "not running")
        return 0 if body else 1
    if cmd == "lang" and args:
        return 0 if call("lang?code=" + args[0]) else 1
    if cmd == "scenario" and args:
        return 0 if call("scenario?name=" + args[0]) else 1
    if cmd == "reset":
        return 0 if call("reset") else 1
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main())
