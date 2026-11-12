# Android Floating Voice Demo

An Android Studio project for testing a floating voice companion over the DeepSeek Android app. Only `com.deepseek.chat` events enter the text-reading path. For other apps, the service checks only the foreground package name on window-state events so it can hide the overlay; it returns without reading event text, window nodes, or screen content. In DeepSeek, the service scans visible text nodes but excludes editable/password nodes; surrounding chat text may still be included, so users review the captured text and tap again before it is sent to the configured TTS server.

## Build

- Android Studio with JDK 17 and Android SDK Platform 35
- Minimum Android version: 8.0 (API 26)

Open this folder in Android Studio, let Gradle sync, then run the `app` configuration on an Android device. The standalone debug APK is delivered as `StoryVoiceOverlayDemo-debug.apk` in this folder. Debug builds are signed with the standard local debug key and are intended only for testing.

## First run

1. Install the demo and open it.
2. Confirm the TTS server URL. The default is `http://192.168.10.10:8766`; the phone and server must be on the same network.
3. Grant “Display over other apps”.
4. In Accessibility settings, enable “DeepSeek Reply Voice”. This is a powerful permission: the disclosure in the app explains that visible text is only sent to the TTS server after the user confirms.
5. Open DeepSeek. The floating panel should appear. Tap “Check and voice”, review the captured text, then tap “Confirm and play”.

## Mainland Android vendor settings

The first-run screen detects Xiaomi/HyperOS, Huawei/HarmonyOS, OPPO/ColorOS, vivo/OriginOS, Honor/MagicOS, and related manufacturers and displays the matching background launch/battery path. Only change those settings if the ROM stops the floating panel after leaving Settings.

## HarmonyOS compatibility

Compatibility is not identical across Android and Huawei devices. HarmonyOS versions with Android app compatibility can install this APK, but overlay permission, Accessibility service behavior, and background execution must be verified on the target device and OS version. HarmonyOS NEXT/pure HarmonyOS does not run Android APKs; this Android project is not compatible with it. No Huawei device testing has been performed for this demo.

## Scope and limitations

- The demo does not automate DeepSeek, tap its controls, read editable input fields, or start playback by itself.
- Other applications are used only for a foreground package-name transition that hides the overlay; their accessibility event text and window nodes are not queried.
- Accessibility nodes vary by DeepSeek version and vendor. Capturing the latest assistant response must be checked on real devices; node text may be split, missing, or mixed with surrounding UI text.
- The current target package is `com.deepseek.chat`. If the installed client uses another package ID, update `TARGET_PACKAGE` in `ReplyAccessibilityService.java`.
- Text is sent over the configured HTTP endpoint. The default is suitable only for a trusted LAN demo, not public deployment. Use authenticated HTTPS before distributing the app outside a private test network.
- The client requests short sentence-sized WAV segments and starts playback as soon as the first segment is synthesized while the next segment is generated. This overlaps speech synthesis with playback, but the current Qwen server API is not token-streaming and cannot guarantee millisecond audio start.
- Google Play distribution requires an Accessibility API declaration and compliant in-app disclosure/consent. The app must not be declared as a disability accessibility tool unless that is its core purpose.
