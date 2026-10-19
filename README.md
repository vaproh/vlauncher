# vlauncher

A minimal, distraction-free Android launcher. Personal fork of
[Olauncher](https://github.com/tanujnotes/Olauncher) that keeps the
distraction-free core and adds the features I actually use.

No ads, no telemetry, no analytics.

## Features

- Minimal home screen: clock, screen time indicator, and a clean app list
- **Custom fonts** - Default, Monospace, Hack, JetBrains Mono, Iosevka, Inter
- **Icon pack support** - app drawer icons resolve from installed icon packs
- **Home screen icons** - optional app icons on the home screen, with a toggle
- **Wallpaper rotation** - configurable change frequency
- Swipe gestures and long-press actions for fast navigation

## Download

Grab the latest APK from
[Releases](https://github.com/vaproh/vlauncher/releases). Every `v*` tag is
built automatically by CI and published as a signed APK.

Installs as `app.vlauncher`, so it sits alongside the original Olauncher if
you have that installed too.

## Build

Requires JDK 17+ and Android SDK platform 36.

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`
(`app.vlauncher.debug`). For a release build, `./gradlew assembleRelease`
with signing credentials in the environment - the CI workflow does this on
tag push.

## License

GPL-3.0. See `LICENSE`. Bundled font licenses are in
`THIRD_PARTY_NOTICES.md` and `app/src/main/assets/licenses/`.

## Credits

Built on [Olauncher](https://github.com/tanujnotes/Olauncher) by
[tanujnotes](https://github.com/tanujnotes) and contributors.
