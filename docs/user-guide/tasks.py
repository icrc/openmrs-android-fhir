#!/usr/bin/env python3
"""The tasks of the user guide, the same on Windows, macOS and Linux (Python 3.7+, no make, no bash).

    python tasks.py <task> [--lang fr]

Run it from anywhere: it works in docs/user-guide. `python tasks.py --help` lists the tasks.
"""
import argparse
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
LANGS = ["en", "fr", "es", "ru", "ar"]
INIT_SCRIPT = os.path.join(HERE, "device-config", "guide-overlay.init.gradle")

sys.path.insert(0, os.path.join(ROOT, "tools"))
import dev  # noqa: E402  (the helpers of tools/dev.py: adb, Gradle)


def call(cmd, cwd=HERE, env=None):
    environment = dict(os.environ)
    environment["PYTHONIOENCODING"] = "utf-8"
    if env:
        environment.update(env)
    code = subprocess.call(cmd, cwd=cwd, env=environment)
    if code != 0:
        raise dev.Fail("%s failed (exit code %d)" % (" ".join(str(c) for c in cmd[:3]), code))


def py(script, *args):
    call([sys.executable, os.path.join(HERE, script)] + list(args))


def node_build(*args):
    node = shutil.which("node")
    if not node:
        raise dev.Fail("Node.js 22 or newer is needed to build the PDFs (https://nodejs.org)")
    call([node, "build.mjs"] + list(args), cwd=os.path.join(HERE, "pdf"))


# ----------------------------------------------------------------------------------------------


def deps(_args):
    npm = shutil.which("npm")
    if not npm:
        raise dev.Fail("npm not found: install Node.js 22 or newer")
    call([npm, "install"], cwd=os.path.join(HERE, "pdf"))


def fake_backend_up(_args):
    py("fake-backend/ctl.py", "up")


def fake_backend_down(_args):
    py("fake-backend/ctl.py", "down")


def app(args):
    """Builds the debug dev3-app with the guide's test configuration and installs it. No project file changes."""
    adb = dev.require_adb()
    serial = args.serial or os.environ.get("ANDROID_SERIAL")
    devices = dev.pick_devices(adb, serial)
    if len(devices) > 1 and not serial:
        raise dev.Fail("several devices connected (%s): pass --serial" % ", ".join(devices))
    print("== Building :dev3-app:assembleDebug with the guide configuration")
    call(dev.gradle_command() + ["-q", "--init-script", INIT_SCRIPT, ":dev3-app:assembleDebug"], cwd=ROOT, env=dev.gradle_env())
    apk = os.path.join(ROOT, "dev3-app", "build", "outputs", "apk", "debug", "dev3-app-debug.apk")
    print("== Installing %s on %s" % (os.path.basename(apk), devices[0]))
    call([adb, "-s", devices[0], "install", "-r", apk])


def capture(lang, args):
    options = ["--lang", lang]
    if getattr(args, "ids", ""):
        options += ["--ids", args.ids]
    if getattr(args, "skip_reset", False):
        options.append("--skip-reset")
    py("fake-backend/ctl.py", "up")
    py("scripts/screenshots.py", *options)
    py("scripts/postprocess.py", "--lang", lang)


def screenshots(args):
    capture(args.lang, args)


def screenshots_all(args):
    for lang in LANGS:
        capture(lang, args)


def polish(_args):
    py("scripts/postprocess.py", "--lang", "all")


def status(_args):
    py("scripts/guide.py", "status")


def check_translations(_args):
    py("scripts/guide.py", "check-translations")


def check(_args):
    py("scripts/guide.py", "check")
    py("scripts/guide.py", "check-translations")


def mark(args):
    options = ["--lang", args.lang] if args.lang else []
    py("scripts/guide.py", "mark", *(options + list(args.files)))


def smoke_test(_args):
    py("scripts/smoke-test.py")


def glossary(_args):
    py("scripts/make-glossary.py")


def html(args):
    node_build("--lang", args.lang, "--html")


def pdf(args):
    node_build("--lang", args.lang)


def pdf_all(_args):
    for lang in LANGS:
        node_build("--lang", lang)


TASKS = [
    ("deps", deps, "install the PDF tools once (npm install in pdf/)", []),
    ("fake-backend-up", fake_backend_up, "start the fictional server", []),
    ("fake-backend-down", fake_backend_down, "stop it", []),
    ("app", app, "build the test app and install it on the emulator", ["serial"]),
    ("screenshots", screenshots, "capture and polish one language", ["lang", "ids"]),
    ("screenshots-all", screenshots_all, "capture and polish all languages", []),
    ("polish", polish, "redraw the images from the raw captures", []),
    ("status", status, "what needs translating", []),
    ("check-translations", check_translations, "check the translations", []),
    ("check", check, "check the guide and its translations", []),
    ("mark", mark, "record a translation as up to date: mark --lang fr [file.md ...]", ["lang", "files"]),
    ("smoke-test", smoke_test, "after a capture: unanswered requests, crashes in the app log", []),
    ("glossary", glossary, "rebuild i18n/glossary.yml from the app strings", []),
    ("html", html, "HTML preview of one language", ["lang"]),
    ("pdf", pdf, "PDF of one language", ["lang"]),
    ("pdf-all", pdf_all, "PDFs of all languages", []),
]


def build_parser():
    parser = argparse.ArgumentParser(prog="tasks.py", description="Tasks of the user guide.")
    sub = parser.add_subparsers(dest="task", metavar="<task>")
    sub.required = True
    for name, function, text, options in TASKS:
        p = sub.add_parser(name, help=text)
        if "lang" in options:
            # `mark` without --lang records every language
            p.add_argument("--lang", default=None if name == "mark" else "en", choices=LANGS)
        if "serial" in options:
            p.add_argument("--serial", "-s")
        if "ids" in options:
            p.add_argument("--ids", default="", help="only these shots, comma separated")
            p.add_argument("--skip-reset", action="store_true", help="keep the app state of the previous capture")
        if "files" in options:
            p.add_argument("files", nargs="*", help="chapters, e.g. 11-patients.md (none = all)")
        p.set_defaults(func=function)
    return parser


def main():
    args = build_parser().parse_args()
    try:
        args.func(args)
    except dev.Fail as error:
        print("error: %s" % error, file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        return 130
    return 0


if __name__ == "__main__":
    sys.exit(main())
