#!/usr/bin/env python3
"""Rewrites FHIR Questionnaire JSON files into the compact form the app expands on load.

    python tools/compact_questionnaires.py core/src/main/assets/questionnaires

The compact form (read by `expandCompactQuestionnaire`, core/.../forms/CompactQuestionnaire.kt):
  * translations: "_text": {"extension": [{translation ...}]}  ->  "text:i18n": {"fr": "...", ...}
  * "url" / "system" values start with a short prefix name: "@h/questionnaire-itemControl"
  * the first key, "$compact", maps the prefix names to the URLs they stand for.

Every file is expanded again and compared with the original before it is written; files already
compact, or that contain a decimal such as 35.0 (the app's JSON reader would write it as 35), are
left alone.
"""
import copy
import glob
import json
import os
import re
import sys
from collections import OrderedDict

TRANSLATION_URL = "http://hl7.org/fhir/StructureDefinition/translation"
I18N_SUFFIX = ":i18n"
MARK = "$compact"
PREFIXES = OrderedDict(
    [
        ("h", "http://hl7.org/fhir/StructureDefinition/"),
        ("s", "http://hl7.org/fhir/uv/sdc/StructureDefinition/"),
        ("o", "http://fhir.openmrs.org/ext/"),
        ("q", "http://hl7.org/fhir/questionnaire-item-control"),
    ]
)


def is_plain_translation(ext):
    return (
        isinstance(ext, dict)
        and list(ext.keys()) == ["url", "extension"]
        and ext["url"] == TRANSLATION_URL
        and isinstance(ext["extension"], list)
        and len(ext["extension"]) == 2
        and list(ext["extension"][0].keys()) == ["url", "valueCode"]
        and ext["extension"][0]["url"] == "lang"
        and list(ext["extension"][1].keys()) == ["url", "valueString"]
        and ext["extension"][1]["url"] == "content"
    )


def compact_i18n(o):
    if isinstance(o, list):
        return [compact_i18n(v) for v in o]
    if not isinstance(o, dict):
        return o
    out = OrderedDict()
    for k, v in o.items():
        if (
            k.startswith("_")
            and isinstance(v, dict)
            and list(v.keys()) == ["extension"]
            and isinstance(v["extension"], list)
            and v["extension"]
            and all(is_plain_translation(e) for e in v["extension"])
        ):
            langs = [e["extension"][0]["valueCode"] for e in v["extension"]]
            if len(set(langs)) == len(langs):
                out[k[1:] + I18N_SUFFIX] = OrderedDict(
                    (e["extension"][0]["valueCode"], e["extension"][1]["valueString"])
                    for e in v["extension"]
                )
                continue
        out[k] = compact_i18n(v)
    return out


def expand_i18n(o):
    if isinstance(o, list):
        return [expand_i18n(v) for v in o]
    if not isinstance(o, dict):
        return o
    out = OrderedDict()
    for k, v in o.items():
        if k.endswith(I18N_SUFFIX) and isinstance(v, dict):
            out["_" + k[: -len(I18N_SUFFIX)]] = OrderedDict(
                [
                    (
                        "extension",
                        [
                            OrderedDict(
                                [
                                    ("url", TRANSLATION_URL),
                                    (
                                        "extension",
                                        [
                                            OrderedDict([("url", "lang"), ("valueCode", lang)]),
                                            OrderedDict([("url", "content"), ("valueString", text)]),
                                        ],
                                    ),
                                ]
                            )
                            for lang, text in v.items()
                        ],
                    )
                ]
            )
        else:
            out[k] = expand_i18n(v)
    return out


def alias_urls(o, used):
    if isinstance(o, list):
        return [alias_urls(v, used) for v in o]
    if not isinstance(o, dict):
        return o
    out = OrderedDict()
    for k, v in o.items():
        if k in ("url", "system") and isinstance(v, str):
            assert not v.startswith("@"), v
            for key, prefix in PREFIXES.items():
                if v.startswith(prefix):
                    v = "@" + key + "/" + v[len(prefix):]
                    used.add(key)
                    break
            out[k] = v
        else:
            out[k] = alias_urls(v, used)
    return out


def unalias_urls(o, prefixes):
    if isinstance(o, list):
        return [unalias_urls(v, prefixes) for v in o]
    if not isinstance(o, dict):
        return o
    out = OrderedDict()
    for k, v in o.items():
        if k in ("url", "system") and isinstance(v, str) and v.startswith("@"):
            key, rest = v[1:].split("/", 1)
            out[k] = prefixes[key] + rest
        else:
            out[k] = unalias_urls(v, prefixes)
    return out


def compact(d):
    used = set()
    c = alias_urls(compact_i18n(d), used)
    head = OrderedDict()
    head[MARK] = OrderedDict((k, PREFIXES[k]) for k in PREFIXES if k in used)
    head.update(c)
    return head


def expand(c):
    c = copy.deepcopy(c)
    prefixes = c.pop(MARK)
    return expand_i18n(unalias_urls(c, prefixes))


def dumps(o):
    return json.dumps(o, ensure_ascii=False, separators=(",", ":"))


def main(folder):
    saved = 0
    for path in sorted(glob.glob(os.path.join(folder, "*.json"))):
        text = open(path, encoding="utf8").read()
        d = json.loads(text, object_pairs_hook=OrderedDict)
        if MARK in d:
            continue
        # Strings are blanked first so a number inside a text is not mistaken for a decimal.
        stripped = re.sub(r'"(?:[^"\\]|\\.)*"', '""', text)
        if re.search(r"[0-9]\.[0-9]*0[,}\]\s]", stripped):
            print("skipped (decimal with a trailing zero): " + path)
            continue
        c = compact(d)
        assert dumps(expand(c)) == dumps(d), "round trip failed: " + path
        out = dumps(c)
        saved += len(text.encode("utf8")) - len(out.encode("utf8"))
        open(path, "w", encoding="utf8", newline="\n").write(out)
        print("compacted " + path)
    print("saved %d bytes" % saved)


if __name__ == "__main__":
    main(sys.argv[1])
