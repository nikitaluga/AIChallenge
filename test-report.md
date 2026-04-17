# Test Report — Day 03 Testing

Generated: 2026-04-17 (updated run)

---

## Level 1 — Code Tests

`./gradlew :server:test :shared:jvmTest --rerun-tasks`

| Модуль | Класс | Тестов | Pass | Fail |
|--------|-------|--------|------|------|
| server | ApplicationTest | 1 | ✅ 1 | 0 |
| server | RagIndexGuardConsistencyTest | 2 | ✅ 2 | 0 |
| server | RagRepositoryTest | 5 | ✅ 5 | 0 |
| server | SupportRepositoryTest | 7 | ✅ 7 | 0 |
| server | SupportRepositoryValidationTest | 3 | ✅ 3 | 0 |
| shared | QueryModelUseCaseTest | 4 | ✅ 4 | 0 |
| shared | QueryModelUseCaseValidationTest | 3 | ✅ 3 | 0 |
| shared | SharedCommonTest | 1 | ✅ 1 | 0 |
| **ИТОГО** | | **26** | **✅ 26** | **0** |

### Исправления (2026-04-17)

- **`QueryModelUseCase.kt`** — добавлена проверка `prompt.isBlank()` → `Result.failure` и клампинг `maxTokens.coerceAtLeast(1)`
- **`RagRoutes.kt` v2/v3** — добавлен guard `index == null || index.chunks.isEmpty()` → graceful ответ (аналогично v1)
- **`RagIndexGuardConsistencyTest`** — исправлены инвертированные assertion'ы (тесты были намеренно сломаны для документации багов)

---

## Level 2 — Smoke UI Scenarios

> Прогон: 2026-04-17, эмулятор Pixel_9, сервер localhost:8080
> Инструмент: `adb` (uiautomator dump + input tap)

| # | Сценарий | Статус | Результат |
|---|---------|--------|-----------|
| 2 | День 33 — Support Assistant | ✅ PASS | Ответ содержит T-001 и T-002 |
| 3 | День 35 — Git Commit Generator | ✅ PASS | `feat(validation): add email validation function` |
| 4 | День 34 — File Operations | ✅ PASS | 21 файл .kt с usages RouterAiApiService |

---

## CI Integration

Pre-commit hook установлен в `.githooks/pre-commit`.

**Активация:**
```bash
git config core.hooksPath .githooks
```

**Что происходит при `git commit`:**
1. Запускаются `:server:test` + `:shared:allTests`
2. При неудаче — коммит отменяется с сообщением об ошибке
3. При успехе — коммит проходит

---

## Smoke-сценарии для Level 2

### Prompt для агента
```
Ты тестировщик мобильного приложения AIChallenge (Android).
Выполни smoke-сценарий:

[СЦЕНАРИЙ]

Правила:
- Делай скриншот перед каждым действием
- Фиксируй PASS/FAIL для каждого шага
- При FAIL: опиши что именно пошло не так
- Итог: markdown-таблица шагов + вывод PASSED / FAILED
```

#### Сценарий 2 — День 33, Support Assistant
```
1. Найди и открой вкладку "День 33"
2. Нажми quick-prompt "Какие у меня тикеты?"
3. Дождись ответа (до 20 сек)
4. PASS если ответ содержит "T-001" и "T-002"
```

#### Сценарий 3 — День 35, Git Commit Generator
```
1. Найди и открой вкладку "День 35"
2. Нажми quick-prompt "+ fun validateEmail(email: Str..."
3. Дождись ответа (до 15 сек)
4. PASS если в ответе есть "feat(" или "fix(" или "chore("
```

#### Сценарий 4 — День 34, File Operations
```
1. Найди и открой вкладку "День 34"
2. Нажми quick-prompt "Найди все usages RouterAiApiService"
3. Дождись ответа (до 40 сек)
4. PASS если в ответе есть файлы .kt
```
