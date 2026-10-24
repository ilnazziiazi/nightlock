# night lock

Name is **night lock**, always lowercase. Package `io.github.ilnazziiazi.nightlock` — renaming it breaks the installed device owner.

An Android app that turns the phone
into a lock screen on a schedule (default 22:00–06:00) with no way out.
Cold Turkey for the whole device; no per-site or per-app blocking.

## Principles

- **Ponytail.** Minimum code, files and dependencies. Stdlib/framework first, everything else later.
- **Zero deps.** Kotlin + Android framework. No Compose, AppCompat, Material, Coroutines,
  Hilt, etc. The only dependency is junit (test only). Do not add more.
- **Zero battery.** No services, no WorkManager, no polling. One alarm
  (`AlarmManager.setAlarmClock`, the only kind vendor ROMs don't widen into a ±1h window)
  for the next window boundary. That's it.
- **No exit at night.** This is the feature. Do not add "pause", "snooze" or "emergency exit"
  buttons. The emergency exit is adb only (see README).

## How it works

Device Owner (assigned via `adb shell dpm set-device-owner`) → `startLockTask()` (kiosk).

- `Schedule.kt` — window in minutes of day in SharedPreferences + pure `isActive`, `nextBoundary`.
  The only logic with a test (`ScheduleTest.kt`). `start == end` means disabled.
- `SyncReceiver.kt` — the single entry point: alarm / BOOT_COMPLETED / TIME_SET /
  TIMEZONE_CHANGED → `sync(ctx)`: reschedule the alarm, apply device-owner policy (`arm`),
  launch `LockActivity` if the window is active, otherwise release it.
- `LockActivity.kt` — current time (`TextClock`), "until HH:MM", Call button (`ACTION_DIAL`).
  No timers, no handlers: the process does nothing while the screen is on.
  Back is swallowed, `onResume → startLockTask()`. `singleTask`, `lockTaskMode=if_whitelisted`.
- `MainActivity.kt` — two `TimePickerDialog`s, device-owner status. `onResume → sync()`,
  which is what makes settings unreachable at night: the screen is immediately covered by `LockActivity`.
- `AdminReceiver.kt` — empty `DeviceAdminReceiver`, the target for `dpm`.
- Device-owner policy (in `arm`): lock task packages = [night lock, default dialer, clock app
  (so a morning alarm's full-screen UI can show and be dismissed)];
  features = KEYGUARD | GLOBAL_ACTIONS; restrictions = DISALLOW_SAFE_BOOT, DISALLOW_CONFIG_DATE_TIME.
- `android:testOnly="true"` in the manifest so that `dpm remove-active-admin` works. Hence
  always `adb install -t`.

## Build

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # brew install openjdk@21
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```

SDK: `/opt/homebrew/share/android-commandlinetools` (brew cask `android-commandlinetools`),
path in `android/local.properties` (not in git). compileSdk/targetSdk 36, minSdk 33, AGP 8.13,
Gradle 8.14 (wrapper; system gradle 9.x does not work with AGP 8).

Signing: `~/.nightlock/release.jks` via `android/keystore.properties` (both gitignored). Debug
and release build types share this key, so they install over each other and `run-as` keeps
working on debug builds. Without the properties file the build silently uses the debug key,
which the installed device owner rejects.

## Release

1. Bump `versionCode` / `versionName` in `app/build.gradle.kts`.
2. `./gradlew :app:testDebugUnitTest :app:assembleRelease`, install and run the on-device test below.
3. `git tag vX.Y && git push --tags`
4. `cp app/build/outputs/apk/release/app-release.apk /tmp/nightlock-X.Y.apk && gh release create vX.Y /tmp/nightlock-X.Y.apk --title "night lock X.Y" --notes "..."`
   (the `file#label` form only changes the display label, not the asset name).

## Don'ts

- Don't move logic into services/WorkManager "for reliability" — a device owner is exempt
  from background activity launch restrictions, the alarm is enough.
- Don't add per-weekday schedules, profiles, stats, notifications until explicitly asked.
- Don't change `applicationId` or the `AdminReceiver` name — it breaks the installed device owner.

## On-device testing

`am force-stop` is ignored for a device-owner app ("protected package"), so a process with
cached prefs has to be killed via `run-as`. Set the window directly in prefs (minutes of day);
launching MainActivity applies it:

```sh
adb shell run-as io.github.ilnazziiazi.nightlock kill $(adb shell pidof io.github.ilnazziiazi.nightlock)
adb shell "run-as io.github.ilnazziiazi.nightlock sh -c 'printf \"<map><int name=\\\"start\\\" value=\\\"1165\\\" /><int name=\\\"end\\\" value=\\\"1168\\\" /></map>\" > shared_prefs/nightlock.xml'"
adb shell am start -n io.github.ilnazziiazi.nightlock/.MainActivity
adb shell dumpsys activity activities | grep -E "mLockTaskModeState|topResumedActivity"   # LOCKED, LockActivity
adb shell dumpsys alarm | grep -A1 "io.github.ilnazziiazi.nightlock" | grep origWhen                            # window=0
adb shell run-as io.github.ilnazziiazi.nightlock rm shared_prefs/nightlock.xml                                    # back to 22:00–06:00
```

Verified on Android 16: Home/Recents/Back don't escape, Call opens the dialer inside the kiosk,
release by alarm, relock after reboot (after first unlock), `dpm remove-active-admin`.
