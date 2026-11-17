"""The Group (patient list) requests of the fake server, answered the way the fhir2extension fork does.

The contract is the one in the fork's README ("Group (patient list) support"): create by PUT with the
client's id (HAPI gives a POSTed resource an id of its own), 422 for a name that is taken, PUT that
leaves the members alone when the Group lists none, DELETE that voids, and the two operations that
add and remove members by what changes.
Reads add the location, the type, the dates and the attributes. The real server could not be reached
when this was written, so this file is the reference of what the app expects; the same requests are
sent by `contract_check.py` to a real server to see that it agrees.
"""
import copy
import time

import data

EXT = "http://fhir.openmrs.org/ext/group/"
DESCRIPTION = EXT + "description"
LOCATION = EXT + "location"
LIST_TYPE = EXT + "cohort-type"
DATE_CREATED = EXT + "date-created"
CHANGED_BY = EXT + "changed-by"
DATE_CHANGED = EXT + "date-changed"
ATTRIBUTE = EXT + "attribute"

# What the seeded lists show beyond their name, so that the screens have attributes to display.
SEED_DETAILS = {
    "morning": ("Clinic list", [("Program", "Primary care"), ("Schedule", "Weekdays, 8:00")]),
    "followup": ("Follow-up", [("Program", "Chronic conditions")]),
    "families": ("Group session", []),
    "riverside-list": ("Clinic list", [("Program", "Outreach")]),
}


def outcome(severity, code, text):
    return {"resourceType": "OperationOutcome", "issue": [{"severity": severity, "code": code, "diagnostics": text}]}


def stamp():
    return time.strftime("%Y-%m-%dT%H:%M:%S+00:00", time.gmtime())


def user_reference(state):
    name = data.USER_DISPLAY[state.lang]
    return {"reference": "Practitioner/" + data.PROVIDER_UUID, "display": name}


def user_identity(state):
    return {"display": data.USER_DISPLAY[state.lang], "identifier": {"value": data.USER_UUID}}


def put_extension(res, url, value_key, value):
    res["extension"] = [e for e in res.get("extension", []) if e.get("url") != url]
    res["extension"].append({"url": url, value_key: value})


def get_extension(res, url):
    for e in res.get("extension", []):
        if e.get("url") == url:
            return e
    return None


def uuid_of(extension):
    """The uuid an extension (a string or a reference) points at."""
    if not extension:
        return None
    raw = extension.get("valueString") or (extension.get("valueReference") or {}).get("reference")
    return raw.strip().split("/")[-1] if raw and raw.strip() else None


def gone(state):
    return state.store.setdefault("GroupGone", {})


def active_groups(state):
    return [g for g in state.store.get("Group", {}).values() if g.get("active", True)]


def present(state, stored):
    """A Group as a read answers it: members and quantity, location, type, dates and attributes."""
    res = copy.deepcopy(stored)
    res["quantity"] = len(res.get("member", []))
    res.setdefault("managingEntity", user_reference(state))
    key = (res.get("identifier") or [{}])[0].get("value")
    type_name, attributes = SEED_DETAILS.get(key, (None, []))
    # The seed names the location with a plain string; a read gives a reference with the name.
    location = get_extension(res, LOCATION)
    if location is not None:
        loc_id = uuid_of(location)
        loc = state.store.get("Location", {}).get(loc_id)
        ref = {"reference": "Location/" + loc_id}
        if loc:
            ref["display"] = loc.get("name")
        put_extension(res, LOCATION, "valueReference", ref)
    if type_name and get_extension(res, LIST_TYPE) is None:
        put_extension(res, LIST_TYPE, "valueReference", {"reference": "CohortType/" + data.uid("cohort-type", type_name), "display": type_name})
    if get_extension(res, DATE_CREATED) is None:
        put_extension(res, DATE_CREATED, "valueDateTime", res.get("meta", {}).get("lastUpdated") or stamp())
    if attributes and not any(e.get("url") == ATTRIBUTE for e in res.get("extension", [])):
        for name, value in attributes:
            res["extension"].append({"url": ATTRIBUTE, "extension": [{"url": "type", "valueString": name}, {"url": "value", "valueString": value}]})
    return res


def trim(res, elements):
    """`_elements=a,b`: only those top-level elements (and the id, the type and the meta)."""
    wanted = set(elements.split(",")) | {"resourceType", "id", "meta"}
    return dict((k, v) for k, v in res.items() if k in wanted)


# ---- requests ------------------------------------------------------------------------------------
def handle(h, state, parts, query):
    """Answers a request on Group/..., or returns False for the plain reads the server already does."""
    method = h.command
    if method == "PATCH":
        h.fhir_reply(400, outcome("error", "invalid", "PATCH is not supported on Group"))
        return True
    if len(parts) == 1:
        return create(h, state) if method == "POST" else False
    rid = parts[1]
    if len(parts) == 3 and parts[2] in ("$add-members", "$remove-members") and method == "POST":
        return change_members(h, state, rid, parts[2] == "$add-members")
    if len(parts) != 2:
        return False
    if method == "GET":
        if rid in gone(state):
            h.fhir_reply(410, outcome("error", "deleted", "Group/" + rid))
            return True
        stored = state.store.get("Group", {}).get(rid)
        if stored is None:
            h.fhir_reply(404, outcome("error", "not-found", "Group/" + rid))
        else:
            h.fhir_reply(200, present(state, stored))
        return True
    if method == "PUT":
        return update(h, state, rid)
    if method == "DELETE":
        return delete(h, state, rid)
    return False


def member_ids(res):
    return [m["entity"]["reference"].split("/")[-1] for m in res.get("member", [])]


def check_members(h, state, res):
    """The patients the Group names must exist (422 otherwise); returns their ids or None."""
    ids = member_ids(res)
    unknown = [i for i in ids if i not in state.store.get("Patient", {})]
    if unknown:
        h.fhir_reply(422, outcome("error", "processing", "Unknown patient " + unknown[0]))
        return None
    return ids


def name_taken(state, name, own_id=None):
    wanted = (name or "").strip().lower()
    return any((g.get("name") or "").strip().lower() == wanted and g["id"] != own_id for g in active_groups(state))


def create(h, state):
    """POST Group: the server gives the id (whatever the body says), as HAPI does."""
    res = h.read_json()
    if not isinstance(res, dict) or res.get("resourceType") != "Group":
        h.fhir_reply(400, outcome("error", "invalid", "A resource of type Group must be supplied"))
        return True
    state.counter += 1
    return create_with_id(h, state, data.uid("group-new", "%s-%s" % (time.time(), state.counter)), res)


def create_with_id(h, state, rid, res):
    """Makes the patient list `rid`: a POST (id of the server) or a PUT of an id that is not there (the client's)."""
    if not (res.get("name") or "").strip():
        h.fhir_reply(422, outcome("error", "required", "A name is required"))
        return True
    with state.lock:
        if rid in state.store.get("Group", {}) or rid in gone(state):
            h.fhir_reply(409, outcome("error", "conflict", "A Group with ID %s exists already" % rid))
            return True
        if name_taken(state, res["name"]):
            h.fhir_reply(422, outcome("error", "processing", "A Group with this name exists already"))
            return True
        if check_members(h, state, res) is None:
            return True
        stored = {
            "resourceType": "Group",
            "id": rid,
            "meta": {"versionId": "1", "lastUpdated": stamp()},
            "active": True,
            "type": "person",
            "actual": True,
            "name": res["name"].strip(),
            "extension": [],
            "managingEntity": user_reference(state),
            "member": [{"entity": {"reference": "Patient/" + i}} for i in dict.fromkeys(member_ids(res))],
        }
        copy_attributes(res, stored)
        put_extension(stored, DATE_CREATED, "valueDateTime", stamp())
        stored["quantity"] = len(stored["member"])
        state.store.setdefault("Group", {})[rid] = stored
        state.uploads.append({"resourceType": "Group", "id": rid, "op": "create"})
    h.fhir_reply(201, present(state, stored), extra={"Location": "%s/Group/%s" % (h.base_url(), rid)})
    return True


def copy_attributes(res, stored):
    """What a Group says about the patient list itself; whatever it does not mention stays."""
    if (res.get("name") or "").strip():
        stored["name"] = res["name"].strip()
    description = get_extension(res, DESCRIPTION)
    if description is not None:
        put_extension(stored, DESCRIPTION, "valueString", description.get("valueString", ""))
    for url, prefix in ((LOCATION, "Location/"), (LIST_TYPE, "CohortType/")):
        uuid = uuid_of(get_extension(res, url))
        if uuid:
            put_extension(stored, url, "valueReference", {"reference": prefix + uuid})


def update(h, state, rid):
    res = h.read_json()
    if not isinstance(res, dict) or res.get("id") != rid:
        h.fhir_reply(400, outcome("error", "invalid", "Group id does not match resource id"))
        return True
    if rid not in state.store.get("Group", {}) and rid not in gone(state):
        return create_with_id(h, state, rid, res)  # update as create
    with state.lock:
        stored = state.store.get("Group", {}).get(rid)
        if stored is None:
            h.fhir_reply(409, outcome("error", "conflict", "Group/%s was deleted" % rid))
            return True
        if "name" in res and name_taken(state, res["name"], rid):
            h.fhir_reply(422, outcome("error", "processing", "A Group with this name exists already"))
            return True
        if "name" in res and not (res["name"] or "").strip():
            h.fhir_reply(422, outcome("error", "required", "A name is required"))
            return True
        if res.get("active") is False:
            state.store["Group"].pop(rid, None)
            gone(state)[rid] = True
            state.uploads.append({"resourceType": "Group", "id": rid, "op": "void"})
            h.fhir_reply(200, res)
            return True
        if "member" in res:
            ids = check_members(h, state, res)
            if ids is None:
                return True
            stored["member"] = [{"entity": {"reference": "Patient/" + i}} for i in dict.fromkeys(ids)]
        copy_attributes(res, stored)
        put_extension(stored, CHANGED_BY, "valueReference", user_identity(state))
        put_extension(stored, DATE_CHANGED, "valueDateTime", stamp())
        touch(stored)
        state.uploads.append({"resourceType": "Group", "id": rid, "op": "update"})
    h.fhir_reply(200, present(state, stored))
    return True


def touch(stored):
    meta = stored.setdefault("meta", {})
    meta["versionId"] = str(int(meta.get("versionId", "0")) + 1) if str(meta.get("versionId", "0")).isdigit() else "1"
    meta["lastUpdated"] = stamp()


def delete(h, state, rid):
    with state.lock:
        if state.store.get("Group", {}).pop(rid, None) is None:
            h.fhir_reply(404, outcome("error", "not-found", "Group/" + rid))
            return True
        gone(state)[rid] = True
        state.uploads.append({"resourceType": "Group", "id": rid, "op": "delete"})
    h.fhir_reply(200, outcome("information", "informational", "Successfully deleted"))
    return True


def change_members(h, state, rid, adding):
    body = h.read_json() or {}
    wanted = []
    for p in body.get("parameter", []) if isinstance(body, dict) else []:
        ref = (p.get("valueReference") or {}).get("reference", "") if p.get("name") == "patient" else None
        if ref is None:
            continue
        kind, _, uuid = ref.partition("/")
        if kind != "Patient" or not uuid:
            h.fhir_reply(400, outcome("error", "invalid", "Every patient parameter must be a reference to a Patient (Patient/<uuid>)"))
            return True
        wanted.append(uuid)
    if not wanted:
        h.fhir_reply(400, outcome("error", "invalid", "At least one patient parameter is needed"))
        return True
    with state.lock:
        stored = state.store.get("Group", {}).get(rid)
        if stored is None:
            h.fhir_reply(404, outcome("error", "not-found", "Group/" + rid))
            return True
        members = member_ids(stored)
        if adding:
            unknown = [i for i in wanted if i not in state.store.get("Patient", {})]
            if unknown:
                h.fhir_reply(422, outcome("error", "processing", "Unknown patient " + unknown[0]))
                return True
            for uuid in dict.fromkeys(wanted):
                if uuid not in members:
                    members.append(uuid)
        else:
            members = [m for m in members if m not in set(wanted)]
        stored["member"] = [{"entity": {"reference": "Patient/" + i}} for i in members]
        put_extension(stored, CHANGED_BY, "valueReference", user_identity(state))
        put_extension(stored, DATE_CHANGED, "valueDateTime", stamp())
        touch(stored)
        state.uploads.append({"resourceType": "Group", "id": rid, "op": "add-members" if adding else "remove-members", "patients": wanted})
    h.fhir_reply(200, present(state, stored))
    return True
