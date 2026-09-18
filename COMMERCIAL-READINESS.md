# Consumer internal candidate — NOT READY FOR DISTRIBUTION

This branch adds a separate consumer application ID so it cannot replace or inherit the personal application's local data. It targets API 36 and builds an unsigned release AAB plus debug APKs. There are no provider API keys in the package.

Consumer entry point is /beta. The personal app remains on /. Consumer hides Bibi configuration and removes background assistant services and their permissions from the merged manifest. Vosk/JNA and the offline model are isolated to the personal flavor. The consumer flavor contains no wake-word engine. Inspect the produced AAB for unexpected native libraries and test on a 16 KB emulator before release.

Release blockers:
- Consumer backend currently uses Sites sign-in and remains private to its owner. Do not distribute APKs to testers until access is deliberately configured and two real accounts are tested.
- Configure a dedicated server-side AI project, explicit budget and quota monitoring. Never embed an API key in BuildConfig or assets.
- Extract consumer hosting from personal hosting before broad public availability; do not simply publish the personal site.
- Verify browser-based sign-in on Android. WebView and Chrome sessions are separate. Do not copy cookies or bypass identity-provider restrictions. If embedded sign-in is rejected, use a supported browser-auth/native-session design.
- Sign AAB with a protected upload key and enroll Play App Signing. Current consumer release bundle is unsigned.
- Complete native 16 KB, API 36, microphone, Bluetooth, account deletion, network interruption, accessibility and device testing.
- Supply final privacy policy, operator/support contact, Data safety, store listing, rating and reviewer access.
- Complete Play Console developer verification and required testing track. No Play submission is performed by this branch.

Official requirements verified 2026-09-18:
https://developer.android.com/google/play/requirements/target-sdk
https://developer.android.com/guide/app-bundle
https://developer.android.com/guide/practices/page-sizes
https://support.google.com/googleplay/android-developer/answer/14151465
https://support.google.com/googleplay/android-developer/answer/13327111
