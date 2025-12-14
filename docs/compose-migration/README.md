# Compose migration: Header and network banner

This module now renders the navigation drawer header and the network status banner with Jetpack Compose.

## Network status banner

* Composable: `NetworkStatusBanner(text: String)` in `core/src/main/java/org/openmrs/android/fhir/ui/components/HeaderComponents.kt`.
* Hosted via a `ComposeView` with ID `network_status_banner` inside `activity_main.xml`.
* Text updates are driven by `MainActivity.observeNetworkConnection`, which writes into a Compose `mutableState`.

## Drawer header

* Composable: `DrawerHeader(label: String, lastSyncValue: String)` in `core/src/main/java/org/openmrs/android/fhir/ui/components/HeaderComponents.kt`.
* Added to the `NavigationView` at runtime through `MainActivity.setupDrawerHeader()`.
* The last sync text uses Compose state so updates from `MainActivityViewModel.lastSyncTimestampLiveData` automatically recompose the header.

## Testing

`core/src/androidTest/java/org/openmrs/android/fhir/ui/NetworkBannerAndDrawerHeaderTest.kt` contains Compose UI tests that assert visibility and dynamic text changes for both composables.
