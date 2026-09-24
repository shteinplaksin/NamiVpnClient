# Nami for Android

Nami is an Android VPN and proxy client powered by sing-box. It supports
Shadowsocks, VMess, VLESS, Trojan, SSH, WireGuard, Hysteria/Hysteria2, TUIC,
NaiveProxy, Mieru, and other sing-box protocols. The project is licensed under
GPL-3.0; see [LICENSE](LICENSE).

Nami is not a client implemented from scratch: the whole application is based
on the open-source [NekoBox for Android](https://github.com/MatsuriDayo/NekoBoxForAndroid)
project and has since been adapted and extended under the Nami name. Nami also
builds on the SagerNet project, originally created by nekohasekai. We are
grateful to the upstream contributors; see the [SagerNet contributor history](https://github.com/SagerNet/SagerNet/graphs/contributors).

The Android app is a single `:app` module written in Kotlin and Java. Compose
with Material 3 is the primary UI, Room stores durable app data, and the separate
`libcore/` directory contains the Go sing-box integration.

## Requirements

- JDK 21
- Gradle 9.6 and Android Gradle Plugin 9.4
- Android SDK Platform 37 and Android SDK Build-Tools 37.0.0
- Android NDK 25.2.9519653 only when rebuilding the frozen `libcore` artifact
- `local.properties` with the local Android SDK path
- Local build inputs `app/libs/libcore.aar` and
  `app/src/main/assets/sing-box/` (GeoIP/GeoSite databases)

The app supports Android 7.0/API 24 and later. It compiles and targets API 37.

## Build

Use the Gradle wrapper from the repository root:

```powershell
.\gradlew.bat :app:assembleOssDebug
.\gradlew.bat :app:testOssDebugUnitTest
```

On macOS or Linux, use `./gradlew` with the same task names. Other product
flavors are `fdroid`, `play`, and `preview`. APKs are written under
`app/build/outputs/apk/`; Play bundles are written under
`app/build/outputs/bundle/`.

This maintainer workspace also has an ignored `run_gradle.ps1` helper that sets
up its local JDK, SDK, and Gradle cache. It is not part of a normal clone; use
the wrapper and a configured Android SDK elsewhere.

## Release signing

For a local signed build, place `release.keystore` at the repository root and
set `KEYSTORE_PASS`, `ALIAS_NAME`, and `ALIAS_PASS` in the ignored
`local.properties`. Do not commit the keystore or signing passwords. GitHub
Actions release and preview jobs read the keystore from the
`RELEASE_KEYSTORE_BASE64` repository secret and signing properties from
`LOCAL_PROPERTIES`.

Debug builds use the Android debug key when release signing is not configured.

## Repository layout

- `app/` — Android application, resources, and tests
- `libcore/` — Go sing-box integration; treated as a frozen native dependency
- `buildSrc/` — shared Gradle configuration
- `buildScript/` — build and CI helper scripts
- `.github/workflows/` — preview and release workflows

## Project links

- [Nami for Android](https://github.com/shteinplaksin/NamiVpnClient)
- [NekoBox for Android — upstream application source](https://github.com/MatsuriDayo/NekoBoxForAndroid)
- [sing-box documentation](https://sing-box.sagernet.org/)
