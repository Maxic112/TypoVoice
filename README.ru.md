<p align="center">
  <img src="docs/images/icon.png" width="112" alt="Иконка TypoVoice">
</p>
<h1 align="center">TypoVoice</h1>
<p align="center">Голосовой ввод в любое приложение на Android с распознаванием на вашем собственном сервере.</p>

<p align="center">
  <a href="README.md"><img alt="English" src="https://img.shields.io/badge/🇬🇧_English-gray?style=for-the-badge"></a>
  <a href="README.ru.md"><img alt="Русский" src="https://img.shields.io/badge/🇷🇺_%D0%A0%D1%83%D1%81%D1%81%D0%BA%D0%B8%D0%B9-%D0%B2%D1%8B%D0%B1%D1%80%D0%B0%D0%BD-0969DA?style=for-the-badge"></a>
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-12%2B-3DDC84?style=flat-square&logo=android&logoColor=white">
  <img alt="API" src="https://img.shields.io/badge/API-31%2B-brightgreen?style=flat-square">
  <a href="https://github.com/Maxic112/TypoVoice/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/Maxic112/TypoVoice?style=flat-square"></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/license-MIT-blue?style=flat-square"></a>
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?style=flat-square&logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white">
</p>

## О приложении

TypoVoice записывает речь, отправляет её на ваш transcribe-text-server (API, совместимый с OpenAI Audio API)
и вставляет распознанный текст в поле ввода, на котором стоит фокус в любом приложении: мессенджере,
заметках, браузере. Это не клавиатура: ваша обычная клавиатура остаётся, а диктовка запускается плавающей
кнопкой, плиткой, кнопкой громкости или системным голосовым вводом.

<!-- Скриншоты: добавьте docs/images/{home,settings,launch}.ru.png и раскомментируйте.
<p align="center">
  <img src="docs/images/home.ru.png" width="230" alt="Главный экран">
  <img src="docs/images/launch.ru.png" width="230" alt="Способы запуска">
  <img src="docs/images/settings.ru.png" width="230" alt="Настройки">
</p>
-->

## Возможности

- 🎙️ **Диктовка куда угодно** — текст сразу попадает в поле ввода, а если поля нет — в буфер обмена.
- 🖥️ **Свой сервер** — работает с transcribe-text-server: модель, язык, подсказка, VAD и потоковый режим настраиваются для каждого сервера.
- 🔁 **Несколько серверов** — политики выбора и автоматическое переключение при сбое; неудачные отправки можно повторить.
- ✨ **Постобработка** — LLM-пресеты на сервере (пунктуация, чистка, свой промпт с `{text}`).
- 🚀 **Запуск как удобно** — плавающая кнопка, плитка в шторке, кнопка громкости, иконка в меню приложений, виджет, ярлык, цифровой ассистент и системный голосовой ввод, «Поделиться» аудио или видео для расшифровки. Каждый способ можно выключить.
- 🕘 **История** последних диктовок.
- 🌐 **Русский и английский** интерфейс: по языку системы или по выбору в приложении.
- ⬆️ **Обновления из GitHub Releases** — проверка раз в день, установка из приложения.

## Установка

1. Скачайте последний `TypoVoice-*.apk` со страницы [Releases](https://github.com/Maxic112/TypoVoice/releases/latest).
2. Откройте его на телефоне и разрешите установку приложений из этого источника, когда Android спросит.
3. В TypoVoice добавьте сервер (базовый URL вида `https://stt.example.com/api/transcribe/v1` и API-ключ)
   и включите спецвозможность, когда приложение попросит.

Нужны Android 12 или новее и работающий transcribe-text-server.

### «Ограниченная настройка» при включении спецвозможностей

Android 13+ блокирует специальные возможности для приложений, установленных из APK-файла. Если появилось
окно «Ограниченная настройка»:

1. Один раз попробуйте включить TypoVoice в **Настройки → Специальные возможности** и закройте окно.
2. Откройте **Настройки → Приложения → TypoVoice** (или нажмите «Открыть настройки приложения» в TypoVoice),
   нажмите **⋮** в правом верхнем углу и выберите **«Разрешить ограниченные настройки»**; подтвердите PIN-кодом
   или отпечатком.
3. Снова включите TypoVoice в специальных возможностях.

Названия пунктов и место меню у разных производителей могут немного отличаться. При установке Google Play
Protect может предупредить о неизвестном приложении: TypoVoice нет в Google Play, поэтому Play Protect его пока
не знает.

### Разрешения

| Разрешение | Зачем |
|---|---|
| Микрофон | Запись идёт только во время диктовки (служба переднего плана с уведомлением). |
| Спецвозможности | Находит поле ввода в фокусе, вставляет текст и показывает плавающую кнопку. Содержимое экрана не сохраняется и никуда не отправляется. |
| Уведомления | Уведомление о записи и об ошибках. |
| Локальная сеть | Доступ к серверу в вашей локальной сети (Android 17+). |
| Установка приложений | Установка обновлений, скачанных из GitHub Releases. |

Аудио отправляется только на настроенные вами серверы. Больше ничего с устройства не уходит.

## Сборка из исходников

Нужны JDK 21 и Android SDK.

```bash
git clone https://github.com/Maxic112/TypoVoice.git
cd TypoVoice
cp local.properties.example local.properties   # укажите sdk.dir; dev-сервер необязателен
./gradlew :app:assembleDebug                   # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest
```

В `local.properties` можно указать dev-сервер (`typovoice.dev.*`): debug-сборка добавит его при первом
запуске. В release-сборки он не попадает.

## Участие

См. [CONTRIBUTING.md](CONTRIBUTING.md) (на английском). Уязвимости: [SECURITY.md](SECURITY.md).

## Лицензия

[MIT](LICENSE) © 2026 Maxim Veretelnikov
