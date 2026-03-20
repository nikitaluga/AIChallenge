# День 23 — Реранкинг и фильтрация RAG

## Цель

Улучшить RAG-пайплайн двумя механизмами:
1. **Threshold-фильтр** — убрать нерелевантные чанки после поиска по cosine similarity
2. **Query Rewrite** — переформулировать запрос через LLM перед эмбеддингом

Добавить **трёхколоночное сравнение** в Compare-таб: `Без RAG` vs `RAG базовый` vs `RAG+Filter+Rewrite`.

---

## Выбранные решения (по итогам интервью)

| Параметр | Решение |
|----------|---------|
| Метод фильтрации | Threshold по cosine similarity |
| Порог по умолчанию | `0.35` |
| topK-before (кандидаты) | `20` |
| topK-after (финал) | `5` |
| Query Rewrite | Да, LLM-based (перед эмбеддингом) |
| UI сравнения | Расширить Compare-таб (3 колонки) |
| Воронка в UI | Да: "20 кандидатов → 5 прошли порог 0.35" |

---

## Архитектура изменений

### 1. Server — `RagIndexer.kt`

#### Новый метод `rewriteQuery(query: String): String`
```kotlin
suspend fun rewriteQuery(query: String): String {
    // LLM prompt: "Переформулируй запрос для семантического поиска по технической документации.
    //              Верни только переформулированный запрос без объяснений."
    // Использует тот же apiService.chat()
}
```

#### Новый метод `filterChunks(candidates: List<ScoredChunk>, threshold: Float, topKAfter: Int): FilterResult`
```kotlin
data class FilterResult(
    val filtered: List<ScoredChunk>,
    val candidatesCount: Int,
    val passedCount: Int,
    val threshold: Float
)
```
Логика: из `candidates` оставить только `score >= threshold`, взять топ `topKAfter`.

#### Расширение `buildContextAndAnswer` → добавить параметры:
- `threshold: Float = 0.0f` (0.0 = отключён)
- `topKBefore: Int = k` (берём больше кандидатов)
- `rewriteQuery: Boolean = false`

#### Расширение `compare` → вернуть 3 ответа:
```kotlin
data class TripleCompareResult(
    val noRagAnswer: String,
    val ragBaselineAnswer: String,        // topK=5, без фильтра, без rewrite
    val ragEnhancedAnswer: String,        // topKBefore=20, threshold, rewrite
    val baselineChunks: List<ScoredChunk>,
    val enhancedChunks: List<ScoredChunk>,
    val filterStats: FilterStats,          // candidatesBefore, candidatesAfter, threshold, rewrittenQuery
)
```

### 2. Server — `RagServerModels.kt`

Добавить:
```kotlin
data class FilterStats(
    val candidatesBefore: Int,
    val candidatesAfter: Int,
    val threshold: Float,
    val rewrittenQuery: String?
)

data class TripleCompareRequest(
    val query: String,
    val k: Int = 5,
    val strategy: String = "structural",
    val threshold: Float = 0.35f,
    val topKBefore: Int = 20,
    val rewriteQuery: Boolean = true
)

data class TripleCompareResponse(
    val noRagAnswer: String,
    val ragBaselineAnswer: String,
    val ragEnhancedAnswer: String,
    val baselineChunks: List<RagChunkResult>,
    val enhancedChunks: List<RagChunkResult>,
    val filterStats: FilterStats
)
```

### 3. Server — `RagRoutes.kt`

Новый endpoint:
```
POST /rag/compare/enhanced
Body: TripleCompareRequest
Response: TripleCompareResponse
```

### 4. Shared — `RagModels.kt`

Добавить domain модели:
```kotlin
data class FilterStats(
    val candidatesBefore: Int,
    val candidatesAfter: Int,
    val threshold: Float,
    val rewrittenQuery: String?
)

data class RagTripleCompareResult(
    val noRagAnswer: String,
    val ragBaselineAnswer: String,
    val ragEnhancedAnswer: String,
    val baselineChunks: List<RagChunkResult>,
    val enhancedChunks: List<RagChunkResult>,
    val filterStats: FilterStats
)
```

### 5. Shared — `RagAgent.kt`

Новый метод:
```kotlin
suspend fun compareEnhanced(
    query: String,
    k: Int = 5,
    strategy: String = "structural",
    threshold: Float = 0.35f,
    topKBefore: Int = 20,
    rewriteQuery: Boolean = true
): RagTripleCompareResult
```

### 6. Presentation — `RagContract.kt`

Добавить в `State`:
```kotlin
val threshold: Float = 0.35f,
val topKBefore: Int = 20,
val rewriteEnabled: Boolean = true,
val tripleCompareResult: RagTripleCompareResult? = null,
val isEnhancedComparing: Boolean = false,
```

Добавить Event:
```kotlin
data class ThresholdChanged(val value: Float) : Event
data class TopKBeforeChanged(val value: Int) : Event
data class RewriteToggled(val enabled: Boolean) : Event
data object RunEnhancedCompare : Event
```

### 7. Presentation — `RagViewModel.kt`

Добавить обработку новых событий, метод `runEnhancedCompare()`.

### 8. Presentation — `RagScreen.kt`

**Compare-таб расширить:**

1. **Настройки фильтра** (под существующими настройками):
   - Slider threshold: 0.0 → 1.0, шаг 0.05
   - Поле topK-before
   - Toggle "Query Rewrite"

2. **Кнопка "Сравнить (расширенно)"** — запускает `RunEnhancedCompare`

3. **Результат** — горизонтальный scroll с 3 колонками:
   - Колонка 1: "Без RAG"
   - Колонка 2: "RAG базовый"
   - Колонка 3: "RAG+Filter+Rewrite"

4. **Воронка фильтрации** (под результатом):
   ```
   📊 Retrieval: 20 кандидатов → 5 прошли порог 0.35
   Rewrite: "исходный запрос" → "переформулированный"
   ```

5. **Чанки с маркировкой** — показывать чанки колонки 3 с badge score.

---

## Затронутые файлы

### Server
- `server/src/main/kotlin/ru/nikitaluga/aichallenge/rag/RagIndexer.kt` — новые методы
- `server/src/main/kotlin/ru/nikitaluga/aichallenge/rag/RagServerModels.kt` — новые data classes
- `server/src/main/kotlin/ru/nikitaluga/aichallenge/rag/RagRoutes.kt` — новый endpoint

### Shared
- `shared/src/commonMain/kotlin/ru/nikitaluga/aichallenge/domain/model/RagModels.kt`
- `shared/src/commonMain/kotlin/ru/nikitaluga/aichallenge/data/agent/RagAgent.kt`

### ComposeApp
- `composeApp/src/commonMain/kotlin/ru/nikitaluga/aichallenge/rag/RagContract.kt`
- `composeApp/src/commonMain/kotlin/ru/nikitaluga/aichallenge/rag/RagViewModel.kt`
- `composeApp/src/commonMain/kotlin/ru/nikitaluga/aichallenge/rag/RagScreen.kt`

---

## Параметры по умолчанию

| Параметр | Значение |
|----------|----------|
| threshold | 0.35 |
| topKBefore | 20 |
| topKAfter (= k) | 5 |
| rewriteQuery | true |
| strategy | structural |

---

## UX-детали

- Query Rewrite LLM prompt: системный промпт "Ты помощник для семантического поиска. Переформулируй запрос для точного поиска по технической документации. Верни только переформулированный запрос."
- Если после фильтра 0 чанков → использовать оригинальный топ-K (fallback)
- Slider threshold с шагом 0.05, отображение значения рядом
- Воронка показывает числа до/после даже если rewrite выключен
