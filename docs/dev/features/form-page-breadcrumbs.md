# Page breadcrumbs of a form

Not an OpenMRS feature: a row above the progress bar of a multi-page form lists its pages (the
`page` item control); a tap goes straight to the page. `forms/PageBreadcrumbs.kt`, installed for every
form built with `newQuestionnaireFragment`.

* **Placement**: in a band of top padding of the form library's root layout. Adding it to the
  library's own title row makes the library lay its list out taller than the space it has, so the last
  questions of a long page cannot be scrolled to.
* **Reflection**: the library has no call to change page, so the row reads and sets its internal
  `QuestionnaireViewModel` (`FormPager`). Names are kept in `proguard-rules.pro`; checked with `javap`
  for data-capture 1.3.1. If an upgrade renames one, the row is not shown (a warning is logged): check
  the names when upgrading.
* **Required answers**: going forward validates the whole form first (what the submit button calls)
  and lands on the first page with a missing required answer instead of the target, so jumping cannot
  get past one. Going back is free.
* A read-only encounter is not paginated, so it has no row. Tutorial step: `form.pages` (optional).
