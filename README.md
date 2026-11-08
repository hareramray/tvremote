# TV Remote (Wi-Fi + Bluetooth + Voice)

[![Sponsor](https://img.shields.io/badge/Sponsor-%E2%9D%A4-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/hareramray)
[![Build](https://github.com/hareramray/tvremote/actions/workflows/build.yml/badge.svg)](https://github.com/hareramray/tvremote/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Android 9+](https://img.shields.io/badge/Android-9%2B-3DDC84?logo=android&logoColor=white)](#build-the-apk-without-android-studio)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Fire TV](https://img.shields.io/badge/Works%20with-Fire%20TV-FF9900)](#pairing)
[![Last commit](https://img.shields.io/github/last-commit/hareramray/tvremote)](https://github.com/hareramray/tvremote/commits/main)
[![Stars](https://img.shields.io/github/stars/hareramray/tvremote?style=social)](https://github.com/hareramray/tvremote/stargazers)

An Android app that turns your phone into a remote for a **Fire TV**. It was built and tested
against the Redmi Fire TV 32" (model `L32MA-FVIN`), and should work with other Fire OS TVs and sticks.

There are two ways to connect, and you can switch between them in the app:

| | **Wi-Fi + PIN** (default) | **Bluetooth** |
|---|---|---|
| Setup | Tap your TV, type the PIN it shows | Pair the phone like a keyboard in TV settings |
| Range | Anywhere on the same Wi-Fi | About 10 m |
| Buttons, touchpad, voice, typing | ✅ | ✅ |
| Open apps ("open YouTube", Apps tab) | ✅ | ❌ |
| Turn the TV on / wake it | ✅ (Wake-on-LAN) | ❌ |
| Delete / clear text | ❌ | ✅ |
| Previous / Next track | ❌ | ✅ |
| Works without Wi-Fi | ❌ | ✅ |

## Features

- **Remote tab**: D-pad, OK, Back, Home, Menu, play/pause, rewind, fast forward, volume, mute
  and power. Buttons repeat while held. Buttons the current connection can't send are dimmed.
- **Touchpad tab**: swipe to move, tap to select.
- **Keyboard tab**: type into TV search boxes from your phone keyboard.
- **Apps tab** (Wi-Fi): every app installed on the TV, with icons. Tap one to open it.
- **Voice**: tap the mic and speak (see the list below).
- **Phone volume buttons** control the TV volume while connected.
- Remembers the TV and reconnects by itself. Over Wi-Fi it also finds the TV again if the
  router gives it a new IP address.

## Pairing

### Wi-Fi (recommended)

1. Make sure the phone is on the **same Wi-Fi** as the TV (not mobile data or a guest network).
2. Open the app. The connect screen lists TVs it finds on your network.
3. Tap your TV (e.g. "Living Room TV"). The TV shows a **4-digit PIN**.
4. Type the PIN in the app. Done. You only do this once.

If the TV isn't listed, type its IP address (on the TV: Settings → My Fire TV → About → Network).

### Bluetooth

1. In the app: connect screen → **Bluetooth** → allow "Nearby devices" → **Make phone visible**.
2. On the TV: **Settings → Controllers & Bluetooth Devices → Other Bluetooth Devices →
   Add Bluetooth Devices**, select the phone, confirm the code.

> Don't use **Amazon Fire TV Remotes → Add New Remote**. That screen only finds Amazon remotes.

## Voice commands

| Say | Does |
| --- | --- |
| "up", "down", "left", "right" | Move |
| "down 3 times", "left twice", "up up up" | Move several steps |
| "ok", "select" | Select |
| "back", "go home", "menu" | Navigate |
| "play", "pause", "rewind", "fast forward" | Playback |
| "volume up", "volume down 5", "mute" | Volume (default 3 steps) |
| "open YouTube", "launch Hotstar" | Opens the app (Wi-Fi) |
| "search Avengers" | Types "Avengers" into the open search box |
| "type hello" | Types into the focused text box |
| "down 2 then ok" | Several commands in a row |
| "turn on the TV" | Wakes the TV (Wi-Fi) |
| "power off" | Turns the TV off |

If a phrase isn't a command, the app offers **Type it** / **Search it**.

Voice uses the phone's speech recognizer (the Google app, usually needs internet), then sends
button presses, typed text or app launches to the TV. It isn't Alexa: the Alexa button's audio
channel is private to Amazon. Commands are in English, and the recognizer language is `en-IN`
(set in `VoiceController.kt`).

## How the Wi-Fi mode works

It uses the same local API as Amazon's official Fire TV phone app:

- **Discovery**: SSDP search for the DIAL service; the TV's name comes from its device description.
- **Pairing**: `POST https://<tv>:8080/v1/FireTV/pin/display` (the TV shows a PIN), then
  `…/pin/verify` returns a client token, which the app stores.
- **Commands**: `POST /v1/FireTV?action=dpad_up|select|home|back|menu|volume_up|mute|power…`,
  `/v1/media?action=play|scan`, `/v1/FireTV/text`, `/v1/FireTV/app/<package>`, `GET /v1/FireTV/appsV2`.
- **Waking**: when the TV doesn't answer, the app sends a Wake-on-LAN packet plus a DIAL wake
  (`http://<tv>:8009/apps/FireTVRemote`), waits for the TV, then resends the press.

Amazon hasn't published this API (details come from community research, especially the
MIT-licensed [Firehose Remote](https://github.com/AustrianCanvasSociety/firehose-remote) protocol
notes), so a future Fire OS update could change it. Bluetooth mode is the fallback if that happens.

## Build the APK (without Android Studio)

JDK 17, the Android SDK (platform 35) and Gradle are installed in `%USERPROFILE%\android-build`.

1. Double-click **`build-apk.bat`**.
2. The result is **`TvRemote.apk`** in this folder: an optimized release build of about 2.4 MB.

Install it by copying the APK to the phone and tapping it. Allow "Install unknown apps", and if
Play Protect warns, choose "Install anyway". The APK is signed with the local debug key, which is
fine for your own phones. For the Play Store, set up your own keystore in `app/build.gradle.kts`.

Setting up the tools on another PC:

1. Get **JDK 17** (e.g. Temurin zip) and the Android **command-line tools** zip.
2. Put the tools at `<sdk>\cmdline-tools\latest`, then run
   `sdkmanager --sdk_root=<sdk> --licenses` followed by
   `sdkmanager --sdk_root=<sdk> "platforms;android-35" "build-tools;35.0.0" "platform-tools"`.
   If your Windows user folder has a space in its name, use the short path (`C:\Users\NAME~1\...`).
3. Create `local.properties` containing `sdk.dir=C:/path/to/sdk` (forward slashes).
4. Set `JAVA_HOME` and run `gradlew.bat assembleRelease`.

You can also open the folder in Android Studio and press Run.

## Troubleshooting

- **Wi-Fi: no TVs found**: phone and TV must be on the same Wi-Fi. Some routers block discovery
  between devices; enter the TV's IP address instead.
- **Wi-Fi: "TV asleep or off"**: press any button (or **Wake**). The first press can take a few
  seconds while the TV wakes up.
- **Wi-Fi: "Pair again"**: the TV forgot this phone (e.g. after a reset). Pair again with a new PIN.
- **Bluetooth: OK does nothing in some app**: Settings (gear) → turn on **OK button sends Enter**.
- **Bluetooth: "Bluetooth remote stopped"**: another app took over the keyboard role. Close it and tap Retry.

## Code map

| File | Purpose |
| --- | --- |
| `wifi/FireTvApi.kt` | Fire TV local API: pairing, commands, text, apps, wake (DIAL + Wake-on-LAN) |
| `wifi/FireTvDiscovery.kt` | Finds TVs on the network (SSDP) |
| `wifi/WifiRemote.kt` | Wi-Fi state, pairing flow, ordered command queue, hold-to-repeat, auto-wake |
| `hid/BluetoothHidRemote.kt` | Phone as a Bluetooth HID keyboard/remote |
| `hid/HidDescriptor.kt`, `hid/HidKeys.kt`, `hid/RemoteButton.kt` | HID report map, key codes, buttons |
| `voice/VoiceController.kt` | Speech recognizer wrapper |
| `voice/VoiceCommandParser.kt` | Spoken phrase → button presses / text / app launch |
| `RemoteViewModel.kt` | Sends every action over the active connection (Wi-Fi or Bluetooth) |
| `ui/*` | Jetpack Compose screens |

## Support

If this app is useful to you, consider [sponsoring the project on GitHub](https://github.com/sponsors/hareramray) ❤️.
Bug reports and pull requests are welcome.

## License

[MIT](LICENSE). Not affiliated with or endorsed by Amazon. "Fire TV" is a trademark of Amazon.com, Inc.
