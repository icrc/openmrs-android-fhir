Filtering Resources on Sync
===========================

Objective
---------

We want to filter data based on **location**, so during the **sync process**, only resources (**FHIR: Patient, Encounter, Observation**) from selected location(s) are synchronized.

* * * * *

Options Explored
----------------

### 1️⃣ Using `_include` / `_revinclude` to Get Observations & Encounters from a Patient

This approach allows us to fetch **Encounters & Observations** associated with a **Patient**. It is supported in **OpenMRS**.

#### ✅ Example API:

```
https://dev3.openmrs.org/openmrs/ws/fhir2/R4/Patient?
_revinclude=Observation:patient&_revinclude=Encounter:patient

```

#### 🔗 PR Related to This:

[openmrs-module-fhir2/pull/545](https://github.com/openmrs/openmrs-module-fhir2/pull/545)

#### ⚠️ Notes:

-   No **paging** of included resources.
-   There could be **thousands to hundreds of thousands** of Observations.
-   Instead, querying against **Encounter** and **Observation** separately and paging as needed is preferred.
-   However, for our **offline-first** use case, we are fine with this approach.

* * * * *

### 2️⃣ Using `_has` Parameter to Filter Patients Based on Group

This method allows filtering **patients based on a group** or **"Patient List"** in OpenMRS. It is also supported in OpenMRS.

#### 🔍 Additional Requirement:

-   We need to **create lists** for patients based on location in the **OpenMRS backend**.

#### ✅ Example APIs:

Fetch patients belonging to a specific group:

```
GET /openmrs/ws/fhir2/R4/Patient?_has:Group:member:id=GROUP_ID

```

Fetch all groups ("Patient Lists"):

```
GET /openmrs/ws/fhir2/R4/Group

```

#### 🔗 PR Related to This:

[openmrs-module-fhir2/pull/545](https://github.com/openmrs/openmrs-module-fhir2/pull/545)

* * * * *

### 3️⃣ `$everything`: An Alternative for `_include` / `_revinclude`

The `$everything` operation on **Patient** returns **all related resources** (similar to `_include`, but it fetches everything).

#### ⚠️ Uncertainty:

-   **Not sure if this is supported in OpenMRS**.
-   This can be utilized in the future **when we need all resources** related to a patient.

* * * * *

### Conclusion

Each of these approaches offers unique advantages, and the choice depends on the **use case and system limitations**:

-   **For fetching observation & encounters**, `_include` / `_revinclude` works but requires handling large datasets.
-   **For filtering based on group**, `_has` with groups is a structured solution but needs **backend setup**.
-   **For future use**, `$everything` may be an option, but **support in OpenMRS needs confirmation**.

🚀 **Next Steps:** Decide on the best approach based on scalability and OpenMRS capabilities.