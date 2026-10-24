# tilltan

**till tan** — «до утра» (*tan* — утро по-казахски). Блокирует телефон на ночь.
По умолчанию 22:00–06:00. Ночью на экране только «till tan», таймер и кнопка **Call**.
Выйти нельзя: ни Home, ни Recents, ни шторка, ни перезагрузка, ни safe mode, ни перевод часов.

Работает через Android **Device Owner** → kiosk-режим (lock task). Фоновых сервисов нет,
батарею не ест: один будильник на ближайшую границу окна.

## Сборка

```sh
brew install openjdk@21 && brew install --cask android-commandlinetools android-platform-tools
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > android/local.properties
yes | sdkmanager --licenses; sdkmanager "platforms;android-36" "build-tools;36.0.0"
cd android && ./gradlew :app:assembleDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`.

## Установка и выдача прав (OnePlus 15R, Android 16 / OxygenOS 16)

Device Owner можно назначить только через adb и только пока на телефоне **нет ни одного
аккаунта**. Делается один раз.

### 1. Включить отладку

Настройки → О телефоне → Версия → 7 раз тап по «Номер сборки».
Настройки → Система → Для разработчиков:
- **Отладка по USB** — вкл.
- **Отключить мониторинг разрешений** (Disable permission monitoring) — вкл. Специфика
  OxygenOS/ColorOS: без этого часть adb-команд молча блокируется.

Подключить кабель, на телефоне разрешить отладку с этого компьютера:

```sh
adb devices        # должен быть статус device, не unauthorized
```

### 2. Убрать всё, что мешает `set-device-owner`

Android откажет, если на устройстве есть аккаунты или дополнительные пользователи.
Проверить:

```sh
adb shell dumpsys account | grep "Account {"   # должно быть пусто
adb shell pm list users                        # только UserInfo{0:...}
```

Удалить (потом всё вернётся):
- Настройки → Пользователи и аккаунты → **все** аккаунты: Google, OnePlus/HeyTap,
  WhatsApp, Telegram, почта и т.д. (`dumpsys account` покажет, что осталось).
- **Private Space** (Настройки → Безопасность и конфиденциальность → Private space) — удалить.
- **Клоны приложений** (Настройки → Приложения → Клонирование приложений) — выключить все.
- Гости и второй пользователь (Настройки → Пользователи) — удалить.

### 3. Поставить и назначить Device Owner

```sh
adb install -t android/app/build/outputs/apk/debug/app-debug.apk
adb shell dpm set-device-owner kz.tilltan/.AdminReceiver
```

Ожидаемый ответ: `Success: Device owner set to package ComponentInfo{kz.tilltan/kz.tilltan.AdminReceiver}`.

Ошибки:
- `already some accounts on the device` — остался аккаунт, см. `dumpsys account`.
- `already several users on the device` — остался Private Space / клон / гость, см. `pm list users`.
- `Not allowed to set the device owner` без деталей — проверить «Отключить мониторинг разрешений».

### 4. Вернуть аккаунты и открыть приложение

Добавить обратно Google и остальные аккаунты. Открыть **tilltan** — должно быть
«Device owner: yes. Lock is armed.» Выставить время. Готово.

Флаг `-t` обязателен всегда (APK собран с `testOnly`, это и даёт аварийный выход ниже).

## Обновление

```sh
cd android && ./gradlew :app:assembleDebug && adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```

Подпись — debug-ключ `~/.android/debug.keystore`. Если его потерять, обновить приложение
будет нельзя (только снять device owner и переустановить).

## Аварийный выход

Ночью из приложения выйти нельзя — так задумано. Единственный способ — компьютер и adb:

```sh
adb shell dpm remove-active-admin kz.tilltan/.AdminReceiver
adb uninstall kz.tilltan
```

Первая команда снимает device owner и все ограничения, экран блокировки сразу отпускает.
Работает только потому, что APK собран с `android:testOnly="true"`.

## Что внутри

Пять Kotlin-файлов, без зависимостей. Подробности — в [CLAUDE.md](CLAUDE.md).
