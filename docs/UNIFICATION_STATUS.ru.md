# Объединение приложений — состояние 2026-10-01

## Рабочие проекты

- `G:/Android/AndroidStudioProjects/aipromptmaster`: Android (`app`), Desktop (`desktop`), общий KMP-контракт (`prompt-contract`). Ветка `codex/unify-android-desktop`.
- `G:/Android/OpenideProjects/aiprompts`: 932 публичных промпта, schema/validator/package, один catalog publisher. Ветка `codex/catalog-cleanup`.

## Выполненные пункты

1. Проверены исходные Git bundles и ZIP локальных важных файлов.
2. Собраны исходные приложения и зафиксирован baseline.
3. Android-копии сверены; основной Android сохранён, старые отличия архивированы.
4. Desktop перенесён с историей Git и проверен в новом расположении; старый каталог aiprompts-kmp вынесен в архив.
5. Подготовлены отдельные Android/Desktop CI и release по точному тегу; Android/Desktop и catalog remote CI прошли в draft PR. Удалён автопуш version.properties. Signed release отдельно требует проверки.
6. Общий DTO, legacy aliases, storage envelope, manifest и адаптеры подключены к обоим клиентам.
7. Исправлены destructive fallback и импорт: транзакции, Mutex, строгие ID/snapshot, проверки manifest/checksum, явные tombstones с защитой личных записей, избранного и заметок. Android схемы 1–3 отсутствуют; не имитируем проверку. Переход 4→8 ранее прошёл на Android 11; instrumentation повторён на Android 12 Emulator.
8. Личные файлы Desktop отделены от Git в `.aiprompts/personal_prompts`. Редактирование делает запись личной. JSON-экспорт исправлен и использует общий DTO. CLI candidate/stage готовит публичные записи на отдельный просмотр, снимает личные флаги и notes, не меняет исходный экспорт и не публикует автоматически.
9. Schema/validator, воспроизводимый ZIP, manifest, контрольные суммы и единственный publisher каталога готовы и проверены локально.
10. Python, исходный Desktop, orphan Kotlin и локальные входные/выходные файлы вынесены в архив. Полная копия старого Compose с Git проверена; исходная папка содержит частичный остаток, который Windows не позволил переименовать. В ней ARCHIVED.md. Рекурсивное удаление отклонено автоматической проверкой без подробной причины; остаток не удалён.
11. Удалены повторная serialization dependency, неиспользуемые kapt/viewBinding, старые wire DTO и невызываемый прямой GitHub publisher. Поддерживаемые библиотеки массово не обновлялись. Исправлены потеря переменных, author ID, UTC-даты, JSON-экспорт, сохранение избранного/рейтинга Android при редактировании и фильтрация Desktop по тегам.
12. Локальные unit/contract tests, Android lint/APK, сборка instrumentation APK, Desktop portable и изолированный runtime smoke завершены. 932 записи и 932 wire documents в изолированной БД. Полный сетевой Selenium suite, installer, signed release и новые общие UI-экраны в этот этап не входят.

## Проверка

Из корня приложения:

```powershell
.\gradlew.bat :prompt-contract:jvmTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Из `desktop`:

```powershell
.\gradlew.bat :prompt-contract:jvmTest :shared:desktopTest --tests '*UseCaseTest' --tests '*PromptSynchronizerTest' --tests '*LegacyCatalogSafetyTest' --tests '*PromptExtensionsTest' --tests '*CatalogDatabaseSafetyTest' --tests '*PromptWireRoundTripTest' :desktopApp:createDistributable
```

Реальный архив каталога проверяется common module test при `PROMPT_CATALOG_ARCHIVE=<полный путь к prompts.zip>`; файл зарегистрирован как test input, поэтому его изменение не пропускается Gradle cache. Без внешнего архива этот acceptance test skipped, остальные contract tests автономны.

Артефакты: `app/build/outputs/apk/debug`, `desktop/desktopApp/build/compose/binaries/main/app/AIPrompts`. Portable нужно переносить целой папкой с runtime, app и launcher.

Логи: `C:/Users/ArnyPC/.codex/unification-android-acceptance.log`, `unification-desktop-acceptance.log`, `desktop-real-migrations.log`. Android 12 Emulator: 7 instrumentation tests прошли. Последние логи: mobile-providers-final.log, mobile-providers-final-instrumentation.log, desktop-providers-final.log в C:/Users/ArnyPC/.codex. Android: 37 unit tests; Desktop: 151 passed / 6 skipped.

## Архивы и восстановление

`G:/Android/ProjectArchives/2026-10-01-AiPrompts` содержит полный старый Compose, исходный Desktop, Python, конфигурации и материалы. Локальные секреты не включены в новые коммиты. Исходные bundles/ZIP: `C:/Users/ArnyPC/.codex/backups/aiprompts-unification-20260930-215834`. Android master не изменён; исходная Desktop история также сохранена в Git ветке `codex/desktop-source-history` и объединена как ancestor новой ветки.

## Улучшение промпта из Python

- [x] Исходники `ai_dialog.py` и `prompt_editor.py` сохранены в архиве Python.
- [x] В Desktop добавлена кнопка «Улучшить» в карточке промпта, в широком и узком режиме.
- [x] Можно выбрать RU/EN, ID модели, пожелания, температуру, лимит токенов и потоковый ответ. Подключение берётся из настроек LLM; пользовательский OpenAI-совместимый endpoint может работать без ключа.
- [x] Есть предпросмотр, отмена, время первого ответа и общее время. Ошибочный, оборванный или обрезанный ответ нельзя применить.
- [x] «Применить к черновику» меняет только выбранный язык и делает запись личной. Сохранение выполняется отдельно; чат и публичный каталог не изменяются.
- [x] Шесть автономных тестов проверяют ответы, параметры, отмену и явное применение. Desktop portable собран.
- [x] Настоящие HTTP-запросы проверены на локальном OpenAI-совместимом сервере: streaming/non-streaming, параметры и работа без API key. Тест компонента подтверждает применение только к выбранному языку и сохранение отдельным действием.
- [x] Проверены PNG реального Compose-диалога при ширине 1100 и 480 px через ImageComposeScene, без computer-use. Предпросмотр и генерация подняты выше параметров. Исправлена видимость сохранения личного черновика публичной записи в узком режиме.
- [ ] Реальный запрос к выбранному пользователем внешнему провайдеру не выполнен.

Архивирование Python не означало полного переноса всех его функций. Отдельный просмотр рассуждений и приблизительная скорость токенов Python-диалога в эту реализацию не включены.

Общий адаптивный UI возможен через ширину окна; направление сохранено в ADAPTIVE_UI_DIRECTION.ru.md. Android multi-backstack и свайпы сохранены. Текущий этап объединяет репозиторий и формат данных; Navigation 3/Decompose и два UI пока остаются отдельными реализациями.

## Мобильная версия и провайдеры

- [x] Улучшение промпта добавлено в Android: RU/EN, предпросмотр, отмена, применение к черновику и отдельное сохранение.
- [x] Профили провайдеров доступны в обоих клиентах: OpenRouter, шаблоны OpenAI/LM Studio/Ollama и свой OpenAI-совместимый сервер.
- [x] Отдельные ключи и модели, проверка подключения, поиск моделей, шифрованное хранение; прежний OpenRouter сохранён.
- [x] Мобильные PNG и доступность кнопки при открытой клавиатуре проверены на Android 12 Emulator.

Инструкция: [PROVIDERS_AND_IMPROVEMENT.ru.md](PROVIDERS_AND_IMPROVEMENT.ru.md).
