"""Smoke test of the capture set-up: the app, the fake backend and the logs. Python 3.7+.

    python scripts/smoke-test.py

Run it after a capture (or before one, after signing in by hand). It fails when
  * the fake backend answered 404 to something (a request the contract does not know),
  * the app log has a crash, or a sync / parsing error.
"""
import json
import re
import sys
import urllib.request

import gl

IGNORED_PATHS = ("/favicon.ico",)
BAD_LOG = re.compile(r"FATAL EXCEPTION|DataFormatException|JsonDataException|JsonEncodingException|FHIRException|ResourceNotFound|HttpException")


def main():
    problems = []
    try:
        with urllib.request.urlopen(gl.FAKE_URL + "/__control/log", timeout=5) as response:
            log = json.loads(response.read().decode("utf-8"))
    except Exception as error:
        print("the fake backend does not answer: %s" % error)
        return 1
    unhandled = [e for e in log["unhandled"] if not e["path"].endswith(IGNORED_PATHS)]
    for e in unhandled:
        problems.append("not answered by the fake backend: %s %s?%s" % (e["method"], e["path"], e["query"]))
    seen = log["last"]
    print("requests seen: %d, unanswered: %d" % (len(seen), len(unhandled)))
    logcat = gl.adb("logcat", "-d", "-t", "4000").stdout.decode("utf-8", "replace")
    for line in logcat.splitlines():
        if gl.PACKAGE.split(".debug")[0] in line or "AndroidRuntime" in line:
            if BAD_LOG.search(line):
                problems.append("app log: " + line.strip()[:200])
    for p in problems[:30]:
        print(p)
    print("smoke test: %s" % ("FAILED (%d)" % len(problems) if problems else "passed"))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
