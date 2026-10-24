# Лаунчер: флаги и проверка

Сборка и установка — `launcher/build-and-install.sh` из корня репо. Флаги передаются переменными окружения,
каждый раз: при переустановке Android выключает accessibility-помощник и сбрасывает часть разрешений.

| Флаг | Что даёт | Чем выдаётся на ТВ |
|---|---|---|
| `PRAYER_CITY=<slug>` | таблица намаза города в APK: строка под часами, плашка в момент намаза, капсула в заставке. Без флага намаза нет | — (сборка) |
| `HOME_KEY_SERVICE=1` | кнопка Home пульта, которая приходит как `KEYCODE_ALL_APPS` (Xiaomi); через этот же сервис таймер сна усыпляет приставку | accessibility-сервис |
| `PRAYER_OVERLAY=1` | плашка «Время намаза» поверх любого приложения | `appops SYSTEM_ALERT_WINDOW` |
| `VPN_PILL=1` | пилюля VPN выключает VPN любого приложения без диалога | `appops ACTIVATE_VPN` |
| `USAGE_DOCK=1` | док из самых используемых за 14 дней приложений | `appops GET_USAGE_STATS` |
| `SLEEP_TIMER=1` | обратный отсчёт таймера сна поверх приложений | `appops SYSTEM_ALERT_WINDOW` |
| `PRAYER_DREAM=1` | заставка «Намаз» вместо текущей (печатает прежнюю) | `settings put secure screensaver_components` |
| `INPUT_KEY_SERVICE=1` | кнопка Input пульта (Google TV без стокового лаунчера) | accessibility-сервис |

Хиджра (`hijri_date`), строка намаза (`prayer_times`), пилюли VPN и «Сон», авто-док включены в
`launcher/app/src/main/res/values/config.xml` этой ветки. Тема (`tools/theme.sh`) может выключить любой из них
теми же ключами, список ключей — `themes/README.md`.

## Проверка после установки

```sh
TV_HOST=<ip> tools/venv/bin/python -I tools/tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
TV_HOST=<ip> tools/venv/bin/python -I tools/tv.py --screenshot shots/home.png HOME
TV_HOST=<ip> tools/venv/bin/python -I tools/tv.py 'dumpsys alarm | grep -A2 action.PRAYER'          # будильник следующего намаза
TV_HOST=<ip> tools/venv/bin/python -I tools/tv.py 'am broadcast -n com.gathin.tvhome/.PrayerNotice\$Shell'   # плашка сейчас
TV_HOST=<ip> tools/venv/bin/python -I tools/tv.py 'dumpsys dreams | grep mCurrentDreamName'
```

`$Shell` раскрывает оболочка ТВ: команду бери в одинарные кавычки и оставляй `\$Shell`. В двойных кавычках
компьютер снимет обратную косую черту, и ТВ получит пустое имя класса.

- **Таймер сна:** `am broadcast -n com.gathin.tvhome/.SleepTimer\$Shell --ei minutes 1` — через минуту
  приставка уснёт, если не нажать кнопку пульта; `--ei minutes 0` отменяет.
- **Пилюля VPN:** проверь, что после переключения пилюлей видна сеть `VPN` в
  `dumpsys connectivity | grep NetworkAgentInfo`.
- **Работа пользователя:** если ТВ смотрят (в `dumpsys activity activities | grep mResumedActivity` чужое
  приложение, идёт воспроизведение), не нажимай кнопки и не переустанавливай — дождись паузы.
