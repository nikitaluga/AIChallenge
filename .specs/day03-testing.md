# Day 03 — Testing: Unit/Integration + Smoke UI

## Цель

Двухуровневая система тестирования:
- **Уровень 1** — unit/integration тесты на бизнес-логику (≥3 модуля)
- **Уровень 2** — smoke UI через claude-in-mobile MCP на Android-девайсе

---

## Статус реализации

| Артефакт | Статус |
|---------|--------|
| `RagRepositoryTest.kt` — 5 тестов | ✅ Проходит |
| `SupportRepositoryTest.kt` — 7 тестов | ✅ Проходит |
| `QueryModelUseCaseTest.kt` — 4 теста | ✅ Проходит |
| `ApplicationTest.kt` — исправлен | ✅ Проходит |
| `SupportRepositoryValidationTest.kt` — 3 теста | ✅ Проходит (фикс применён) |
| `.githooks/pre-commit` | ✅ Создан |
| claude-in-mobile MCP | ✅ Установлен глобально |
| Smoke сценарий 2 (Support) | ✅ PASS |
| Smoke сценарий 3 (Git Commit) | ✅ PASS |
| Smoke сценарий 4 (File Ops) | ✅ PASS |
| Smoke сценарий 1 (RAG Chat) | ✅ Фикс применён (graceful ответ без индекса) |

---

## Уровень 1 — Код-тесты

### Зависимости (добавлены)

`gradle/libs.versions.toml`:
```toml
ktor-client-mock = { module = "io.ktor:ktor-client-mock-jvm", version.ref = "ktor" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
```

`server/build.gradle.kts`:
```kotlin
testImplementation(libs.ktor.client.mock)
testImplementation(libs.kotlinx.coroutines.test)
```

`shared/build.gradle.kts` (commonTest):
```kotlin
implementation(libs.kotlinx.coroutines.test)
```

### Тест-файлы

#### `RagRepositoryTest.kt` — `server/src/test/`
Тестирует JSON-persistence + Mutex кеш.

- `load returns null when file does not exist`
- `save and load roundtrip preserves data` — полный сериализация/десериализация
- `getSync returns cached value after save`
- `clear removes file and invalidates cache`
- `save overwrites existing file`

Паттерн: `@Before` создаёт `File.createTempFile()` и сразу `delete()` (файла нет, но путь известен). `@After` удаляет temp-файл.

#### `SupportRepositoryTest.kt` — `server/src/test/`
Тестирует seed data, фильтрацию, обновление статуса с персистентностью.

- `seed data initialises 3 users and 4 tickets`
- `getTicketsForUser filters by userId`
- `getTicket returns correct ticket by id`
- `getTicket returns null for unknown id`
- `updateTicketStatus changes status and persists` — создаёт вторую инстанцию репозитория из тех же файлов
- `updateTicketStatus returns false for unknown ticket`
- `getUser returns null for unknown userId`

Паттерн: два temp-файла (users + tickets), оба удаляются в `@After`.

#### `QueryModelUseCaseTest.kt` — `shared/src/commonTest/`
Тестирует чистую бизнес-логику без сети.

- `successful query returns ModelQueryResult with correct fields`
- `estimatedCostUsd is computed correctly` — 1M input * 0.15 + 1M output * 0.60 = 0.75
- `repository throws exception returns failure`
- `modelId from config is passed to repository`

Паттерн: `object : ModelQueryRepository { override suspend fun query(...) = ... }` — анонимный мок без библиотек.

### Запуск тестов

```bash
./gradlew :server:test :shared:jvmTest --rerun-tasks
```

Результаты парсятся из:
- `server/build/reports/tests/test/TEST-*.xml`
- `shared/build/reports/tests/jvmTest/TEST-*.xml`

---

## Уровень 2 — Smoke UI

### Установка claude-in-mobile

```bash
# Добавить MCP глобально (один раз)
claude mcp add --scope user --transport stdio mobile -- npx -y claude-in-mobile@latest
```

После перезапуска Claude Code доступны инструменты: `mobile_screenshot`, `mobile_tap`, `mobile_type`, `mobile_swipe`, `mobile_get_ui_elements`.

### Подготовка перед прогоном

```bash
# Запустить эмулятор (если нет физического устройства)
~/Library/Android/sdk/emulator/emulator -avd Pixel_9 -no-snapshot-load &

# Дождаться устройства
~/Library/Android/sdk/platform-tools/adb devices

# Собрать и установить приложение
./gradlew :composeApp:installDebug

# Запустить приложение
~/Library/Android/sdk/platform-tools/adb shell am start -n ru.nikitaluga.aichallenge/.MainActivity

# Запустить сервер
./gradlew :server:run &

# Проверить сервер
curl -s http://localhost:8080/
```

### Сценарии

**Сценарий 1 — RAG Chat (День 21)** ❌ FAIL (намеренный)
```
Предусловие: rag_index.json ОТСУТСТВУЕТ (не запускать индексацию)
1. Найти и открыть вкладку "День 21"
2. Нажать quick-prompt или ввести: "Что такое MVI?"
3. Дождаться ответа (до 30 сек)
PASS: ответ содержит осмысленное сообщение об ошибке ("индекс не найден" или аналог)
FAIL (текущее поведение): сервер возвращает 500 или пустой ответ без объяснения

Цель: показать что отсутствие RAG-индекса не обработано gracefully.
Фикс: добавить проверку в RagRoutes — если индекс пуст, вернуть 200 с текстом
"RAG-индекс не построен. Запустите индексацию через вкладку статистики."
```

**Сценарий 2 — Support Assistant (День 33)** ✅ PASS (2026-04-17)
```
1. Открыть вкладку "День 33"
2. Убедиться что видны тикеты T-001, T-002 в панели
3. Нажать quick-prompt "Какие у меня тикеты?"
4. Дождаться ответа (до 20 сек)
PASS: ответ содержит "T-001" и "T-002"
```

**Сценарий 3 — Git Commit Generator (День 35)** ✅ PASS (2026-04-17)
```
1. Открыть вкладку "День 35"
2. Нажать quick-prompt "+ fun validateEmail(email: Str..."
   (авто-отправляется после заполнения поля)
3. Дождаться ответа (до 15 сек)
PASS: ответ содержит строки начинающиеся с feat( / fix( / chore(
```

**Сценарий 4 — File Operations (День 34)** ✅ PASS (2026-04-17)
```
1. Открыть вкладку "День 34"
2. Нажать quick-prompt "Найди все usages RouterAiApiService"
3. Дождаться ответа (до 40 сек — агент читает файлы)
PASS: ответ содержит имена файлов с расширением .kt
```

### Промпт для запуска через Claude Code

```
Запусти полный цикл тестирования AIChallenge:

## Уровень 1 — Код-тесты
1. Выполни `./gradlew :server:test :shared:jvmTest --rerun-tasks`
2. Распарси результаты из TEST-*.xml файлов в build/reports/
3. Сформируй таблицу: модуль → тестов → pass → fail

## Уровень 2 — Smoke UI (claude-in-mobile MCP)
Используй инструменты mobile_* для управления Android-устройством.

Перед началом:
- mobile_screenshot — убедись что устройство подключено и приложение запущено
- Если не запущено: adb shell am start -n ru.nikitaluga.aichallenge/.MainActivity
- Убедись что сервер работает: curl -s http://localhost:8080/

Сценарий 2 — День 33 (Support Assistant)
1. Найди вкладку "День 33" и открой её (uiautomator dump → tap по координатам)
2. Скриншот — убедись что видны T-001 и T-002
3. Нажми quick-prompt "Какие у меня тикеты?"
4. Жди ответа до 20 сек (Monitor пока "Анализирую" пропадёт из UI)
5. PASS если ответ содержит "T-001" и "T-002"

Сценарий 3 — День 35 (Git Commit Generator)
1. Открой вкладку "День 35"
2. Нажми quick-prompt "+ fun validateEmail(email: Str..."
3. Жди ответа до 15 сек
4. PASS если есть "feat(" или "fix(" или "chore("

Сценарий 4 — День 34 (File Operations)
1. Открой вкладку "День 34"
2. Нажми quick-prompt "Найди все usages RouterAiApiService"
3. Жди ответа до 40 сек
4. PASS если в ответе есть файлы .kt

## Отчёт
Обнови test-report.md:
- Таблица Level 1: класс → тестов → pass/fail
- Таблица Level 2: сценарий → PASS/FAIL → скриншот
- Если что-то упало — укажи в каком месте и возможную причину
```

### Технические детали навигации (adb)

Координаты в UI dump — в пикселях реального экрана (1080×2424 для Pixel 9).
Скриншоты отображаются уменьшенными: умножь видимые координаты на ~1.21 чтобы получить реальные.

```bash
# Получить координаты элементов
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml /tmp/ui.xml
python3 -c "
import xml.etree.ElementTree as ET
tree = ET.parse('/tmp/ui.xml')
for el in tree.iter():
    text = el.get('text','')
    bounds = el.get('bounds','')
    if text and len(text) > 2:
        x1,y1,x2,y2 = [int(v) for v in bounds.replace('][',',').replace('[','').replace(']','').split(',')]
        print(f'{repr(text[:50])} center=({(x1+x2)//2},{(y1+y2)//2})')
"

# Ждать исчезновения loading-индикатора (Monitor)
until adb shell uiautomator dump /sdcard/check.xml 2>/dev/null && \
  adb pull /sdcard/check.xml /tmp/check.xml 2>/dev/null && \
  ! grep -q 'Анализирую\|загрузк' /tmp/check.xml; do sleep 3; done
```

---

## Интеграция в flow разработки

### Pre-commit hook

`.githooks/pre-commit` — запускает unit-тесты перед каждым коммитом:
```bash
#!/bin/sh
echo "=== Pre-commit: running unit tests ==="
./gradlew :server:test :shared:allTests --quiet --continue 2>&1
if [ $? -ne 0 ]; then
  echo "Tests FAILED. Commit aborted."
  exit 1
fi
echo "All tests passed."
```

Активация (один раз):
```bash
git config core.hooksPath .githooks
```

### После деплоя новой фичи

```
Я задеплоил фичу: [ОПИСАНИЕ].
Прочитай smoke-сценарии в .specs/day03-testing.md.
Добавь новый сценарий для этой фичи.
Прогони все сценарии через mobile_* инструменты.
Обнови test-report.md: статусы + скриншоты.
```

### Разрешения (`.claude/settings.json`)

Уже настроены, подтверждения не нужны для:
```json
"Bash(./gradlew :server:test *)",
"Bash(./gradlew :shared:jvmTest *)",
"Bash(~/Library/Android/sdk/platform-tools/adb shell uiautomator dump *)",
"Bash(~/Library/Android/sdk/platform-tools/adb pull *)",
"Bash(~/Library/Android/sdk/platform-tools/adb devices)",
"Bash(~/Library/Android/sdk/platform-tools/adb exec-out screencap *)",
"Bash(curl -s http://localhost:*)"
```

---

## Известные баги (выявлены тестами)

### BUG-1: SupportRepository не валидирует статус тикета
**Файл:** `server/src/main/kotlin/.../support/SupportRepository.kt:47`
**Тест:** `SupportRepositoryValidationTest` — 3 теста, все FAIL
**Симптом:** `updateTicketStatus("T-001", "HACKED")` возвращает `true` и сохраняет невалидный статус
**Ожидание:** допустимые значения только `"open"`, `"in_progress"`, `"resolved"`
**Фикс:**
```kotlin
private val validStatuses = setOf("open", "in_progress", "resolved")

suspend fun updateTicketStatus(ticketId: String, status: String): Boolean {
    if (status !in validStatuses) return false
    return mutex.withLock { ... }
}
```

### BUG-2: RAG Chat не обрабатывает отсутствие индекса
**Файл:** `server/src/main/kotlin/.../rag/RagRoutes.kt` — POST /rag/chat
**Тест:** Smoke Сценарий 1 — FAIL
**Симптом:** при пустом `rag_index.json` сервер возвращает 500 или пустой ответ
**Фикс:** добавить в хэндлер `/rag/chat`:
```kotlin
val index = repository.load()
if (index == null || index.chunks.isEmpty()) {
    call.respond(RagChatResponse(
        answer = "RAG-индекс не построен. Запустите индексацию через вкладку статистики.",
        usedChunks = emptyList()
    ))
    return@post
}
```

---

## Затронутые файлы

```
server/
  build.gradle.kts                          + ktor-client-mock, kotlinx-coroutines-test
  src/test/.../RagRepositoryTest.kt         новый (5 тестов)
  src/test/.../SupportRepositoryTest.kt     новый (7 тестов)
  src/test/.../ApplicationTest.kt           исправлен (убран вызов module())

shared/
  build.gradle.kts                          + kotlinx-coroutines-test в commonTest
  src/commonTest/.../QueryModelUseCaseTest.kt  новый (4 теста)

gradle/libs.versions.toml                   + ktor-client-mock, kotlinx-coroutines-test

.githooks/pre-commit                        новый
.claude/settings.json                       новый (permissions.allow)
scripts/run-tests.sh                        новый (вспомогательный скрипт)
test-report.md                              генерируется после прогона
```
