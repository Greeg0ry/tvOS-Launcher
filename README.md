# tvOS Launcher RU

Лаунчер для Android TV и ТВ-приставок с интерфейсом в стиле tvOS: приложения, часы, погода и управление с пульта. Поддерживает русский и английский языки. Форк [tvOS Launcher](https://github.com/techgenius1/tvOS-Launcher). Первая целевая приставка — **X4Q Pro, Android 11**; проверка на устройстве ещё предстоит.

![Главный экран tvOS Launcher (скриншот оригинального проекта)](screenshots_en/Home.png)

## Установка

1. Скачайте `tvos-home.apk` из [релизов форка](https://github.com/Greeg0ry/tvOS-Launcher/releases). Перенесите APK на приставку и откройте файловым менеджером, разрешив ему установку из неизвестных источников.
2. Откройте **tvOS Launcher**, выберите **Русский** и пройдите первоначальную настройку. Дополнительные разрешения нужны только для соответствующих функций.
3. В системных настройках выберите **tvOS Launcher** приложением главного экрана. Названия меню зависят от прошивки.

Через ADB (отладка включена, компьютер и приставка в одной сети):

```sh
adb connect IP_ПРИСТАВКИ:5555
adb install -r tvos-home.apk
adb shell am start -n com.techgenius.tvoslauncher/.MainActivity --es lang ru
adb shell cmd package set-home-activity --user 0 com.techgenius.tvoslauncher/.MainActivity
```

Для первой установки на X4Q Pro оставьте штатный лаунчер включённым. Сначала проверьте кнопку Home, запуск после перезагрузки и работу пульта. Для выбора обоев с USB и восстановления настроек на Android 11 может понадобиться разрешение «Доступ ко всем файлам».

Если установлен оригинальный tvOS Launcher, экспортируйте настройки и удалите его перед установкой форка: подписи APK различаются. Для обновлений форка сохраняйте тот же ключ подписи.

Сборка из исходников: JDK 17+, Android SDK (`build-tools;34.0.0`, `platforms;android-34`), затем `bash build.sh` или `powershell -ExecutionPolicy Bypass -File .\build.ps1`. APK: `build/tvos-home.apk`.
