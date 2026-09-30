# Общий контракт промптов — 2026-10-01

`prompt-contract` включён в обе Gradle-сборки. commonMain содержит единый PromptJson, вложенные DTO, проверку каталога, manifest и сохранение дополнительных данных. JVM target обслуживает Android и Desktop; модуль не зависит от Android, Java API, Room или Compose. Выходные файлы двух roots разделены; версии Kotlin берутся из соответствующего каталога.

Клиенты используют typealias для прежних названий DTO и собственные доменные/Room-адаптеры. Формат сохраняет source_id, переменные с default_value/type/examples, variant_id/type/id/priority, metadata и даты. Старые Desktop camelCase-поля читаются через JsonNames. Авторский ID исправлен; даты принимают UTC Instant и старый LocalDateTime.

PromptStorage использует имеющуюся variables_json: версионированный envelope содержит значения переменных и исходный DTO. Старые map-only значения читаются. Новая Room-схема не требуется. Поэтому варианты и определения переменных Desktop сохраняются вместе с записью, хотя UI вариантов пока отличается от Android. Android также сохраняет свой действующий prompt_variants_json.

CatalogContract проверяет строковые ID/title до coercion DTO, отклоняет пустой snapshot и повторные ID. Заголовки могут совпадать. CatalogManifest версии 1 проверяет точный набор файлов, количество, SHA-256, соответствие ID и tombstones. В ZIP используется имя catalog.manifest: старые клиенты, читающие *.json, игнорируют его. Новый клиент проверяет manifest целиком до записи. Legacy ZIP сохраняет режим upsert без удаления отсутствующих ID.

Удаляются только явно указанные deleted_ids, только публичные записи без избранного и заметок. Обновления сохраняют личные записи, избранное и заметки. Чтение/слияние/удаление/вставка выполняются в одной DAO-транзакции в обоих клиентах.

Проверки: общие DTO/storage/manifest tests, реальный ZIP всех 932 записей в обоих roots, Desktop DTO→Entity→Domain→Export, реальные Desktop Room migrations 1→3 и tombstones. Android unit/lint/APK и компиляция instrumentation tests проверены; повторный запуск instrumentation на текущем этапе недоступен без подключённого устройства. Ранее переход 4→8 проверен на Android 11.
