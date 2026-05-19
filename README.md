# HonDash Android

An Android instrument-cluster app for Honda engines running a **Hondata K-Pro V4** ECU,
connected over USB-OTG. Designed to be mounted in the car as a digital dashboard.

This is an independent Android port inspired by the original
[HonDash](https://github.com/pablobuenaposada/HonDash) (Raspberry Pi / Python) project.

## Features

- Live ECU polling over USB-OTG (K-Pro V4).
- Configurable tile dashboard:
  - 2 × 2 grid, multiple pages, long-press to configure, tap "+" to add.
  - Sources include RPM, ECT, IAT, TPS, MAP, AFR, battery, VSS, gear, and 8 generic analog inputs (AN0–AN7).
  - Per-tile unit selection (e.g. °C/°F, psi/bar, km/h/mph) and custom label override.
  - Per-tile warning / alarm thresholds, with a hard-flashing alarm state.
  - Per-analog-channel linear calibration (e.g. 0.5–4.5 V → 0–100 psi).
- Demo mode that drives every tile from a synthetic ECU, no hardware required.
- Layout, thresholds, and calibration persisted across launches.

## Requirements

- Android device with USB-OTG support, Android 8.0 (API 26) or newer.
- Hondata K-Pro V4 ECU and a USB-OTG cable.
- For development: JDK 17 and Android Studio (or the included Gradle wrapper).

## Build

```sh
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

To install on a connected device:

```sh
./gradlew installDebug
```

## Usage

1. Launch the app. Tap **Demo** to explore the UI without a car.
2. With the K-Pro V4 plugged in via USB-OTG, tap **Connect** and grant the USB permission prompt.
3. Long-press any tile to change its source, unit, label, thresholds, or analog calibration.
4. Tap the **+** tile (or swipe to the next page) to add additional tiles.

## License

Licensed under the **GNU Affero General Public License v3.0** (AGPL-3.0),
inherited from the upstream HonDash project. See [LICENSE](LICENSE).
