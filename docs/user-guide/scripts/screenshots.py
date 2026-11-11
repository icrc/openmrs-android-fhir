"""Captures the screenshots of the guide from the manifest (screenshots/manifest.yml).

    python scripts/screenshots.py --lang en                 # all shots of one language
    python scripts/screenshots.py --lang fr --ids home-01,patients-01
    python scripts/screenshots.py --lang en --post-only     # only redraw the polished images

It needs the fake backend running (fake-backend/server.py), the debug app of the `dev3-app` module
installed on the emulator, and the emulator set to a 2560x1600 landscape tablet (scripts/device.py).
Nothing here contacts a real server: the app is pointed at the fake backend before its first start.
"""
import argparse
import json
import os
import sys
import time
import urllib.request

import yaml

import gl

sys.path.insert(0, os.path.join(gl.GUIDE, "fake-backend"))
import data as fake  # noqa: E402

RAW = os.path.join(gl.GUIDE, "screenshots", "raw")
OUT = os.path.join(gl.GUIDE, "screenshots")
MANIFEST = os.path.join(OUT, "manifest.yml")
SERVER_URL = "http://openmrs-demo.example:%d/openmrs" % gl.FAKE_PORT


def control(path):
    with urllib.request.urlopen(gl.FAKE_URL + "/__control/" + path, timeout=10) as response:
        return json.loads(response.read().decode("utf-8"))


def expand(text, lang):
    """{loc:north} {pat:p01} {grp:morning} {user} {pw} become the fictional data in that language."""
    if "{" not in text:
        return text
    out = text
    for key, names in fake.LOCATIONS:
        out = out.replace("{loc:%s}" % key, names[lang])
    for p in fake.PATIENTS:
        given, family = p[4][lang]
        out = out.replace("{pat:%s}" % p[0], "%s %s" % (given, family))
        out = out.replace("{given:%s}" % p[0], given)
        out = out.replace("{family:%s}" % p[0], family)
        out = out.replace("{id:%s}" % p[0], fake.identifier_value([x[0] for x in fake.PATIENTS].index(p[0])))
    for g in fake.GROUPS:
        out = out.replace("{grp:%s}" % g[0], g[2][lang])
    return out.replace("{user}", fake.DEMO_USER).replace("{pw}", fake.DEMO_PASSWORD)


# ---- device preparation ---------------------------------------------------------------------------
def prepare_device():
    gl.shell("wm size 1600x2560; wm density 320")
    gl.shell("settings put system accelerometer_rotation 0; settings put system user_rotation 0")
    gl.shell("cmd uimode night no")
    gl.shell("settings put global sysui_demo_allowed 1")
    for cmd in ("-e command enter", "-e command clock -e hhmm 0941", "-e command battery -e level 100 -e plugged false",
                "-e command network -e wifi show -e level 4 -e fully true", "-e command notifications -e visible false"):
        gl.shell("am broadcast -a com.android.systemui.demo " + cmd)


def start_app():
    gl.shell("am start -n %s/org.openmrs.android.fhir.SplashActivity" % gl.PACKAGE)


def write_prefs(name, xml):
    path = os.path.join(gl.GUIDE, "scripts", ".tmp-" + name)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(xml)
    gl.shell("run-as %s mkdir -p shared_prefs" % gl.PACKAGE)
    with open(path, "rb") as handle:
        import subprocess

        subprocess.run(["adb", "-s", gl.SERIAL, "shell", "run-as %s sh -c 'cat > shared_prefs/%s'" % (gl.PACKAGE, name)], stdin=handle)
    os.remove(path)


def reset_app(lang):
    """A fresh install state, pointed at the fake backend, in `lang`, without the first-run tour."""
    control("lang?code=%s" % lang)
    control("scenario?name=default")
    gl.shell("am force-stop " + gl.PACKAGE)
    gl.shell("pm clear " + gl.PACKAGE)
    gl.shell("pm grant %s android.permission.POST_NOTIFICATIONS" % gl.PACKAGE)
    gl.adb("shell", "cmd", "locale", "set-app-locales", gl.PACKAGE, "--locales", lang)
    prefs = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <string name=\"server_url\">%s</string>\n</map>\n" % SERVER_URL
    write_prefs("server_settings.xml", prefs)
    # The first start sets the tour flag; set it back before the home screen shows.
    start_app()
    time.sleep(4)
    gl.shell("am force-stop " + gl.PACKAGE)
    write_prefs("first_run_tutorial.xml", "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <boolean name=\"pending\" value=\"false\" />\n</map>\n")
    start_app()
    time.sleep(3)


# ---- actions --------------------------------------------------------------------------------------
class Runner(object):
    def __init__(self, lang):
        self.lang = lang
        self.snapshot = None

    def L(self, text):
        return expand(text, self.lang)

    def node(self, text, exact=True, nth=0, timeout=20):
        text = self.L(text)
        end = time.time() + timeout
        while time.time() < end:
            n = gl.find(text, self.lang, exact=exact, nth=nth)
            if n:
                return n
            time.sleep(0.8)
        raise RuntimeError("not on screen: %r (%s)" % (text, self.lang))

    def do(self, step):
        if isinstance(step, str):
            step = {step: True}
        (verb, arg), = [(k, v) for k, v in step.items() if k not in ("exact", "nth", "timeout", "absolute")]
        exact = step.get("exact", True)
        nth = step.get("nth", 0)
        timeout = step.get("timeout", 20)
        if verb == "tap":
            gl.tap_node(self.node(arg, exact, nth, timeout))
            time.sleep(1.3)
        elif verb == "web_type":
            time.sleep(0.6)
            gl.type_text(self.L(arg))
            time.sleep(0.8)
        elif verb == "scroll_to":
            for _ in range(14):
                n = gl.find(self.L(arg), self.lang)
                if n and 300 < n[2][1] < 1300:
                    break
                gl.adb("shell", "input", "swipe", "800", "2000", "800", "1100", "500")
                time.sleep(1.0)
        elif verb == "open_menu":
            if not gl.find(self.L("Send diagnostics"), self.lang):
                gl.tap_xy(1528 if self.lang == "ar" else 72, 112)
                time.sleep(1.2)
        elif verb == "hide_keyboard":
            gl.key(4)
            time.sleep(0.8)
        elif verb == "home":
            for _ in range(6):
                if gl.find(self.L("Register patient"), self.lang):
                    break
                gl.key(4)
                time.sleep(1.4)
                for dismiss in ("Discard", "Cancel", "Leave", "Close"):
                    n = gl.find(self.L(dismiss), self.lang)
                    if n and gl.find(self.L("Discard"), self.lang):
                        gl.tap_node(n)
                        time.sleep(1)
                        break
        elif verb == "tap_class":
            found = [n for n in gl.nodes() if n[1].endswith(arg)]
            gl.tap_node(found[nth])
            time.sleep(1.0)
        elif verb == "tap_xy":
            # the screen is mirrored in a right-to-left language (the menu button is on the right)
            x = 1600 - arg[0] if (self.lang == "ar" and not step.get("absolute")) else arg[0]
            gl.tap_xy(x, arg[1])
            time.sleep(1.2)
        elif verb == "expect":
            self.node(arg, exact, nth, timeout)
        elif verb == "absent":
            end = time.time() + timeout
            while time.time() < end and gl.find(self.L(arg), self.lang, exact=exact):
                time.sleep(1)
        elif verb == "type":
            fields = [n for n in gl.nodes() if n[1].endswith("EditText")]
            f = fields[arg.get("field", 0)]
            gl.tap_node(f)
            time.sleep(0.6)
            gl.type_text(self.L(arg["text"]))
            gl.key(4)
            time.sleep(0.8)
        elif verb == "clear":
            fields = [n for n in gl.nodes() if n[1].endswith("EditText")]
            gl.tap_node(fields[arg])
            time.sleep(0.5)
            gl.key(123)
            for _ in range(40):
                gl.key(67)
            gl.key(4)
            time.sleep(0.8)
        elif verb == "back":
            for _ in range(int(arg) if arg is not True else 1):
                gl.key(4)
                time.sleep(1.2)
        elif verb == "wait":
            time.sleep(float(arg))
        elif verb == "swipe":
            gl.adb("shell", "input", "swipe", *[str(int(v)) for v in arg])
            time.sleep(1.0)
        elif verb == "scenario":
            control("scenario?name=%s" % arg)
        elif verb == "key":
            gl.key(arg)
            time.sleep(0.8)
        elif verb == "tap_if":
            n = gl.find(self.L(arg), self.lang, exact=exact)
            if n:
                gl.tap_node(n)
                time.sleep(1.3)
        elif verb == "shot":
            self.shot(arg)
        else:
            raise ValueError("unknown step: %r" % (step,))

    def shot(self, spec):
        shot_id = spec["id"] if isinstance(spec, dict) else spec
        folder = os.path.join(RAW, self.lang)
        os.makedirs(folder, exist_ok=True)
        time.sleep(1.0)
        gl.screencap(os.path.join(folder, shot_id + ".png"))
        marks = []
        snapshot = gl.nodes()
        for c in (spec.get("callouts", []) if isinstance(spec, dict) else []):
            if "at" not in c:
                continue
            n = gl.find(self.L(c["at"]), self.lang, exact=c.get("exact", True), nth=c.get("nth", 0), snapshot=snapshot)
            if n is None:
                print("  ! callout target not found: %r in %s" % (c["at"], shot_id))
                continue
            marks.append({"n": c["n"], "box": list(n[2]), "side": c.get("side", "auto")})
        for c in (spec.get("callouts", []) if isinstance(spec, dict) else []):
            if "box" in c:
                marks.append({"n": c["n"], "box": c["box"], "side": "auto"})
        redact = []
        for r in (spec.get("redact", []) if isinstance(spec, dict) else []):
            for n in snapshot:
                if n[0] and r["contains"] in n[0]:
                    redact.append({"box": list(n[2]), "text": r["text"]})
        with open(os.path.join(folder, shot_id + ".json"), "w", encoding="utf-8") as handle:
            json.dump({"id": shot_id, "lang": self.lang, "callouts": marks, "redact": redact}, handle)
        print("  captured %s/%s" % (self.lang, shot_id))


def onboard(runner):
    """Log in, choose the location and the lists, wait for the first sync: the home screen is up."""
    runner.node("Log in", timeout=30)
    for index, text in ((0, fake.DEMO_USER), (1, fake.DEMO_PASSWORD)):
        fields = [n for n in gl.nodes() if n[1].endswith("EditText")]
        gl.tap_node(fields[index])
        time.sleep(0.6)
        gl.type_text(text)
        gl.key(4)
        time.sleep(0.8)
    runner.do({"tap": "Log in"})
    runner.do({"tap": "Not now", "timeout": 30})
    runner.do({"tap": "{loc:north}", "timeout": 90})
    runner.do({"tap": "Next"})
    runner.node("{grp:morning}", timeout=40)
    runner.do({"tap": "{grp:morning}"})
    runner.do({"tap": "{grp:followup}"})
    runner.do({"tap": "Submit"})
    runner.node("Register patient", timeout=180)
    time.sleep(2)


def run(lang, ids=None, skip_reset=False):
    manifest = yaml.safe_load(open(MANIFEST, encoding="utf-8"))
    prepare_device()
    runner = Runner(lang)
    if not skip_reset:
        reset_app(lang)
    for flow in manifest["flows"]:
        if skip_reset and flow.get("setup"):
            continue
        wanted = [s for s in flow["steps"] if isinstance(s, dict) and "shot" in s]
        if ids and not any((s["shot"]["id"] if isinstance(s["shot"], dict) else s["shot"]) in ids for s in wanted) and not flow.get("setup"):
            continue
        print("flow %s" % flow["name"])
        for step in flow["steps"]:
            if step == "onboard":
                onboard(runner)
                continue
            if isinstance(step, dict) and "shot" in step and ids:
                sid = step["shot"]["id"] if isinstance(step["shot"], dict) else step["shot"]
                if sid not in ids:
                    continue
            runner.do(step)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--lang", default="en", choices=gl.LANGS)
    parser.add_argument("--ids", default="")
    parser.add_argument("--skip-reset", action="store_true")
    args = parser.parse_args()
    run(args.lang, [i for i in args.ids.split(",") if i], args.skip_reset)


if __name__ == "__main__":
    main()
