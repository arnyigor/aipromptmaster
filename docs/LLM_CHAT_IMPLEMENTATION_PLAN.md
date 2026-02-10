# План модернизации чата LLM в AI Prompts KMP

## 📋 Общая информация

**Дата создания:** 2025-02-10  
**Статус:** В разработке  
**Приоритет:** Высокий  
**Оценка времени:** 14-18 дней

## 🎯 Требования

На основе выбранных параметров:
- ✅ **Полный комплекс** (все Phase 1-5)
- ✅ **Блокировка отправки** до получения ответа/ошибки
- ✅ **Persistence в Room Database**
- ✅ **System prompt** можно менять в процессе
- ✅ **Только текст** (без attachments)
- ✅ **Стиль Jan/LM Studio** (технический, с деталями)
- ✅ **Светлая/темная тема** (Material 3)

---

## 📁 Структура проекта

```
shared/src/commonMain/kotlin/com/arny/aiprompts/
├── data/
│   ├── db/
│   │   ├── daos/
│   │   │   ├── ChatSessionDao.kt          [NEW]
│   │   │   ├── ChatMessageDao.kt          [NEW]
│   │   │   └── PromptDao.kt               [EXISTING]
│   │   ├── entities/
│   │   │   ├── ChatSessionEntity.kt       [NEW]
│   │   │   ├── ChatMessageEntity.kt       [NEW]
│   │   │   └── PromptEntity.kt            [EXISTING]
│   │   ├── migrations/
│   │   │   └── Migrations.kt              [NEW]
│   │   └── AppDatabase.kt                 [UPDATE]
│   ├── model/
│   │   ├── ChatSession.kt                 [UPDATE]
│   │   ├── ChatMessage.kt                 [EXISTING]
│   │   └── ChatSettings.kt                [NEW]
│   └── repositories/
│       ├── IChatSessionRepository.kt        [NEW]
│       ├── ChatSessionRepositoryImpl.kt     [NEW]
│       └── [остальные существующие]
├── domain/
│   └── interactors/
│       ├── LLMInteractor.kt                 [UPDATE]
│       └── ILLMInteractor.kt                [UPDATE]
└── presentation/
    ├── features/
    │   └── llm/
    │       ├── LlmComponent.kt              [UPDATE]
    │       ├── DefaultLlmComponent.kt       [UPDATE]
    │       └── LlmUiState.kt                [UPDATE]
    └── ui/
        └── llm/
            ├── LlmScreen.kt                 [UPDATE]
            ├── components/
            │   ├── ChatMessageCard.kt       [NEW]
            │   ├── CodeBlock.kt             [NEW]
            │   ├── ParametersPanel.kt       [NEW]
            │   ├── ChatSidebar.kt           [NEW]
            │   ├── ChatInputPanel.kt        [UPDATE]
            │   └── ModelSelector.kt         [NEW]
            └── theme/
                └── ChatTheme.kt             [NEW]
```

---

## 🚀 Phase 1: Исправление критических багов

### 1.1 Исправление DefaultLlmComponent.kt

**Проблемы:**
- ❌ Prompt не очищается после отправки
- ❌ Очистка prompt при активной генерации (вместо блокировки)
- ❌ Утечка Job (нет сброса streamingJob)
- ❌ Нет корректной обработки состояния генерации

**Решения:**
1. Добавить проверку isGenerating с показом ошибки
2. Очистить prompt сразу после отправки
3. Добавить onCompletion с сбросом Job
4. Улучшить обработку ошибок

---

## 🗄️ Phase 2: Расширение базы данных (Room)

### 2.1 Entity файлы

#### ChatSessionEntity.kt
```kotlin
@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val systemPrompt: String?,
    val temperature: Float,
    val maxTokens: Int,
    val topP: Float,
    val contextWindow: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val isArchived: Boolean,
    val modelId: String?
)
```

#### ChatMessageEntity.kt
```kotlin
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val status: String,
    val modelId: String?,
    val tokenCount: Int?,
    val editedAt: Long?,
    val orderIndex: Int
)
```

### 2.2 DAO интерфейсы

#### ChatSessionDao.kt
- getAllActiveSessions(): Flow<List<ChatSessionEntity>>
- getSessionById(sessionId: String): ChatSessionEntity?
- insertSession(session: ChatSessionEntity)
- updateSession(session: ChatSessionEntity)
- deleteSession(session: ChatSessionEntity)
- archiveSession(sessionId: String)

#### ChatMessageDao.kt
- getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>
- insertMessage(message: ChatMessageEntity)
- insertMessages(messages: List<ChatMessageEntity>)
- updateMessage(message: ChatMessageEntity)
- deleteMessagesForSession(sessionId: String)
- getMaxOrderIndex(sessionId: String): Int?

### 2.3 Миграция БД

Версия 1 → 2:
- Создание таблицы chat_sessions
- Создание таблицы chat_messages
- Индексы для производительности

---

## 🏗️ Phase 3: Репозитории и Domain слой

### 3.1 Интерфейс репозитория

#### IChatSessionRepository.kt
```kotlin
interface IChatSessionRepository {
    fun getAllSessions(): Flow<List<ChatSession>>
    fun getSessionById(sessionId: String): Flow<ChatSession?>
    suspend fun createSession(name: String, systemPrompt: String? = null): ChatSession
    suspend fun updateSession(session: ChatSession)
    suspend fun deleteSession(sessionId: String)
    suspend fun archiveSession(sessionId: String)
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>>
    suspend fun addMessage(sessionId: String, message: ChatMessage)
    suspend fun updateMessage(message: ChatMessage)
    suspend fun clearSessionMessages(sessionId: String)
}
```

### 3.2 Domain модели

#### ChatSession.kt
```kotlin
data class ChatSession(
    val id: String,
    val name: String,
    val systemPrompt: String?,
    val settings: ChatSettings,
    val createdAt: Long,
    val updatedAt: Long,
    val isArchived: Boolean,
    val modelId: String?,
    val messages: List<ChatMessage> = emptyList()
)

#### ChatSettings.kt
data class ChatSettings(
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val topP: Float = 0.9f,
    val contextWindow: Int = 10
)
```

---

## ⚙️ Phase 4: Interactor и Component

### 4.1 Обновление LLMInteractor

Новые методы:
- getSessionsFlow(): Flow<List<ChatSession>>
- createSession(name: String, systemPrompt: String?): ChatSession
- sendMessage(sessionId: String, content: String): Flow<DataResult<ChatMessage>>
- updateSystemPrompt(sessionId: String, systemPrompt: String)
- updateChatSettings(sessionId: String, settings: ChatSettings)

### 4.2 Переработка DefaultLlmComponent

Новая архитектура:
- Улучшенное управление Job
- Подписка на изменения текущей сессии
- Методы управления сессиями
- Обработка system prompt

---

## 🎨 Phase 5: Современный UI (Jan/LM Studio стиль)

### 5.1 Компоненты сообщений

#### ChatMessageCard.kt
- Markdown рендеринг
- Code blocks с подсветкой
- Кнопки: Copy, Edit, Delete, Regenerate
- Анимации появления

#### CodeBlock.kt
- Подсветка синтаксиса
- Кнопка "Copy code"
- Line numbers
- VS Code стиль

### 5.2 Панель параметров (Jan стиль)

#### ParametersPanel.kt
- Temperature slider (0.0 - 2.0)
- Max Tokens input
- Top P slider
- Context Window selector
- Model info card
- System Prompt textarea

### 5.3 Боковая панель чатов

#### ChatSidebar.kt
- Список сессий с preview
- Относительные даты
- Контекстное меню
- Поиск по истории
- Кнопка "New Chat"

### 5.4 Главный экран

#### LlmScreen.kt
```
┌─────────────────────────────────────────────────┐
│ Header: Model Selector + Settings               │
├──────────┬─────────────────────┬────────────────┤
│          │                     │                │
│  Chat    │   Chat Messages     │  Parameters   │
│ Sidebar  │   (reverse layout)  │  Panel        │
│          │                     │                │
├──────────┴─────────────────────┴────────────────┤
│ Input Panel (textarea + send button)            │
└─────────────────────────────────────────────────┘
```

---

## 🔗 Phase 6: Интеграция

### 6.1 Интеграция с промптами
- Drag-and-drop промпта в system prompt
- "Use as system prompt" кнопка
- Сохранение диалога как промпт

### 6.2 Настройки приложения
- Default chat settings
- API keys management
- Theme preferences
- Export/Import chats

---

## ✨ Phase 7: Дополнительные функции

### 7.1 Streaming улучшения
- Word-by-word анимация
- Typing indicator
- Progress bar

### 7.2 Branching conversations
- Создание веток
- Визуализация дерева
- Переключение между ветками

### 7.3 Экспорт/Импорт
- Export to Markdown
- Export to JSON
- Import chat history

---

## ⚡ Порядок реализации

### Sprint 1: Фундамент (3-4 дня)
1. Исправить баги в DefaultLlmComponent.kt
2. Создать Entity и DAO для чатов
3. Обновить AppDatabase и миграции
4. Создать репозиторий сессий

### Sprint 2: Domain слой (2-3 дня)
1. Обновить LLMInteractor
2. Обновить DefaultLlmComponent
3. Интегрировать persistence

### Sprint 3: UI Компоненты (4-5 дней)
1. ChatMessageCard с markdown
2. CodeBlock с подсветкой
3. ParametersPanel
4. ChatSidebar

### Sprint 4: Интеграция (3-4 дня)
1. Обновить LlmScreen
2. Адаптивная верстка
3. Анимации
4. Интеграция с промптами

### Sprint 5: Polish (2-3 дня)
1. Тестирование
2. Edge cases
3. Performance
4. Документация

---

## 🎯 Ключевые решения

### Управление состоянием генерации
- Блокировка кнопки до завершения/ошибки
- Индикатор "AI is typing..."
- Возможность отмены

### System Prompt
- Редактируемый в любой момент
- Сохраняется в сессии
- Визуальный индикатор

### Persistence
- Автосохранение каждого сообщения
- Offline-first архитектура
- Синхронизация при подключении

### UI Philosophy (Jan/LM Studio)
- Техническая эстетика
- Информативные панели
- Темная тема по умолчанию
- Моноширинный шрифт для кода

---

## 📝 Заметки

- Использовать compose-markdown для рендеринга
- Добавить поддержку Material You (динамические цвета)
- Реализовать поиск по истории через SQL LIKE
- Добавить индексы для часто используемых запросов
- Использовать Flow для реактивного обновления UI

---

## ✅ Чек-лист завершения

- [ ] Phase 1: Баги исправлены
- [ ] Phase 2: БД расширена
- [ ] Phase 3: Репозитории созданы
- [ ] Phase 4: Interactor обновлен
- [ ] Phase 5: UI компоненты созданы
- [ ] Phase 6: Интеграция завершена
- [ ] Phase 7: Доп. функции реализованы
- [ ] Тесты написаны
- [ ] Документация обновлена
