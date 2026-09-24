# Flowesal Android — rootless

Android client inspired by Flowseal/zapret, designed around `VpnService` rather than root/kernel hooks.

## No root

The project deliberately avoids:

- root / `su`
- Magisk / KernelSU
- Shizuku
- iptables / nftables
- NFQUEUE
- third-party VPN servers

The Android system VPN permission is the only special access requested.

## Profiles

The UI contains:

- General
- ALT 1
- ALT 2
- ALT 3
- ALT 4

The profile engine is intentionally separated from the VPN service so real desync strategies can be added without changing the UI.

## Current status

The repository is a **buildable rootless foundation**. It currently establishes a local Android `VpnService` and has a small DNS interception/forwarding path. It does **not** yet claim full Flowseal/nfqws DPI-desync parity.

A full userspace desync engine is the remaining major component.

## Build without installing Android tooling locally

Push this repository to GitHub and run:

`Actions → Build Flowesal APK → Run workflow`

The workflow installs JDK/Android SDK/Gradle on the GitHub runner and publishes `app-debug.apk` as an artifact.

## Local build

Use Android Studio or install Android SDK + Gradle 8.10+ and run:

`./scripts/build-local.sh`
