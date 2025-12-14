# Compose header utilities

New Compose primitives centralize banner and header rendering:

* `NetworkStatusBanner` – renders a centered, bold status label on the grey background formerly provided by `network_status_flag_layout.xml`. A `NetworkStatusText` test tag is attached for Compose UI assertions.
* `DrawerHeader` – stacks the "Last sync" label and timestamp vertically with the original spacing. Both rows expose test tags (`DrawerHeaderLabel` and `DrawerHeaderValue`).

These composables are defined in `core/src/main/java/org/openmrs/android/fhir/ui/components/HeaderComponents.kt` and are hosted from `MainActivity` via `ComposeView` to keep existing navigation and toolbar scaffolding intact.
