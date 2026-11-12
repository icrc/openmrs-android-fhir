"""A fake OpenMRS server for the user-guide screenshots. Standard library only (Python 3.7+).

It answers what the app asks for (see backend-contract.md) with the fictional data of data.py. It
has no connection to any real server.

    python server.py --port 8089

Control (no authentication, for the screenshot scripts):

    GET  /__control/state
    POST /__control/lang?code=fr          re-seeds the data in that language
    POST /__control/scenario?name=empty   default | empty | error | validation | slow | offline
    POST /__control/reset                 forget what was uploaded, back to the seed
    GET  /__control/log                   the last requests, with the ones nobody answered
"""
import argparse
import base64
import json
import re
import socket
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from socketserver import ThreadingMixIn
from urllib.parse import parse_qs, urlparse, urlencode

import data

PREFIX = "/openmrs"
FHIR = PREFIX + "/ws/fhir2/R4"
REST = PREFIX + "/ws/rest/v1"
SCENARIOS = ("default", "empty", "error", "validation", "slow", "offline")
SLOW_SECONDS = 4.0
PAGE = 50


class State(object):
    def __init__(self):
        self.lock = threading.RLock()
        self.lang = "en"
        self.scenario = "default"
        self.session_location = None
        self.counter = 0
        self.log = []
        self.unhandled = []
        self.store = {}
        self.uploads = []
        self.seed()

    def seed(self):
        with self.lock:
            lang = self.lang
            store = {"Location": {}, "Group": {}, "Patient": {}, "Encounter": {}, "Observation": {}}
            for i, (key, _names) in enumerate(data.LOCATIONS):
                res = data.location_resource(key, lang, i)
                store["Location"][res["id"]] = res
            for i, g in enumerate(data.GROUPS):
                res = data.group_resource(g[0], lang, i)
                store["Group"][res["id"]] = res
            for i in range(len(data.PATIENTS)):
                res = data.patient_resource(i, lang)
                store["Patient"][res["id"]] = res
            for kind, items in load_fixtures().items():
                for res in items:
                    store.setdefault(kind, {})[res["id"]] = res
            self.store = store
            self.uploads = []
            self.counter = 0

    def next_identifier(self):
        with self.lock:
            self.counter += 1
            return data.identifier_value(500 + self.counter)


def load_fixtures():
    """Encounters and observations captured from the app (fixtures/*.json), if there are any."""
    import os

    out = {}
    folder = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixtures")
    if not os.path.isdir(folder):
        return out
    for name in sorted(os.listdir(folder)):
        if not name.endswith(".json"):
            continue
        with open(os.path.join(folder, name), encoding="utf-8") as handle:
            doc = json.load(handle)
        for res in doc if isinstance(doc, list) else doc.get("entry", []):
            res = res.get("resource", res)
            out.setdefault(res["resourceType"], []).append(res)
    return out


STATE = State()


SSO_CODE = "demo-authorization-code"

SSO_TEXT = {
    "en": {"title": "Sign in", "heading": "Sign in to your organisation", "user": "Username", "password": "Password", "button": "Sign in", "note": "Demo identity provider. Fictional, for the user guide.", "error": "Invalid username or password."},
    "fr": {"title": "Connexion", "heading": "Connectez-vous à votre organisation", "user": "Nom d'utilisateur", "password": "Mot de passe", "button": "Se connecter", "note": "Fournisseur d'identité de démonstration. Fictif, pour le guide.", "error": "Nom d'utilisateur ou mot de passe incorrect."},
    "es": {"title": "Iniciar sesión", "heading": "Inicie sesión en su organización", "user": "Nombre de usuario", "password": "Contraseña", "button": "Iniciar sesión", "note": "Proveedor de identidad de demostración. Ficticio, para la guía.", "error": "Nombre de usuario o contraseña incorrectos."},
    "ru": {"title": "Вход", "heading": "Войдите в свою организацию", "user": "Имя пользователя", "password": "Пароль", "button": "Войти", "note": "Демонстрационный поставщик входа. Вымышленный, для руководства.", "error": "Неверное имя пользователя или пароль."},
    "ar": {"title": "تسجيل الدخول", "heading": "سجّل الدخول إلى مؤسستك", "user": "اسم المستخدم", "password": "كلمة المرور", "button": "تسجيل الدخول", "note": "مزوّد هوية تجريبي. خيالي، لدليل المستخدم.", "error": "اسم المستخدم أو كلمة المرور غير صحيحة."},
}

SSO_HTML = """<!doctype html><html lang="%(lang)s"%(rtl)s><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>%(title)s</title><style>
body{font-family:Roboto,Arial,sans-serif;background:#f3f6f6;margin:0;color:#1d2b2b}
.top{background:#005f5f;color:#fff;padding:36px 24px;text-align:center;font-size:30px;font-weight:600}
.card{max-width:560px;margin:-18px auto 0;background:#fff;border-radius:18px;padding:36px;box-shadow:0 4px 18px rgba(0,0,0,.15)}
h1{font-size:26px;margin:0 0 24px}label{display:block;font-size:20px;margin:18px 0 6px}
input{width:100%%;box-sizing:border-box;font-size:24px;padding:16px;border:2px solid #9aa9a9;border-radius:10px}
button{margin-top:28px;width:100%%;font-size:24px;padding:18px;background:#005f5f;color:#fff;border:0;border-radius:12px}
.note{max-width:560px;margin:18px auto;text-align:center;color:#667;font-size:18px}.err{color:#b3261e;font-size:20px}
</style></head><body><div class="top">OpenMRS</div><div class="card"><h1>%(heading)s</h1>%(error)s
<form method="post" action="%(action)s"><label>%(user)s</label><input name="username" autocomplete="username">
<label>%(password)s</label><input name="password" type="password" autocomplete="current-password">
<button type="submit">%(button)s</button></form></div><p class="note">%(note)s</p></body></html>"""


def b64url(raw):
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def make_sso_token():
    """An unsigned demo token: only the fake server reads it."""
    payload = {"iss": "demo", "sub": data.USER_UUID, "preferred_username": data.DEMO_USER, "exp": 1893456000, "demo": True}
    return "eyJhbGciOiJub25lIn0." + b64url(json.dumps(payload).encode("utf-8")) + ".demo"


def sso_token_valid(token):
    try:
        part = token.split(".")[1]
        payload = json.loads(base64.urlsafe_b64decode(part + "=" * (-len(part) % 4)).decode("utf-8"))
    except Exception:
        return False
    return payload.get("preferred_username") == data.DEMO_USER and payload.get("demo") is True


def outcome(severity, code, text):
    return {"resourceType": "OperationOutcome", "issue": [{"severity": severity, "code": code, "diagnostics": text}]}


def norm_time(value):
    """Times compare as text once they share a format: 2026-10-12T08:00:00 (UTC)."""
    return value.replace("Z", "").split("+")[0].split(".")[0].replace(" ", "+")


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "FakeOpenMRS/1.0"

    def log_message(self, fmt, *args):
        pass

    # ---- plumbing ------------------------------------------------------------------------------
    _status = None

    def reply(self, status, body, ctype="application/json", extra=None):
        self._status = status if status >= 400 else None
        raw = b"" if body is None else (body if isinstance(body, bytes) else json.dumps(body, ensure_ascii=False).encode("utf-8"))
        self.send_response(status)
        self.send_header("Content-Type", ctype + "; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(raw)

    def fhir_reply(self, status, body, extra=None):
        self.reply(status, body, "application/fhir+json", extra)

    def read_json(self):
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length else b""
        if not raw:
            return None
        try:
            return json.loads(raw.decode("utf-8"))
        except ValueError:
            return None

    def authorized(self):
        header = self.headers.get("Authorization") or ""
        # The SDK sends "Basic <credentials>"; the app's own requests (ApiManager) send the same
        # encoded credentials under the "Bearer" scheme, so both are accepted.
        scheme, _, token = header.partition(" ")
        if scheme not in ("Basic", "Bearer"):
            return False
        if scheme == "Bearer" and token.startswith("eyJ"):
            return sso_token_valid(token)
        try:
            user, _, password = base64.b64decode(token).decode("utf-8").partition(":")
        except Exception:
            return False
        return user == data.DEMO_USER and password == data.DEMO_PASSWORD

    def base_url(self):
        return "http://%s%s" % (self.headers.get("Host") or "localhost", FHIR)

    # ---- entry points --------------------------------------------------------------------------
    def do_GET(self):
        self.dispatch()

    def do_POST(self):
        self.dispatch()

    def do_PUT(self):
        self.dispatch()

    def do_DELETE(self):
        self.dispatch()

    def do_PATCH(self):
        self.dispatch()

    def dispatch(self):
        parsed = urlparse(self.path)
        path = parsed.path
        query = parse_qs(parsed.query, keep_blank_values=True)
        header = self.headers.get("Authorization") or ""
        auth = "none" if not header else ("ok" if self.authorized() else "bad:" + header.split(" ")[0])
        entry = {"t": time.strftime("%H:%M:%S"), "method": self.command, "path": path, "query": parsed.query[:200], "auth": auth, "status": None}
        handled = True
        try:
            if path.startswith("/__control"):
                self.control(path, query)
                entry["status"] = "control"
                return
            if path.startswith("/realms/"):
                self.oidc(path, query)
                entry["status"] = "oidc"
                return
            scenario = STATE.scenario
            if scenario == "offline":
                # Refused like a server that cannot be reached.
                self.close_connection = True
                try:
                    self.connection.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
                entry["status"] = "dropped"
                return
            if scenario == "slow":
                time.sleep(SLOW_SECONDS)
            if path.startswith(REST):
                handled = self.rest(path[len(REST):].strip("/"), query)
            elif path.startswith(FHIR):
                handled = self.fhir(path[len(FHIR):].strip("/"), query)
            else:
                handled = False
            if not handled:
                self.fhir_reply(404, outcome("error", "not-found", "Not handled by the fake backend: " + path))
            entry["status"] = (self._status or "ok") if handled else 404
        except (BrokenPipeError, ConnectionResetError):
            entry["status"] = "closed"
        finally:
            with STATE.lock:
                STATE.log.append(entry)
                del STATE.log[:-400]
                if entry["status"] == 404:
                    STATE.unhandled.append(entry)
            sys.stdout.write("%s %s %s%s -> %s\n" % (entry["t"], self.command, path, ("?" + parsed.query[:90]) if parsed.query else "", entry["status"]))
            sys.stdout.flush()

    # ---- control -------------------------------------------------------------------------------
    def control(self, path, query):
        name = path.rsplit("/", 1)[-1]
        if name == "lang":
            code = (query.get("code") or ["en"])[0]
            if code not in data.LANGS:
                return self.reply(400, {"error": "unknown language"})
            STATE.lang = code
            STATE.session_location = None
            STATE.seed()
        elif name == "scenario":
            value = (query.get("name") or ["default"])[0]
            if value not in SCENARIOS:
                return self.reply(400, {"error": "unknown scenario", "known": SCENARIOS})
            STATE.scenario = value
        elif name == "reset":
            STATE.session_location = None
            STATE.seed()
        elif name == "shutdown":
            self.reply(200, {"bye": True})
            threading.Thread(target=self.server.shutdown).start()
            return
        elif name == "log":
            return self.reply(200, {"unhandled": STATE.unhandled[-50:], "last": STATE.log[-80:]})
        elif name == "uploads":
            return self.reply(200, STATE.uploads)
        self.reply(200, {"lang": STATE.lang, "scenario": STATE.scenario, "patients": len(STATE.store["Patient"]), "uploads": len(STATE.uploads)})

    # ---- fictional single sign-on provider (OpenID Connect) ---------------------------------------
    def oidc(self, path, query):
        base = "http://%s/realms/main" % (self.headers.get("Host") or "localhost")
        route = path[len("/realms/main"):]
        if route == "/.well-known/openid-configuration":
            return self.reply(200, {
                "issuer": base,
                "authorization_endpoint": base + "/protocol/openid-connect/auth",
                "token_endpoint": base + "/protocol/openid-connect/token",
                "end_session_endpoint": base + "/protocol/openid-connect/logout",
                "userinfo_endpoint": base + "/protocol/openid-connect/userinfo",
                "jwks_uri": base + "/protocol/openid-connect/certs",
                "response_types_supported": ["code"],
                "subject_types_supported": ["public"],
                "id_token_signing_alg_values_supported": ["RS256"],
                "grant_types_supported": ["authorization_code", "refresh_token"],
                "token_endpoint_auth_methods_supported": ["none"],
                "code_challenge_methods_supported": ["S256"],
            })
        if route == "/protocol/openid-connect/certs":
            return self.reply(200, {"keys": []})
        if route == "/protocol/openid-connect/token" and self.command == "POST":
            length = int(self.headers.get("Content-Length") or 0)
            form = parse_qs(self.rfile.read(length).decode("utf-8")) if length else {}
            grant = (form.get("grant_type") or [""])[0]
            if grant == "authorization_code" and (form.get("code") or [""])[0] != SSO_CODE:
                return self.reply(400, {"error": "invalid_grant"})
            return self.reply(200, {
                "access_token": make_sso_token(),
                "refresh_token": "demo-refresh-token",
                "token_type": "Bearer",
                "expires_in": 28800,
                "refresh_expires_in": 86400,
                "scope": "openid",
            })
        if route == "/protocol/openid-connect/userinfo":
            return self.reply(200, {"sub": data.USER_UUID, "preferred_username": data.DEMO_USER, "name": data.USER_DISPLAY[STATE.lang]})
        if route == "/protocol/openid-connect/logout":
            return self.reply(200, "<html><body>Signed out</body></html>".encode("utf-8"), "text/html")
        if route == "/protocol/openid-connect/auth":
            return self.sso_page(query)
        return self.reply(404, {"error": "unknown"})

    def sso_page(self, query, error=False):
        redirect = (query.get("redirect_uri") or [""])[0]
        state = (query.get("state") or [""])[0]
        if self.command == "POST":
            length = int(self.headers.get("Content-Length") or 0)
            form = parse_qs(self.rfile.read(length).decode("utf-8")) if length else {}
            if (form.get("username") or [""])[0] == data.DEMO_USER and (form.get("password") or [""])[0] == data.DEMO_PASSWORD:
                sep = "&" if "?" in redirect else "?"
                target = "%s%scode=%s&state=%s&session_state=demo" % (redirect, sep, SSO_CODE, state)
                self.send_response(302)
                self.send_header("Location", target)
                self.send_header("Content-Length", "0")
                self.end_headers()
                return
            error = True
        text = SSO_TEXT[STATE.lang]
        rtl = ' dir="rtl"' if STATE.lang == "ar" else ""
        action = "/realms/main/protocol/openid-connect/auth?" + urlencode(dict((k, v[0]) for k, v in query.items()))
        page = SSO_HTML % {
            "lang": STATE.lang, "rtl": rtl, "title": text["title"], "heading": text["heading"], "user": text["user"],
            "password": text["password"], "button": text["button"], "note": text["note"], "action": action.replace("&", "&amp;"),
            "error": ('<p class="err">%s</p>' % text["error"]) if error else "",
        }
        return self.reply(200, page.encode("utf-8"), "text/html")

    # ---- REST (/ws/rest/v1) --------------------------------------------------------------------
    def rest(self, route, query):
        if route == "session":
            if self.command == "POST":
                body = self.read_json() or {}
                if not self.authorized():
                    return self.reply(401, {"error": {"message": "Unauthorized"}}) or True
                STATE.session_location = body.get("sessionLocation")
                return self.session_reply() or True
            return self.session_reply() or True
        if not self.authorized():
            self.reply(401, {"error": {"message": "User is not logged in"}})
            return True
        if STATE.scenario == "error":
            self.reply(500, {"error": {"message": "Demo server error", "code": "demo"}})
            return True
        if route.startswith("provider/"):
            pid = route.split("/", 1)[1]
            if pid == data.PROVIDER_UUID:
                self.reply(200, {"uuid": pid, "display": data.USER_DISPLAY[STATE.lang]})
            else:
                self.reply(404, {"error": {"message": "Object with given uuid doesn't exist"}})
            return True
        if route.startswith("idgen/autogenerationoption"):
            self.reply(
                200,
                {
                    "results": [
                        {
                            "uuid": data.uid("autogen", "openmrs-id"),
                            "display": data.IDENTIFIER_TYPE_NAME,
                            "identifierType": {"uuid": data.IDENTIFIER_TYPE_UUID, "display": data.IDENTIFIER_TYPE_NAME, "required": True},
                            "manualEntryEnabled": False,
                            "automaticGenerationEnabled": True,
                            "source": {"uuid": data.IDENTIFIER_SOURCE_UUID, "display": "Demo ID source"},
                        }
                    ]
                },
            )
            return True
        m = re.match(r"idgen/identifiersource/([^/]+)/identifier$", route)
        if m and self.command == "POST":
            self.read_json()
            self.reply(201, {"identifier": STATE.next_identifier()})
            return True
        return False

    def session_reply(self):
        if not self.authorized():
            return self.reply(200, {"authenticated": False})
        loc = None
        if STATE.session_location:
            for res in STATE.store["Location"].values():
                if res["id"] == STATE.session_location:
                    loc = {"uuid": res["id"], "display": res["name"]}
        lang = STATE.lang
        body = {
            "sessionId": "DEMO-SESSION",
            "authenticated": True,
            "user": {
                "uuid": data.USER_UUID,
                "display": data.DEMO_USER,
                "username": data.DEMO_USER,
                "person": {"uuid": data.PERSON_UUID, "display": data.USER_DISPLAY[lang]},
            },
            "currentProvider": {"uuid": data.PROVIDER_UUID, "display": data.USER_DISPLAY[lang]},
            "sessionLocation": loc,
            "locale": "en",
            "allowedLocales": ["en"],
        }
        return self.reply(200, body, extra={"Set-Cookie": "JSESSIONID=DEMOSESSION; Path=/openmrs; HttpOnly"})

    # ---- FHIR (/ws/fhir2/R4) -------------------------------------------------------------------
    def fhir(self, route, query):
        if not self.authorized():
            self.fhir_reply(401, outcome("error", "login", "Not authenticated"))
            return True
        if STATE.scenario == "error":
            self.fhir_reply(500, outcome("fatal", "exception", "Demo server error"))
            return True
        if route == "" and self.command == "POST":
            return self.transaction()
        if route == "metadata":
            self.fhir_reply(200, {"resourceType": "CapabilityStatement", "status": "active", "kind": "instance", "fhirVersion": "4.0.1", "format": ["json"]})
            return True
        parts = route.split("/")
        kind = parts[0]
        if kind not in ("Location", "Group", "Patient", "Encounter", "Observation", "Condition", "Questionnaire", "Practitioner", "RiskAssessment", "Person", "Appointment", "Medication", "MedicationRequest", "AllergyIntolerance", "Procedure", "DiagnosticReport", "ServiceRequest"):
            return False
        if len(parts) == 1 and self.command == "GET":
            return self.search(kind, query)
        if len(parts) == 2:
            rid = parts[1]
            if self.command == "GET":
                res = STATE.store.get(kind, {}).get(rid)
                if res is None:
                    self.fhir_reply(404, outcome("error", "not-found", kind + "/" + rid))
                else:
                    self.fhir_reply(200, res)
                return True
            if self.command in ("PUT", "POST"):
                res = self.read_json()
                if res is None:
                    self.fhir_reply(400, outcome("error", "invalid", "No body"))
                    return True
                if STATE.scenario == "validation":
                    self.fhir_reply(400, outcome("error", "required", "Demo validation error: a required value is missing"))
                    return True
                self.keep(kind, rid, res)
                self.fhir_reply(200, res)
                return True
            if self.command == "DELETE":
                STATE.store.get(kind, {}).pop(rid, None)
                self.fhir_reply(200, outcome("information", "informational", "Deleted"))
                return True
        return False

    def keep(self, kind, rid, res):
        with STATE.lock:
            res["id"] = rid
            meta = res.setdefault("meta", {})
            meta["versionId"] = str(int(meta.get("versionId", "0")) + 1) if str(meta.get("versionId", "0")).isdigit() else "1"
            meta["lastUpdated"] = time.strftime("%Y-%m-%dT%H:%M:%S.000+00:00", time.gmtime())
            STATE.store.setdefault(kind, {})[rid] = res
            STATE.uploads.append({"resourceType": kind, "id": rid})

    def transaction(self):
        bundle = self.read_json() or {}
        if STATE.scenario == "validation":
            self.fhir_reply(400, outcome("error", "required", "Demo validation error: a required value is missing"))
            return True
        entries = []
        for entry in bundle.get("entry", []):
            req = entry.get("request", {})
            res = entry.get("resource")
            method = req.get("method", "POST")
            url = req.get("url", "")
            kind = (res or {}).get("resourceType") or url.split("/")[0]
            rid = (res or {}).get("id") or (url.split("/")[1] if "/" in url else data.uid("new", str(time.time())))
            if method in ("POST", "PUT") and res is not None:
                self.keep(kind, rid, res)
                status = "201 Created" if method == "POST" else "200 OK"
            elif method == "DELETE":
                STATE.store.get(kind, {}).pop(rid, None)
                status = "204 No Content"
            else:
                status = "200 OK"
            entries.append({"response": {"status": status, "location": "%s/%s/_history/1" % (kind, rid), "etag": 'W/"1"'}})
        self.fhir_reply(200, {"resourceType": "Bundle", "type": "transaction-response", "entry": entries})
        return True

    # ---- FHIR search ---------------------------------------------------------------------------
    def search(self, kind, query):
        scenario_empty = STATE.scenario == "empty"
        items = list(STATE.store.get(kind, {}).values())
        if kind == "Questionnaire":
            items = []
        if scenario_empty and kind in ("Group", "Patient", "Encounter", "Observation"):
            items = []

        def one(name):
            return (query.get(name) or [""])[0]

        ids = one("_id")
        if ids:
            wanted = set(ids.split(","))
            items = [r for r in items if r["id"] in wanted]
        if kind == "Location" and one("_tag"):
            tag = one("_tag").replace("+", " ")
            items = [r for r in items if any(t.get("code") == tag for t in r.get("meta", {}).get("tag", []))]
        if kind == "Group" and one("location"):
            loc = one("location")
            items = [r for r in items if any(e.get("valueString") == loc for e in r.get("extension", []))]
        if kind == "Patient":
            has = [v for k, vs in query.items() if k.startswith("_has:Group") for v in vs]
            if has:
                lists = set(",".join(has).split(",")) - {""}
                members = set()
                for g in STATE.store["Group"].values():
                    if g["id"] in lists:
                        members.update(m["entity"]["reference"].split("/")[1] for m in g.get("member", []))
                items = [r for r in items if r["id"] in members]
            for text in [v.lower() for v in query.get("name", []) if v]:
                items = [r for r in items if text in " ".join(" ".join(n.get("given", [])) + " " + n.get("family", "") for n in r.get("name", [])).lower()]
            if one("identifier") and not one("name"):
                items = [r for r in items if any(i.get("value", "").lower() == one("identifier").lower() for i in r.get("identifier", []))]
            elif one("identifier"):
                items = [r for r in items if any(i.get("value", "").lower() == one("identifier").lower() for i in r.get("identifier", []))]
        if kind in ("Encounter", "Observation", "Condition", "RiskAssessment") and one("patient"):
            wanted = set(one("patient").split(","))
            items = [r for r in items if (r.get("subject", {}).get("reference", "").split("/")[-1]) in wanted]
        if kind == "Observation" and one("encounter"):
            items = [r for r in items if r.get("encounter", {}).get("reference", "").split("/")[-1] in set(one("encounter").split(","))]
        for key in ("_lastUpdated",):
            for value in query.get(key, []):
                m = re.match(r"(gt|ge|lt|le)(.+)$", value)
                if m:
                    op, stamp = m.group(1), norm_time(m.group(2))
                    items = [r for r in items if cmp(norm_time(r.get("meta", {}).get("lastUpdated", "")), op, stamp)]
        items.sort(key=lambda r: (r.get("meta", {}).get("lastUpdated", ""), r["id"]))
        includes = []
        if kind == "Patient" and not one("_elements") and query.get("_revinclude"):
            have = set(r["id"] for r in items)
            for spec in query["_revinclude"]:
                inc_kind = spec.split(":")[0]
                for r in STATE.store.get(inc_kind, {}).values():
                    if r.get("subject", {}).get("reference", "").split("/")[-1] in have:
                        includes.append(r)
        total = len(items)
        count = int(one("_count") or PAGE)
        offset = int(one("__offset") or 0)
        page = items[offset: offset + count]
        entries = []
        for r in page:
            entries.append({"fullUrl": "%s/%s/%s" % (self.base_url(), kind, r["id"]), "resource": minimal(r) if one("_elements") == "id" else r, "search": {"mode": "match"}})
        if offset == 0:
            for r in includes:
                entries.append({"fullUrl": "%s/%s/%s" % (self.base_url(), r["resourceType"], r["id"]), "resource": r, "search": {"mode": "include"}})
        links = [{"relation": "self", "url": "%s/%s" % (self.base_url(), kind)}]
        if offset + count < total:
            nq = dict((k, v[0]) for k, v in query.items() if k != "__offset")
            nq["__offset"] = str(offset + count)
            links.append({"relation": "next", "url": "%s/%s?%s" % (self.base_url(), kind, urlencode(nq))})
        self.fhir_reply(200, {"resourceType": "Bundle", "type": "searchset", "total": total, "link": links, "entry": entries})
        return True


def minimal(res):
    return {"resourceType": res["resourceType"], "id": res["id"]}


def cmp(a, op, b):
    return {"gt": a > b, "ge": a >= b, "lt": a < b, "le": a <= b}[op]


class Server(ThreadingMixIn, HTTPServer):
    daemon_threads = True
    allow_reuse_address = True


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8089)
    parser.add_argument("--host", default="0.0.0.0")
    args = parser.parse_args()
    server = Server((args.host, args.port), Handler)
    import os

    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".server.pid"), "w") as handle:
        handle.write(str(os.getpid()))
    sys.stdout.write("Fake OpenMRS on http://%s:%d%s  (sign in: %s / %s, fictional)\n" % (args.host, args.port, PREFIX, data.DEMO_USER, data.DEMO_PASSWORD))
    sys.stdout.flush()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
