# MoonWaker

MoonWaker is an Android TV game streaming client built on
[Moonlight for Android](https://github.com/moonlight-stream/moonlight-android). It pairs with
MoonWaker Host on a Windows PC to wake the PC, sign in, browse the game library and stream.

This repository holds the source of every released MoonWaker TV app version: each release is
one commit tagged `v<version>`. MoonWaker Host is a separate, closed-source product.

MoonWaker is not affiliated with Moonlight, NVIDIA, Valve, Epic Games or Discord.

## Building

```bash
git submodule update --init --recursive
```

Create `local.properties` with `sdk.dir=` pointing at the Android SDK, then build the debug
APK with JDK 17:

```bash
./gradlew assembleNonRootDebug
```

Run the unit tests:

```bash
./gradlew testNonRootDebugUnitTest
```

## License

GNU General Public License v3.0 (`LICENSE.txt`). Third-party notices are in `NOTICE`.
