"""Fictional data of the fake backend: one set of personas used across the whole guide.

Everything here is made up. Ids and dates are fixed, so every language shows the same records;
only the text that a person reads (names, places) is localized.

Python 3.7 compatible (no walrus operator, no builtin generics).
"""
import uuid

NS = uuid.UUID("5e1f0c1e-0000-4000-8000-00000000d00d")
LANGS = ("en", "fr", "es", "ru", "ar")


def uid(kind, key):
    """A stable id: the same key always gives the same uuid."""
    return str(uuid.uuid5(NS, kind + ":" + key))


# The fictional sign-in the guide shows. Never a real account.
DEMO_USER = "demo.agent"
DEMO_PASSWORD = "demo1234"
USER_UUID = uid("user", DEMO_USER)
PERSON_UUID = uid("person", DEMO_USER)
PROVIDER_UUID = uid("provider", DEMO_USER)
USER_DISPLAY = {"en": "Demo Agent", "fr": "Agent Démo", "es": "Agente Demo", "ru": "Демо-агент", "ar": "وكيل تجريبي"}

LOGIN_TAG = "Offline"

# (key, names per language, parent key)
LOCATIONS = [
    ("north", {"en": "Demo Health Centre North", "fr": "Centre de santé Nord (démo)", "es": "Centro de salud Norte (demo)", "ru": "Центр здоровья «Север» (демо)", "ar": "المركز الصحي الشمالي (تجريبي)"}),
    ("riverside", {"en": "Riverside Clinic", "fr": "Clinique de la Rivière", "es": "Clínica Ribera", "ru": "Клиника «Речная»", "ar": "عيادة الضفة"}),
    ("mobile", {"en": "Mobile Clinic Alpha", "fr": "Clinique mobile Alpha", "es": "Clínica móvil Alfa", "ru": "Мобильная клиника «Альфа»", "ar": "العيادة المتنقلة ألفا"}),
    ("district", {"en": "District Hospital", "fr": "Hôpital de district", "es": "Hospital de distrito", "ru": "Районная больница", "ar": "مستشفى المنطقة"}),
    ("camp", {"en": "Hope Camp Health Post", "fr": "Poste de santé du camp Espoir", "es": "Puesto de salud del campamento Esperanza", "ru": "Медпункт лагеря «Надежда»", "ar": "نقطة صحية في مخيم الأمل"}),
]
DEFAULT_LOCATION = "north"

# Patient lists (Groups of the server). Tied to a location through an extension.
GROUPS = [
    ("morning", "north", {"en": "Morning clinic", "fr": "Consultation du matin", "es": "Consulta de la mañana", "ru": "Утренний приём", "ar": "عيادة الصباح"}, [0, 1, 2, 3, 4, 5, 6]),
    ("followup", "north", {"en": "Follow-up group", "fr": "Groupe de suivi", "es": "Grupo de seguimiento", "ru": "Группа наблюдения", "ar": "مجموعة المتابعة"}, [3, 4, 7, 8, 9]),
    ("families", "north", {"en": "Family sessions", "fr": "Séances familiales", "es": "Sesiones familiares", "ru": "Семейные сессии", "ar": "جلسات الأسرة"}, [0, 2, 10, 11]),
    ("riverside-list", "riverside", {"en": "Riverside weekly list", "fr": "Liste hebdomadaire de la Rivière", "es": "Lista semanal de Ribera", "ru": "Недельный список «Речная»", "ar": "قائمة الضفة الأسبوعية"}, [5, 6]),
]

# 12 fictional patients. Gender, birth date and phone are the same in every language.
# (key, gender, birth date, phone or None, city key, names per language: (given, family))
CITIES = {
    "en": ("Northfield", "Demoland"),
    "fr": ("Valnord", "Démoland"),
    "es": ("Campo Norte", "Demolandia"),
    "ru": ("Северное", "Демоландия"),
    "ar": ("الحقل الشمالي", "ديمولاند"),
}
PATIENTS = [
    ("p01", "female", "1988-03-14", "+000 555 0101", {"en": ("Amara", "Okafor"), "fr": ("Aminata", "Diallo"), "es": ("Amara", "Ortega"), "ru": ("Амара", "Окафор"), "ar": ("أمل", "الحداد")}),
    ("p02", "male", "1975-11-02", "+000 555 0102", {"en": ("Daniel", "Mensah"), "fr": ("Daniel", "Moreau"), "es": ("Daniel", "Mendoza"), "ru": ("Даниил", "Меньшов"), "ar": ("دانيال", "منصور")}),
    ("p03", "female", "2009-07-21", "+000 555 0103", {"en": ("Lina", "Haddad"), "fr": ("Lina", "Haddad"), "es": ("Lina", "Herrera"), "ru": ("Лина", "Хаддад"), "ar": ("لينا", "حداد")}),
    ("p04", "male", "1992-01-30", "+000 555 0104", {"en": ("Samuel", "Bekele"), "fr": ("Samuel", "Bernard"), "es": ("Samuel", "Bello"), "ru": ("Самуил", "Белов"), "ar": ("صموئيل", "بكري")}),
    ("p05", "female", "1964-09-09", "+000 555 0105", {"en": ("Grace", "Nakamura"), "fr": ("Grâce", "Nicolas"), "es": ("Gracia", "Navarro"), "ru": ("Грейс", "Николаева"), "ar": ("غريس", "ناصر")}),
    ("p06", "male", "2015-05-18", "+000 555 0106", {"en": ("Yusuf", "Karimi"), "fr": ("Youssef", "Karim"), "es": ("Yusuf", "Karimi"), "ru": ("Юсуф", "Каримов"), "ar": ("يوسف", "كريمي")}),
    ("p07", "female", "1981-12-25", "+000 555 0107", {"en": ("Mei", "Tanaka"), "fr": ("Mei", "Tanguy"), "es": ("Mei", "Tapia"), "ru": ("Мэй", "Танаева"), "ar": ("ميّ", "طه")}),
    ("p08", "male", "1999-04-04", "+000 555 0108", {"en": ("Ibrahim", "Sesay"), "fr": ("Ibrahim", "Sow"), "es": ("Ibrahim", "Soto"), "ru": ("Ибрагим", "Сергеев"), "ar": ("إبراهيم", "سالم")}),
    ("p09", "female", "1957-08-16", "+000 555 0109", {"en": ("Fatima", "Rahman"), "fr": ("Fatima", "Roux"), "es": ("Fátima", "Rivas"), "ru": ("Фатима", "Рахманова"), "ar": ("فاطمة", "رحمن")}),
    ("p10", "other", "2002-10-10", "+000 555 0110", {"en": ("Alex", "Moreno"), "fr": ("Alex", "Martin"), "es": ("Álex", "Moreno"), "ru": ("Алекс", "Морозов"), "ar": ("أليكس", "مراد")}),
    # Edge case: a very long name.
    ("p11", "female", "1990-06-06", "+000 555 0111", {
        "en": ("Maria-Esperanza Valentina", "Fernández de la Cruz-Okonkwo"),
        "fr": ("Marie-Espérance Valentine", "Fernandez de la Croix-Okonkwo"),
        "es": ("María Esperanza Valentina", "Fernández de la Cruz Okonkwo"),
        "ru": ("Мария-Эсперанса Валентина", "Фернандес де ла Крус-Оконкво"),
        "ar": ("ماريا إسبيرانزا فالنتينا", "فرنانديز دي لا كروز أوكونكوو"),
    }),
    # Edge case: no phone number and no address.
    ("p12", "male", "1970-02-27", None, {"en": ("Peter", "Wanjiru"), "fr": ("Pierre", "Weber"), "es": ("Pedro", "Walker"), "ru": ("Пётр", "Воронов"), "ar": ("بطرس", "وهبي")}),
]
NO_ADDRESS = ("p12",)

IDENTIFIER_TYPE_UUID = uid("idtype", "openmrs-id")
IDENTIFIER_SOURCE_UUID = uid("idsource", "openmrs-id")
IDENTIFIER_TYPE_NAME = "OpenMRS ID"

# Fixed clock of the data: nothing here depends on today's date.
BASE_TIME = "2026-10-12T08:00:00+00:00"


def patient_id(key):
    return uid("patient", key)


def location_id(key):
    return uid("location", key)


def group_id(key):
    return uid("group", key)


def last_updated(n):
    """A fixed, increasing update time (n is the position of the record)."""
    minutes = n % 60
    hour = 8 + (n // 60) % 10
    return "2026-10-12T%02d:%02d:00.000+00:00" % (hour, minutes)


def identifier_value(index):
    return "DEMO-%07d" % (1000 + index)


def location_resource(key, lang, position):
    names = dict(LOCATIONS)[key]
    return {
        "resourceType": "Location",
        "id": location_id(key),
        "meta": {"versionId": "1", "lastUpdated": last_updated(position), "tag": [{"system": "http://fhir.openmrs.org/ext/location-tag", "code": LOGIN_TAG, "display": LOGIN_TAG}]},
        "status": "active",
        "name": names[lang],
        "description": names[lang],
    }


def group_resource(key, lang, position):
    for gkey, loc, names, members in GROUPS:
        if gkey == key:
            return {
                "resourceType": "Group",
                "id": group_id(gkey),
                "meta": {"versionId": "1", "lastUpdated": last_updated(20 + position)},
                "extension": [{"url": "http://fhir.openmrs.org/ext/group/location", "valueString": location_id(loc)}],
                "identifier": [{"system": "http://fhir.openmrs.org/ext/group/identifier", "value": gkey}],
                "active": True,
                "type": "person",
                "actual": True,
                "name": names[lang],
                "quantity": len(members),
                "member": [{"entity": {"reference": "Patient/" + patient_id(PATIENTS[i][0])}} for i in members],
            }
    raise KeyError(key)


def patient_resource(index, lang):
    key, gender, birth, phone, names = PATIENTS[index]
    given, family = names[lang]
    city, country = CITIES[lang]
    res = {
        "resourceType": "Patient",
        "id": patient_id(key),
        "meta": {"versionId": "1", "lastUpdated": last_updated(40 + index)},
        "identifier": [
            {
                "use": "official",
                "type": {"text": IDENTIFIER_TYPE_NAME, "coding": [{"system": "http://fhir.openmrs.org/code-system/patient-identifier-type", "code": IDENTIFIER_TYPE_UUID, "display": IDENTIFIER_TYPE_NAME}]},
                "value": identifier_value(index),
            }
        ],
        "active": True,
        "name": [{"use": "usual", "family": family, "given": given.split(" ") if " " in given and "-" not in given else [given]}],
        "gender": gender,
        "birthDate": birth,
    }
    if phone:
        res["telecom"] = [{"system": "phone", "use": "mobile", "value": phone}]
    if key not in NO_ADDRESS:
        res["address"] = [{"use": "home", "city": city, "country": country}]
    return res
