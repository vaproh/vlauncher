# vlauncher

A personal fork of [Olauncher](https://github.com/tanujnotes/Olauncher): a minimal Android launcher that reduces screen time.

Changes over upstream:

- Rebranded as vlauncher (`app.vlauncher`), new icon and version.
- Font selection in settings: Default, Monospace, Hack, JetBrains Mono, Iosevka, Inter.
- Removed upstream's cross-promotions and store links.
- No telemetry or ads.

## Build

Requires JDK 17 or 21 and Android SDK platform 36.

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew assembleDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk` and installs as
`app.vlauncher.debug`, alongside any installed Olauncher.

## License

GPL-3.0. See `LICENSE`. Font licenses are in `THIRD_PARTY_NOTICES.md` and
`app/src/main/assets/licenses/`.
