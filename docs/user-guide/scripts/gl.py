"""Shared helpers of the user-guide tools (Python 3.7+, standard library plus PyYAML and Pillow).

* the app's strings in every language (the guide uses the app's own words),
* the paths of the guide,
* a small wrapper around adb and uiautomator.
"""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
GUIDE = os.path.dirname(HERE)
REPO = os.path.dirname(os.path.dirname(GUIDE))
RES = os.path.join(REPO, "core", "src", "main", "res")
LANGS = ("en", "fr", "es", "ru", "ar")
LANG_NAMES = {"en": "English", "fr": "Français", "es": "Español", "ru": "Русский", "ar": "العربية"}
PACKAGE = "org.openmrs.android.fhir.dev3_app.debug"
SERIAL = os.environ.get("ANDROID_SERIAL", "emulator-5554")
FAKE_PORT = int(os.environ.get("FAKE_PORT", "8089"))
FAKE_URL = "http://127.0.0.1:%d" % FAKE_PORT


def _clean(text):
    text = text.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n").replace("\\@", "@").replace("\\?", "?")
    return re.sub(r"\s+", " ", text).strip()


_strings_cache = {}


def strings(lang):
    """name -> text of core/src/main/res/values[-lang]/strings.xml (English when a key is missing)."""
    if lang in _strings_cache:
        return _strings_cache[lang]
    folder = "values" if lang == "en" else "values-" + lang
    tree = ET.parse(os.path.join(RES, folder, "strings.xml"))
    out = {}
    for el in tree.getroot().findall("string"):
        out[el.get("name")] = _clean("".join(el.itertext()))
    if lang != "en":
        merged = dict(strings("en"))
        merged.update(out)
        out = merged
    _strings_cache[lang] = out
    return out


def keys_for_english(text):
    """The string keys whose English text is `text`."""
    t = _clean(text)
    return [k for k, v in strings("en").items() if v == t]


def label(text, lang):
    """The words of the app in `lang` for an English label, or the key written as `@key`."""
    if text.startswith("@"):
        return strings(lang)[text[1:]]
    candidates = [strings(lang).get(k) for k in keys_for_english(text)]
    candidates = [c for c in candidates if c]
    return candidates[0] if candidates else text


def labels(text, lang):
    """Every translation that an English label can have (several keys can share one English text)."""
    if text.startswith("@"):
        return [strings(lang)[text[1:]]]
    found = [strings(lang).get(k) for k in keys_for_english(text)]
    found = [c for c in found if c]
    return found or [text]


# ---- adb ------------------------------------------------------------------------------------------
def adb(*args, **kw):
    cmd = ["adb", "-s", SERIAL] + list(args)
    return subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kw)


def shell(command):
    return adb("shell", command).stdout.decode("utf-8", "replace")


def screencap(path):
    data = adb("exec-out", "screencap", "-p").stdout
    with open(path, "wb") as handle:
        handle.write(data)


def nodes():
    """The visible text nodes of the screen: (text, class, (l, t, r, b), hint)."""
    adb("shell", "uiautomator", "dump", "/sdcard/u.xml")
    xml = adb("exec-out", "cat", "/sdcard/u.xml").stdout.decode("utf-8", "replace")
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return []
    out = []
    for n in root.iter("node"):
        b = [int(x) for x in re.findall(r"\d+", n.get("bounds") or "0 0 0 0")]
        text = n.get("text") or n.get("content-desc") or ""
        out.append((_clean(text), n.get("class") or "", tuple(b[:4]), n.get("hint") or ""))
    return out


def tap_xy(x, y):
    adb("shell", "input", "tap", str(int(x)), str(int(y)))


def tap_node(node):
    l, t, r, b = node[2]
    tap_xy((l + r) // 2, (t + b) // 2)


def find(wanted, lang, exact=True, nth=0, snapshot=None):
    options = [_clean(w) for w in labels(wanted, lang)] + [wanted]
    found = []
    for n in snapshot if snapshot is not None else nodes():
        if not n[0]:
            continue
        for o in options:
            if (n[0] == o) if exact else (o.lower() in n[0].lower()):
                found.append(n)
                break
    if not found:
        return None
    try:
        return found[nth]
    except IndexError:
        return None


def wait_for(wanted, lang, timeout=25, exact=True):
    end = time.time() + timeout
    while time.time() < end:
        n = find(wanted, lang, exact)
        if n:
            return n
        time.sleep(1.0)
    return None


def key(code):
    adb("shell", "input", "keyevent", str(code))


def type_text(text):
    adb("shell", "input", "text", text.replace(" ", "%s"))
