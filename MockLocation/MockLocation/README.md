# Mock Location Tester (Android)

A small developer tool for testing **your own Android app** with a fixed GPS position.
Pick a point on the map or type coordinates, press Start, and any app that reads the
device location receives that position until you press Stop.

Default start point: Burj Khalifa, Dubai (25.1972, 55.2744).

## Setup

1. Open this folder in **Android Studio** (it creates the Gradle wrapper on first sync).
2. Run on a real device or emulator (Android 7.0+, API 24).
3. On the device: **Settings > About phone > tap Build number 7 times** to enable Developer Options.
4. **Developer Options > Select mock location app > Mock Location Tester**.
   (The in-app "Open Developer Options" button jumps there.)
5. Open the app, allow location and notification permissions.

## Use

- Tap the map, or type latitude/longitude and press **Go** to move the marker.
- Press **Start mock** to begin feeding that location to the system.
- Open your app under test and check the position it reads.
- Press **Stop mock** when done. The real GPS takes over again.

Change the point at any time: pick a new spot, then press Start again.

## Notes

- Map tiles come from OpenStreetMap through osmdroid. No API key needed, but keep
  usage light, as the public tile servers have a fair-use policy.
- The mock feeds both the GPS and network providers, so apps using either (or Fused
  Location) see the test position.
- Intended for testing your own app and campaigns. Do not use it to misrepresent
  location to third-party apps or services.
