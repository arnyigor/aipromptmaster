# Общая навигация и карточки моделей — 9 октября 2026

- SyncTabPager перенесён из Desktop в shared-ui и подключён к Android. Источник выбранной вкладки — платформенный владелец маршрутов. Программное переключение не меняет промежуточные вкладки; пользовательский свайп выбирает раздел после завершения жеста.
- Android сохраняет Navigation 3, отдельные back stacks и владельцев сохранённого состояния/ViewModel. Desktop сохраняет Decompose и независимые стеки. Импорт остаётся только Desktop.
- ModelRow заменяет раздельную верстку карточок моделей Android/Desktop. Название переносится до двух строк, выбранная строка получает общие цвет и семантику RadioButton. Android сохраняет избранное, рейтинг, модальности и проверку доступности через слоты общего компонента.
- SettingsPagerNavigationTest проверяет быстрые программные переходы и восстановленную вкладку при другом начальном положении pager. Сценарии проходят на Desktop и проверяют общий компонент.
- PNG диалога выбора модели содержит настоящие карточки с длинным названием, описанием и контекстом; пустой список больше не скрывает проблемы карточек при визуальной проверке.

Оставшиеся различия: панели параметров/системного промпта и список чатов ещё имеют отдельную верстку; платформенные адаптеры хранения, навигации и выбора файлов сохраняются по назначению.

Логи: C:/Users/ArnyPC/.codex/shared-navigation-desktop.log, shared-models-android.log, shared-ui-final-desktop.log, shared-ui-final-android.log.

## Итоговая проверка

- Полный Desktop suite после переноса и удаления неиспользуемых DesktopPromptDetailLayout, MobilePromptDetailLayout и ChatInput: 183 теста, 175 passed / 8 opt-in skipped / 0 failures. Повторная сборка portable, MSI и EXE прошла.
- Android debug APK, unit tests, app lint и shared-ui lint прошли.
- Подписанный Android APK проверен workflow 37898684463 на fc0fe6924cf3b9557a7d0402ca3bd0f0eeaf6bfc. Подпись v1/v2 действительна, сертификат совпадает с опубликованной 2.0.0. Режим verify_only не создавал тег или release. Последующие изменения затрагивают только Desktop packaging и удаление неиспользуемого Desktop UI.
- Desktop packageVersion больше не зафиксирован на 1.0.0: приоритет -PversionName, VERSION_NAME, затем общий version.properties (сейчас 2.0.0). Проверяются формат и ограничения MSI. Upgrade UUID сохранён.
- Переустановка изменённого пакета 1.0.0 получила MSI SecureRepair 1603. После исправления версии установка MSI 2.0.0 поверх изолированной тестовой 1.0.0 завершилась кодом 0. Установленный launcher запущен с отдельными user.home/namespace, загрузил 932 записи. Эта установка была до удаления неиспользуемых функций; финальные пакеты после удаления также собраны, повторная установка той же версии не заявляется.
- Нативные PNG установленного приложения проверены при 360 и 1440 px; также сделаны снимки 760/480. Папка screenshots/installed-desktop-*.png в локальном backup. Emulator/computer-use не применялись.
- Данные миграционного протокола от 4 октября в UNIFICATION_STATUS.ru.md — отдельное пользовательское изменение, не включённое в эти коммиты.

Артефакты: desktop/desktopApp/build/compose/binaries/main/msi/AIPrompts-2.0.0.msi и exe/AIPrompts-2.0.0.exe. Полный portable — папка app/AIPrompts с runtime. Signed verification APK — C:/Users/ArnyPC/.codex/backups/aiprompts-unification-20260930-215834/signed-verification-fc0fe69/AiPromptMaster-vverification.apk.
