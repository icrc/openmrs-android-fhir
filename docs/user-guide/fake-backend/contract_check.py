"""Checks the Group (patient list) contract of the fhir2extension fork against a server.

    python contract_check.py                                   # the fake server on port 8089
    python contract_check.py --base http://host:8080/openmrs/ws/fhir2/R4 --user USER --password PASSWORD

It creates a patient list with a random name, changes it and deletes it, and leaves nothing behind (a deleted
patient list is voided on a real server). It needs two patients on the server. The requests are the ones the
app sends, so a server that passes is a server the app can use. Standard library only.
"""
import argparse
import base64
import json
import sys
import urllib.error
import urllib.request
import uuid

EXT = "http://fhir.openmrs.org/ext/group/"


class Client(object):
    def __init__(self, base, user, password):
        self.base = base.rstrip("/")
        self.auth = "Basic " + base64.b64encode(("%s:%s" % (user, password)).encode()).decode()

    def call(self, method, path, body=None):
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(self.base + "/" + path, data=data, method=method)
        request.add_header("Authorization", self.auth)
        request.add_header("Accept", "application/fhir+json")
        if data is not None:
            request.add_header("Content-Type", "application/fhir+json")
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                raw = response.read()
                return response.status, (json.loads(raw.decode("utf-8")) if raw else None)
        except urllib.error.HTTPError as error:
            raw = error.read()
            try:
                return error.code, json.loads(raw.decode("utf-8"))
            except ValueError:
                return error.code, None


def parameters(*patients):
    return {"resourceType": "Parameters", "parameter": [{"name": "patient", "valueReference": {"reference": "Patient/" + p}} for p in patients]}


def members(group):
    return sorted(m["entity"]["reference"].split("/")[-1] for m in group.get("member", []))


def ext(group, url):
    return next((e for e in group.get("extension", []) if e.get("url") == url), None)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8089/openmrs/ws/fhir2/R4")
    parser.add_argument("--user", default="demo.agent")
    parser.add_argument("--password", default="demo1234")
    args = parser.parse_args()
    api = Client(args.base, args.user, args.password)
    failures = []

    def check(label, ok, detail=""):
        print(("  ok    " if ok else "  FAIL  ") + label + ("" if ok else "  " + str(detail)))
        if not ok:
            failures.append(label)

    status, bundle = api.call("GET", "Patient?_count=3&_elements=id")
    patients = [e["resource"]["id"] for e in (bundle or {}).get("entry", [])]
    if status != 200 or len(patients) < 2:
        print("Need two patients on the server (got %s, status %s)" % (len(patients), status))
        return 2
    a, b = patients[0], patients[1]
    gid = str(uuid.uuid4())
    name = "Contract check " + gid[:8]

    group = {"resourceType": "Group", "id": gid, "type": "person", "actual": True, "active": True, "name": name,
             "extension": [{"url": EXT + "description", "valueString": "made by contract_check.py"}]}
    # The app creates with PUT: HAPI gives a POSTed resource an id of its own, so only a PUT keeps the one the
    # device chose (its queued member changes carry it).
    status, created = api.call("PUT", "Group/" + gid, group)
    check("PUT of an id the server lacks creates it with that id", status in (200, 201) and (created or {}).get("id") == gid, status)
    status, _ = api.call("PUT", "Group/" + gid, group)
    check("the same PUT again is an update (200)", status == 200, status)
    other = dict(group, id=str(uuid.uuid4()))
    status, _ = api.call("PUT", "Group/" + other["id"], other)
    check("a name that is taken answers 422", status == 422, status)

    status, read = api.call("GET", "Group/" + gid)
    check("GET Group/id", status == 200 and read.get("name") == name, status)
    check("the creator is the managing entity", bool(read and read.get("managingEntity")))
    check("the creation date is there", bool(read and ext(read, EXT + "date-created")))
    check("a new Group has no member and quantity 0", read is not None and not read.get("member") and read.get("quantity", 0) == 0)

    status, after = api.call("POST", "Group/%s/$add-members" % gid, parameters(a, b))
    check("$add-members adds two (200)", status == 200 and members(after or {}) == sorted([a, b]), status)
    status, after = api.call("POST", "Group/%s/$add-members" % gid, parameters(a))
    check("adding a member again changes nothing", status == 200 and members(after or {}) == sorted([a, b]), status)
    status, _ = api.call("POST", "Group/%s/$add-members" % gid, parameters(a, "00000000-0000-0000-0000-000000000000"))
    check("an unknown patient answers 422", status == 422, status)
    status, _ = api.call("POST", "Group/%s/$add-members" % gid, {"resourceType": "Parameters", "parameter": []})
    check("no patient answers 400", status == 400, status)
    status, _ = api.call("POST", "Group/%s/$add-members" % str(uuid.uuid4()), parameters(a))
    check("an unknown group answers 404", status == 404, status)

    status, after = api.call("POST", "Group/%s/$remove-members" % gid, parameters(a))
    check("$remove-members removes one", status == 200 and members(after or {}) == [b], status)
    status, after = api.call("POST", "Group/%s/$remove-members" % gid, parameters(a))
    check("removing a non-member is ignored", status == 200 and members(after or {}) == [b], status)

    renamed = dict(read, name=name + " renamed")
    renamed.pop("member", None)
    renamed.pop("quantity", None)
    status, updated = api.call("PUT", "Group/" + gid, renamed)
    check("PUT renames it (200)", status == 200 and (updated or {}).get("name") == renamed["name"], status)
    status, read = api.call("GET", "Group/" + gid)
    check("a PUT without members keeps the members", members(read or {}) == [b], members(read or {}))
    check("the last change is shown", bool(read and ext(read, EXT + "date-changed")))

    status, found = api.call("GET", "Group?_elements=id,name,quantity,extension,managingEntity,meta&_count=100")
    entries = [e["resource"] for e in (found or {}).get("entry", [])]
    mine = next((g for g in entries if g.get("id") == gid), None)
    check("the search finds it, light", status == 200 and mine is not None and "member" not in mine, status)
    check("the search counts the active members", mine is not None and mine.get("quantity") == 1, mine and mine.get("quantity"))

    status, _ = api.call("PATCH", "Group/" + gid, {"resourceType": "Parameters"})
    check("PATCH is refused", status in (400, 405, 415), status)

    status, _ = api.call("DELETE", "Group/" + gid)
    check("DELETE removes it", status in (200, 204), status)
    status, _ = api.call("GET", "Group/" + gid)
    check("a deleted Group is gone (404 or 410)", status in (404, 410), status)
    status, _ = api.call("DELETE", "Group/" + gid)
    check("deleting it again answers 404 or 410", status in (404, 410), status)

    print("\n%s" % ("All checks passed." if not failures else "%d check(s) failed." % len(failures)))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
