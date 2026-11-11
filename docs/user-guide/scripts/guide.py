"""Tools to keep the user guide and its translations in step. Python 3.7+, standard library (+ PyYAML for the glossary).

    python scripts/guide.py status                 table: language x chapter (ok / stale / missing / structure)
    python scripts/guide.py check-translations     lists what is missing, stale or structurally different
    python scripts/guide.py sync-structure         creates skeleton files for English chapters missing in a language
    python scripts/guide.py mark [--lang fr] [file.md ...]   records that the translation is up to date with the English
    python scripts/guide.py check                  checks images, alt text, links, label keys, headings; fails when something is wrong

English (content/en) is the source. A translation is "stale" when the English file changed after the
hash recorded in i18n/translation-status.json.
"""
import argparse
import hashlib
import json
import os
import re
import sys

import gl

CONTENT = os.path.join(gl.GUIDE, "content")
STATUS = os.path.join(gl.GUIDE, "i18n", "translation-status.json")
SOURCE_LANG = "en"


def sha(path):
    with open(path, "rb") as handle:
        return hashlib.sha256(handle.read().replace(b"\r\n", b"\n")).hexdigest()[:16]


def chapter_files():
    return sorted(f for f in os.listdir(os.path.join(CONTENT, SOURCE_LANG)) if re.match(r"^\d\d-.*\.md$", f))


def read(lang, name):
    path = os.path.join(CONTENT, lang, name)
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def structure(text):
    """What must be identical between a chapter and its translations."""
    return {
        "heading_ids": re.findall(r"\{#([A-Za-z0-9_-]+)[^}]*\}", text),
        "images": [os.path.basename(i) for i in re.findall(r"!\[[^\]]*\]\(([^)]+)\)", text)],
        "steps": len(re.findall(r"(?m)^\s*\d+\. ", text)),
        "labels": sorted(re.findall(r"\{\{([a-z0-9_]+)\}\}", text)),
        "callouts": len(re.findall(r"(?m)^> \*\*", text)),
        "tables": len(re.findall(r"(?m)^\|---", text)),
    }


def load_status():
    if os.path.exists(STATUS):
        with open(STATUS, encoding="utf-8") as handle:
            return json.load(handle)
    return {}


def save_status(status):
    with open(STATUS, "w", encoding="utf-8") as handle:
        json.dump(status, handle, indent=2, ensure_ascii=False, sort_keys=True)
        handle.write("\n")


def compare():
    """{lang: {file: state}} with state in ok, stale, missing, structure:<what>."""
    status = load_status()
    result = {}
    for lang in gl.LANGS:
        if lang == SOURCE_LANG:
            continue
        result[lang] = {}
        for name in chapter_files():
            src_path = os.path.join(CONTENT, SOURCE_LANG, name)
            text = read(lang, name)
            if text is None:
                result[lang][name] = "missing"
                continue
            if "<!-- TRANSLATE -->" in text:
                result[lang][name] = "missing"
                continue
            a, b = structure(read(SOURCE_LANG, name)), structure(text)
            different = [k for k in a if a[k] != b[k]]
            if different:
                result[lang][name] = "structure:" + ",".join(different)
                continue
            recorded = status.get(lang, {}).get(name)
            result[lang][name] = "ok" if recorded == sha(src_path) else "stale"
    return result


def cmd_status(_args):
    result = compare()
    names = chapter_files()
    width = max(len(n) for n in names)
    print("%-*s  %s" % (width, "chapter", "  ".join("%-9s" % l for l in result)))
    for name in names:
        cells = []
        for lang in result:
            state = result[lang][name]
            cells.append("%-9s" % ("ok" if state == "ok" else state.split(":")[0]))
        print("%-*s  %s" % (width, name, "  ".join(cells)))
    bad = sum(1 for lang in result for s in result[lang].values() if s != "ok")
    print("\n%d file(s) need attention" % bad)
    return 0


def cmd_check_translations(_args):
    result = compare()
    problems = 0
    for lang in result:
        for name, state in result[lang].items():
            if state != "ok":
                problems += 1
                print("%s/%s: %s" % (lang, name, state))
    print("OK: every translation is complete and up to date" if not problems else "\n%d problem(s)" % problems)
    return 1 if problems else 0


def cmd_sync_structure(_args):
    created = 0
    for lang in gl.LANGS:
        if lang == SOURCE_LANG:
            continue
        os.makedirs(os.path.join(CONTENT, lang), exist_ok=True)
        for name in chapter_files():
            path = os.path.join(CONTENT, lang, name)
            if os.path.exists(path):
                continue
            text = read(SOURCE_LANG, name)
            # keep the headings, the heading ids and the images; the text is marked to translate
            lines = ["<!-- TRANSLATE -->"]
            for line in text.split("\n"):
                if line.startswith("#") or line.startswith("!["):
                    lines.append(line.replace("screenshots/" + SOURCE_LANG + "/", "screenshots/" + lang + "/"))
            with open(path, "w", encoding="utf-8") as handle:
                handle.write("\n\n".join(lines) + "\n")
            created += 1
            print("created", os.path.relpath(path, gl.GUIDE))
    print("%d skeleton file(s) created" % created)
    return 0


def cmd_mark(args):
    status = load_status()
    langs = [args.lang] if args.lang else [l for l in gl.LANGS if l != SOURCE_LANG]
    names = args.files or chapter_files()
    for lang in langs:
        for name in names:
            if read(lang, name) is None:
                continue
            status.setdefault(lang, {})[name] = sha(os.path.join(CONTENT, SOURCE_LANG, name))
    save_status(status)
    print("recorded the English hashes for", ", ".join(langs))
    return 0


def cmd_check(_args):
    errors = []
    for lang in gl.LANGS:
        strs = gl.strings(lang)
        all_ids = set()
        texts = {}
        for name in chapter_files():
            text = read(lang, name)
            if text is None:
                errors.append("%s/%s: file is missing" % (lang, name))
                continue
            texts[name] = text
            for hid in re.findall(r"\{#([A-Za-z0-9_-]+)[^}]*\}", text):
                if hid in all_ids:
                    errors.append("%s/%s: duplicate heading id %s" % (lang, name, hid))
                all_ids.add(hid)
        for name, text in texts.items():
            for alt, target in re.findall(r"!\[([^\]]*)\]\(([^)]+)\)", text):
                if not alt.strip():
                    errors.append("%s/%s: image without alt text: %s" % (lang, name, target))
                path = os.path.normpath(os.path.join(CONTENT, lang, target))
                if not os.path.exists(path):
                    errors.append("%s/%s: image not found: %s" % (lang, name, target))
            for target in re.findall(r"\]\(#([A-Za-z0-9_-]+)\)", text):
                if target not in all_ids:
                    errors.append("%s/%s: link to a heading that does not exist: #%s" % (lang, name, target))
            for key in re.findall(r"\{\{([a-z0-9_]+)\}\}", text):
                if key not in strs:
                    errors.append("%s/%s: unknown label key {{%s}}" % (lang, name, key))
            if re.search(r"(?m)^(?!\s*\|)(?!\s*<!--).*\b(TODO)\b", text) and "<!-- TODO" not in text:
                errors.append("%s/%s: a TODO outside a comment" % (lang, name))
            if re.search(r"https?://(?!openmrs\.example|login\.example)", text):
                errors.append("%s/%s: a web address in the text (only example addresses are allowed)" % (lang, name))
    for e in errors:
        print(e)
    print("check: %s" % ("%d problem(s)" % len(errors) if errors else "no problems"))
    return 1 if errors else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd")
    sub.add_parser("status")
    sub.add_parser("check-translations")
    sub.add_parser("sync-structure")
    sub.add_parser("check")
    mark = sub.add_parser("mark")
    mark.add_argument("--lang")
    mark.add_argument("files", nargs="*")
    args = parser.parse_args()
    handlers = {"status": cmd_status, "check-translations": cmd_check_translations, "sync-structure": cmd_sync_structure, "mark": cmd_mark, "check": cmd_check}
    if args.cmd not in handlers:
        parser.print_help()
        return 2
    return handlers[args.cmd](args)


if __name__ == "__main__":
    sys.exit(main())
