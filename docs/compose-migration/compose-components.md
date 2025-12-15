# Compose header utilities

New Compose primitives centralize banner and header rendering:

* `NetworkStatusBanner` – renders a centered, bold status label on the grey background formerly provided by `network_status_flag_layout.xml`. A `NetworkStatusText` test tag is attached for Compose UI assertions.
* `DrawerHeader` – stacks the "Last sync" label and timestamp vertically with the original spacing. Both rows expose test tags (`DrawerHeaderLabel` and `DrawerHeaderValue`).

These composables are defined in `core/src/main/java/org/openmrs/android/fhir/ui/components/HeaderComponents.kt` and are hosted from `MainActivity` via `ComposeView` to keep existing navigation and toolbar scaffolding intact.

## Sync status utilities

`SyncStatusLayout` mirrors `core/src/main/res/layout/sync_status_layout.xml` using Compose. It keeps the light-blue status row with grey icon/text, left-aligned percentage label, and in-card horizontal progress indicator using a `SyncStatusUiState` that distinguishes syncing, success, error, and idle states.

```kotlin
SyncStatusLayout(state = SyncStatusUiState.Syncing(completed = 5, total = 10))
```

Use `SyncInfoContent` to render the sync session list and delete/clear actions.
