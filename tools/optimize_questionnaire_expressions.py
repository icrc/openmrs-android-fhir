#!/usr/bin/env python3
"""Makes the score variables of the bundled questionnaires cheap to evaluate, with the same results.

    python tools/optimize_questionnaire_expressions.py core/src/main/assets/questionnaires

The scale forms (GAD-7, PHQ-9, ProQOL, ...) turn each answer into points with one FHIRPath variable
per question, written as a chain of tests that each walk the whole response again:

    iif(%resource.repeat(item).where(linkId='q1').answer.first().value.code.empty(), 0,
      iif(%resource.repeat(item).where(linkId='q1').answer.first().value.code='A', 0,
        iif(%resource.repeat(item).where(linkId='q1').answer.first().value.code='B', 1, 0)))

The data-capture library re-evaluates every variable a total depends on each time an answer
changes, on the main thread. `repeat(item)` collects every item of the response and removes the
duplicates by comparing them one by one, and the chain does that up to five times per variable, so
one tap on a 30 question form was a visible stall. The rewritten variable reads the answer once and
goes straight to the item through its place in the form:

    %resource.item.item.item.item.item.where(linkId='q1').answer.first().value.code
      .select(iif($this='A', 0, iif($this='B', 1, 0))).combine(0).first()

Same result for every answer: no answer gives the first number (0), a known answer its points, any
other code the last number. Only the expression text changes, so what is extracted and saved is
untouched. Variables that do not have the exact shape above are left as they are, and a variable is
only rewritten when its question exists once in the form; one whose question is not in the form at all
is always its first number and becomes just that number. Files are rewritten in the compact form
they already have; running the script twice changes nothing.
"""
import glob
import json
import os
import re
import sys
from collections import OrderedDict

VARIABLE_URL_SUFFIX = "/variable"


def items_with_depth(items, depth=1):
    for item in items:
        yield item, depth
        yield from items_with_depth(item.get("item", []), depth + 1)


def parse_chain(expression):
    """`(link_id, first_value, [(code, points), ...], last_value)` of an old style variable, or None."""
    m = re.match(
        r"^iif\(%resource\.repeat\(item\)\.where\(linkId='([^']+)'\)\.answer\.first\(\)\.value\.code\.empty\(\), (-?\d+), ",
        expression,
    )
    if not m:
        return None
    link_id, first_value = m.group(1), m.group(2)
    rest = expression[m.end():]
    test = "iif(%%resource.repeat(item).where(linkId='%s').answer.first().value.code='" % link_id
    pairs = []
    while rest.startswith(test):
        rest = rest[len(test):]
        m = re.match(r"([^']*)', (-?\d+), ", rest)
        if not m:
            return None
        pairs.append((m.group(1), m.group(2)))
        rest = rest[m.end():]
    m = re.match(r"^(-?\d+)(\)+)$", rest)
    if not m or not pairs:
        return None
    # One closing parenthesis for the empty test and one for each comparison.
    if len(m.group(2)) != len(pairs) + 1:
        return None
    return link_id, first_value, pairs, m.group(1)


def rewrite(link_id, first_value, pairs, last_value, depth):
    chain = last_value
    for code, points in reversed(pairs):
        chain = "iif($this='%s', %s, %s)" % (code, points, chain)
    return "%%resource.%s.where(linkId='%s').answer.first().value.code.select(%s).combine(%s).first()" % (
        ".".join(["item"] * depth),
        link_id,
        chain,
        first_value,
    )


def optimize(questionnaire):
    depth_of = {}
    duplicated = set()
    for item, depth in items_with_depth(questionnaire.get("item", [])):
        link_id = item.get("linkId")
        if link_id in depth_of:
            duplicated.add(link_id)
        depth_of[link_id] = depth
    changed = 0
    for ext in questionnaire.get("extension", []):
        if not ext.get("url", "").endswith(VARIABLE_URL_SUFFIX):
            continue
        value = ext.get("valueExpression")
        if not value or value.get("language") != "text/fhirpath":
            continue
        parsed = parse_chain(value["expression"])
        if parsed is None:
            continue
        link_id, first_value, pairs, last_value = parsed
        if link_id in duplicated:
            continue
        if link_id not in depth_of:
            # The question is not in the form (a leftover of a longer version): the answer is always
            # missing, so the variable is always its first number.
            value["expression"] = first_value
        else:
            value["expression"] = rewrite(link_id, first_value, pairs, last_value, depth_of[link_id])
        changed += 1
    return changed


def main(folder):
    total = 0
    for path in sorted(glob.glob(os.path.join(folder, "*.json"))):
        text = open(path, encoding="utf8").read()
        questionnaire = json.loads(text, object_pairs_hook=OrderedDict)
        if json.dumps(questionnaire, ensure_ascii=False, separators=(",", ":")) != text:
            print("skipped (not written the way this script writes it): " + path)
            continue
        changed = optimize(questionnaire)
        if not changed:
            continue
        out = json.dumps(questionnaire, ensure_ascii=False, separators=(",", ":"))
        before = len(text.encode("utf8"))
        open(path, "w", encoding="utf8", newline="\n").write(out)
        total += changed
        print("%s: %d variables, %d -> %d bytes" % (os.path.basename(path), changed, before, len(out.encode("utf8"))))
    print("%d variables rewritten" % total)


if __name__ == "__main__":
    main(sys.argv[1])
