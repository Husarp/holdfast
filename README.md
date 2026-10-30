# Holdfast

Android app that keeps the notifications you care about from disappearing.

Pick the apps that matter. When you swipe one of their notifications away, Holdfast puts back its own copy (with the
original app's icon and name), and keeps bringing it back until you open it or tap **Dismiss**, even after a restart.

## Features
- Choose which apps are pinned; copies return after a delay you set, and can expire on their own.
- Tap a copy to open the original app; Snooze 1h / 3h, Dismiss, and quick reply where the original supports it.
- History (kept 30 days) and per-app statistics.
- Mute rules by notification channel or text; profiles (Work, Home, Sleep), quiet hours and quiet places.
- VIP senders, repeat-until-done, chat grouping, a digest at set times, hints for apps you never open.
- Export and import of your rules and settings.

## Privacy
Notification data stays on your phone. Internet access is used only to check GitHub for updates and download an APK you request. No user data is ever sent. There are no analytics or cloud backups.

## Limits
- Android does not let an app keep another app's notification from being removed, so Holdfast shows a copy instead.
- After a restart a copy can open the app, but not always the exact chat.
- Android 15+ hides one-time codes from apps like this one.

## Build
Android Studio or `./gradlew assembleDebug` (JDK 17+, Android SDK 36). Requires Android 8.0+.
