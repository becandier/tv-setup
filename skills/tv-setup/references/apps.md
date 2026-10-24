# Что предлагать отключить

Покажи пользователю установленное (`pm list packages`) по категориям ниже. Для неизвестных имён смотри
`dumpsys package <имя>` и отчёт `tools/privacy.py`. Если всё равно непонятно, спроси, а не угадывай. Готовые
списки по моделям — в `examples/<модель>/debloat.txt`: в комментариях там написано, что на этой прошивке за что
отвечает.

## Рекомендую отключить: телеметрия, реклама, автоустановка

Ничего полезного для пользователя, только сбор данных и подсовывание приложений. Примеры, отключённые на
проверенной Mi TV Stick (Android 9):

| Пакет | Что это |
|---|---|
| `com.miui.tv.analytics` | телеметрия Xiaomi |
| `tv.alphonso.alphonso_eula` | Alphonso: ACR, распознавание того, что вы смотрите, для рекламы |
| `com.google.android.tvrecommendations` | рекомендации-реклама в стоковом лаунчере |
| `com.xm.webcontent`, `com.xiaomo.tv.milegal` | рекламный контент и юридические экраны Xiaomi |
| `android.autoinstalls.config.*` | тихая автоустановка партнёрских приложений |
| `com.mitv.download.service` | загрузчик рекламных приложений Xiaomi |
| `com.xiaomi.android.tvsetup.partnercustomizer` | партнёрские настройки первого запуска |

На других брендах то же самое называется иначе: TCL — `com.tcl.*` analytics/ads, Hisense — VIDAA ads, Sony —
Samba TV (`com.samba.*`). `tools/privacy.py` их находит и показывает, на чём основан вывод.

## Отключатся сами: стоковый лаунчер

`com.google.android.tvlauncher` (или `com.google.android.apps.tv.launcherx`, `com.mitv.tvhome`). Его отключает
`launcher/build-and-install.sh`, но только после того, как новый лаунчер стал Home. Вручную не отключай. Его
спутники можно добавить в список: `com.mitv.tvhome.atv`, `com.mitv.tvhome.michannel`.

## Спросить: «нужно ли вам?»

| Категория | Примеры | Что пропадёт |
|---|---|---|
| Голосовой ассистент | `com.google.android.katniss`, `com.google.android.speech.pumpkin`, `com.google.android.tts` | кнопка микрофона на пульте, голосовой поиск |
| Chromecast | `com.google.android.apps.mediashell` | трансляция с телефона |
| Трансляция производителя | `com.mitv.milinkservice` (Mi Cast) | трансляция экрана с телефона Xiaomi |
| Стрим-сервисы | `com.netflix.ninja`, `com.amazon.amazonvideo.livingroom`, `com.google.android.videos`, `com.google.android.youtube.tvmusic` | сами сервисы |
| Игры, медиаплеер | `com.google.android.play.games`, `com.mitv.videoplayer` | игры Google Play, встроенный плеер |
| Обновление прошивки | `com.xiaomi.mitv.updateservice` и аналоги | обновления системы. **Рекомендую оставить**: без них не будет исправлений безопасности |

## Не трогать никогда

Системные пакеты (`com.android.*` из списка в AGENTS.md, `android`, `com.android.systemui`, Play Services,
Play Store), клавиатура, сервисы пульта и Bluetooth, приложение ТВ-тюнера и входов HDMI. `tools/disable.sh`
откажется применять список, где они есть.

## Удаление

Только по прямой просьбе: `pm uninstall --user 0 <пакет>`. Системная копия остаётся, вернуть можно
`cmd package install-existing <пакет>`. `tools/tv-restore.sh` делает это сам для всего, чего нет по сравнению со
снимком.
