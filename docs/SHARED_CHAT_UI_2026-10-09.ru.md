# Общие список чатов и параметры — 9 октября 2026

ConversationList и ConversationRow подключены Android/Desktop. Общие отступы, перенос названия и превью, пустая история, стабильные ключи и выделение выбранного чата. Android сохраняет долгое нажатие и подтверждение удаления; Desktop сохраняет переименование, архивирование и меню удаления. Форматирование даты остаётся платформенным адаптером.

ChatParametersPane и SystemPromptEditor используются обоими клиентами. Desktop получает текст напрямую из состояния текущего чата без второй локальной копии; очистка и правки возвращаются владельцу состояния. Модель, статистика и генерация передаются слотами. GenerationParameters с температурой, лимитом ответа, Top P и количеством сообщений перенесён в shared-ui и подключён Desktop.

Android пока предоставляет только системный промпт: настройки генерации для Android ещё не подключены к его хранению и HTTP-запросам. Перенос компонента в общий UI не означает наличия этих возможностей на обеих платформах. Черновики и навигация продолжают принадлежать Android ViewModel/Navigation 3 и Desktop Decompose.

Удалены неиспользуемые Desktop SystemPromptSection, ParameterSlider и ParameterIntField. Новые панели имеют собственный фон и цвет содержимого; они работают и внутри внешнего экрана, и отдельно в диалоге.

Проверки: Android assembleDebug/testDebugUnitTest, app lint и shared-ui lint прошли; после исправления поверхности повторены debug/unit/shared-ui lint. Полный Desktop suite: 184 теста, 176 passed / 8 opt-in skipped / 0 failures. Portable собран. SharedConversationScreenshotTest рисует реальные ChatSidebar/ParametersPanel с данными при 360/720 px; проверены итоговые PNG шириной 360. Emulator и computer-use не применялись. Реальные сетевые запросы в этом этапе не повторялись.

Логи: C:/Users/ArnyPC/.codex/common-chat-final-android.log и common-chat-final-desktop.log. PNG: C:/Users/ArnyPC/.codex/backups/aiprompts-unification-20260930-215834/screenshots/shared-chat-history-360.png и shared-chat-parameters-360.png.

Следующие задачи: подключение параметров генерации Android, проверка действий меню/редактирования/вложений в реальном окне, проверка финальных release-пакетов после всех изменений UI. Предыдущий подписанный APK относится к fc0fe69, а не к этому переносу.
