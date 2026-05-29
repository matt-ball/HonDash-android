# Privacy Policy — HonDash for Android

**Last updated:** May 28, 2026

## Overview

HonDash for Android is an open-source instrument-cluster app for Honda engines running a Hondata K-Pro V4 ECU. This policy explains how the app handles your data.

## Data Collection

**HonDash does not collect, store, or transmit any personal data.** Specifically:

- **No analytics or tracking** — the app contains no analytics SDKs or tracking code.
- **No internet access** — the app does not connect to the internet. All communication is local, between your Android device and the ECU via USB-OTG.
- **No accounts or sign-in** — the app does not require or support user accounts.
- **No advertising** — the app contains no ads or ad-related SDKs.

## Locally Stored Data

The app stores your dashboard layout, tile configuration, warning thresholds, and analog calibration settings **locally on your device** using Android SharedPreferences. This data:

- Never leaves your device
- Is not backed up to any server
- Can be cleared by uninstalling the app or clearing app data in Android settings

## USB Permissions

The app requests USB host permission to communicate with the Hondata K-Pro V4 ECU. This permission is used solely to read engine sensor data over the USB-OTG connection. No USB data is recorded, logged, or transmitted.

## Third-Party Services

HonDash does not integrate with any third-party services, APIs, or SDKs that collect user data.

## Children's Privacy

The app is not directed at children under 13. It is a vehicle diagnostic tool intended for use by vehicle owners and mechanics.

## Changes to This Policy

Any updates to this privacy policy will be reflected in this document within the app's source repository.

## Contact

If you have questions about this privacy policy, please open an issue on the [GitHub repository](https://github.com/matt-ball/HonDash-android).

## Source Code

This app is open source under the AGPL-3.0 license. You can review the complete source code at:
https://github.com/matt-ball/HonDash-android
