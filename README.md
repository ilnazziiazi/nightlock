# tilltan

**till tan** — "until morning" (*tan* is Kazakh for morning). Locks your phone for the night.
Default window is 22:00–06:00. At night the screen shows only "till tan", a countdown and a
**Call** button. There is no way out: no Home, no Recents, no notification shade, no reboot
trick, no safe mode, no clock change.

Built on Android **Device Owner** → kiosk mode (lock task). No background services, no
battery drain: a single alarm scheduled for the next window boundary.

## Build

```sh
brew install openjdk@21 && brew install --cask android-commandlinetools android-platform-tools
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > android/local.properties
yes | sdkmanager --licenses; sdkmanager "platforms;android-36" "build-tools;36.0.0"
cd android && ./gradlew :app:assembleDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`. Requires Android 13+.

## Install and grant permissions

Device Owner can only be assigned over adb, and only while the device has **no accounts
and no secondary users**. It is a one-time step.

### 1. Enable USB debugging

Settings → About phone → tap "Build number" 7 times.
Settings → System → Developer options:
- **USB debugging** — on.
- On some vendor ROMs (OxygenOS/ColorOS, MIUI/HyperOS) there is an extra switch such as
  **Disable permission monitoring** or **USB debugging (Security settings)**. Turn it on,
  otherwise adb commands may silently fail.

Plug in the cable, accept the debugging prompt on the phone:

```sh
adb devices        # status must be "device", not "unauthorized"
```

### 2. Remove what blocks `set-device-owner`

Android refuses if any account or extra user exists. Check:

```sh
adb shell dumpsys account | grep "Account {"   # must be empty
adb shell pm list users                        # only UserInfo{0:...}
```

Remove (you will add everything back afterwards):
- Settings → Users & accounts → **every** account: Google, vendor account, WhatsApp,
  Telegram, mail, etc. `dumpsys account` shows what is left.
- **Private Space** (Settings → Security & privacy → Private space) — delete.
- **App cloning / dual apps** — disable all.
- Guest and secondary users (Settings → Users) — delete.

### 3. Install and assign Device Owner

```sh
adb install -t android/app/build/outputs/apk/debug/app-debug.apk
adb shell dpm set-device-owner kz.tilltan/.AdminReceiver
```

Expected: `Success: Device owner set to package ComponentInfo{kz.tilltan/kz.tilltan.AdminReceiver}`.

Errors:
- `already some accounts on the device` — an account is left, see `dumpsys account`.
- `already several users on the device` — Private Space / clone / guest is left, see `pm list users`.
- `Not allowed to set the device owner` with no details — check the vendor debugging switch from step 1.

### 4. Restore accounts and open the app

Add Google and the other accounts back. Open **tilltan** — it should say
"Device owner: yes. Lock is armed." Set the times. Done.

The `-t` flag is always required: the APK is built with `testOnly`, which is what makes
the emergency exit below possible.

## Update

```sh
cd android && ./gradlew :app:assembleDebug && adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```

Signed with the debug key `~/.android/debug.keystore`. If you lose it you cannot update
the app, only remove device owner and reinstall.

## Emergency exit

There is no way out of the lock from the phone itself. That is the point. The only exit
is a computer with adb:

```sh
adb shell dpm remove-active-admin kz.tilltan/.AdminReceiver
adb uninstall kz.tilltan
```

The first command removes device owner and all restrictions; the lock screen releases
immediately. It works only because the APK is built with `android:testOnly="true"`.

## Inside

Five Kotlin files, zero dependencies. See [CLAUDE.md](CLAUDE.md).
