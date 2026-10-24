# tilltan

«till tan» — до утра (tan = утро, каз.). Android-приложение, которое по расписанию
(по умолчанию 22:00–06:00) превращает телефон в экран «till tan», с которого нельзя выйти.
Аналог Cold Turkey для всего устройства, без блокировки отдельных сайтов/приложений.

## Принципы

- **Ponytail.** Минимум кода, файлов, зависимостей. Сначала stdlib/framework, потом всё остальное.
- **Zero deps.** Kotlin + Android framework. Без Compose, AppCompat, Material, Coroutines,
  Hilt и т.п. Единственная зависимость — junit (test only). Не добавлять.
- **Zero battery.** Нет сервисов, нет WorkManager, нет polling. Один exact-алярм
  (`AlarmManager.setAlarmClock` — единственный тип, который OEM-прошивки не размазывают в окно ±1ч) на ближайшую границу окна. Всё.
- **Нет выхода ночью.** Это фича. Не добавлять кнопки «пауза», «отложить», «аварийный выход».
  Аварийный выход — только adb (см. README).

## Как работает

Device Owner (ставится через `adb shell dpm set-device-owner`) → `startLockTask()` (kiosk).

- `Schedule.kt` — окно в минутах дня в SharedPreferences + чистые `isActive`, `nextBoundary`.
  Единственная логика с тестом (`ScheduleTest.kt`). `start == end` = выключено.
- `SyncReceiver.kt` — единственная точка входа: алярм / BOOT_COMPLETED / TIME_SET /
  TIMEZONE_CHANGED → `sync(ctx)`: переставить алярм, применить device-owner политику (`arm`),
  запустить `LockActivity` если окно активно, иначе отпустить.
- `LockActivity.kt` — «till tan», `Chronometer countDown` до конца, кнопка Call (`ACTION_DIAL`).
  Back заглушен, `onResume → startLockTask()`. `singleTask`, `lockTaskMode=if_whitelisted`.
- `MainActivity.kt` — два `TimePickerDialog`, статус device owner. `onResume → sync()` —
  поэтому ночью настройки недостижимы: экран сразу перекрывается `LockActivity`.
- `AdminReceiver.kt` — пустой `DeviceAdminReceiver`, цель для `dpm`.
- Device-owner политика (в `arm`): lock task packages = [tilltan, default dialer];
  features = KEYGUARD | GLOBAL_ACTIONS; restrictions = DISALLOW_SAFE_BOOT, DISALLOW_CONFIG_DATE_TIME.
- `android:testOnly="true"` в манифесте — чтобы `dpm remove-active-admin` работал. Поэтому
  всегда `adb install -t`.

## Сборка

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # brew install openjdk@21
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```

SDK: `/opt/homebrew/share/android-commandlinetools` (brew cask `android-commandlinetools`),
путь в `android/local.properties` (не в git). compileSdk/targetSdk 36, minSdk 33, AGP 8.13,
Gradle 8.14 (wrapper; системный gradle 9.x с AGP 8 не работает).

Debug-ключ `~/.android/debug.keystore`
— им подписан установленный device owner, не терять, иначе обновить APK нельзя.

## Чего не делать

- Не выносить логику в сервисы/WorkManager «для надёжности» — device owner освобождён от
  ограничений запуска activity из фона, алярма достаточно.
- Не добавлять per-day расписание, профили, статистику, уведомления — пока явно не попросят.
- Не менять `applicationId` / имя `AdminReceiver` — это сломает уже установленный device owner.
