# AIChallenge Agents

KMP HTTP-клиенты к Ktor-серверу. Все агенты находятся в `commonMain` — работают одинаково на Android и iOS.

## Список агентов

| Агент | День | Endpoint | Назначение |
|-------|------|----------|-----------|
| `ChatAgent` | 1 | `/chat` | Базовый чат с LLM (OpenAI-совместимый) |
| `BranchingAgent` | 6 | `/branch/chat` | Разветвлённый диалог с ветками ответов |
| `FactsAgent` | 8 | `/facts/chat` | Извлечение фактов из текста |
| `CompressingAgent` | 9 | `/compress/chat` | Сжатие контекста (скользящее окно + суммаризация) |
| `SlidingWindowAgent` | 10 | `/sliding/chat` | Управление контекстом через скользящее окно |
| `MemoryAgent` | 11 | `/memory/chat` | Многоуровневая память (краткосрочная + долгосрочная) |
| `PersonalizedAgent` | 12 | `/personalized/chat` | Персонализированные ответы по профилю пользователя |
| `TaskStateMachineAgent` | 13 | `/task/chat` | Конечный автомат состояний задачи |
| `InvariantsAgent` | 14 | `/invariants/chat` | Проверка инвариантов в диалоге |
| `ProfiledTaskAgent` | 15 | `/profiled/chat` | Профилирование задач по типу и сложности |
| `McpWeatherAgent` | 17 | MCP `/mcp/tools/call` | MCP tool-calling: погода через get_weather |
| `SchedulerAgent` | 18 | `/scheduler/**` | Управление расписаниями через LLM (tool-calling) |
| `PipelineAgent` | 19 | `/pipeline/tools/call` | Цепочка MCP-инструментов: погода → суммаризация → файл |
| `OrchestratorAgent` | 20 | `/mcp/**` | Оркестратор нескольких MCP-серверов |
| `RagAgent` | 21–25 | `/rag/**` | RAG-пайплайн: индексация, поиск, чат v1/v2/v3, сравнение |
| `LocalLlmAgent` | 26 | `/local/chat`, `/local/cloud` | Локальная LLM (Ollama) vs облачная |
| `LocalLlmStreamAgent` | 27 | `/local/stream` | Стриминг ответов от локальной LLM (SSE) |
| `LocalRagAgent` | 28 | `/rag/local/**` | RAG на локальных эмбеддингах (nomic-embed-text) |
| `LocalOptimizationAgent` | 29 | `/local/benchmark`, `/local/judge` | Benchmark параметров + LLM-judge |
| `LocalServiceAgent` | 30 | `/local/health`, `/local/stream` | Локальная LLM как сервис (health + stream) |
| `DevAssistantAgent` | 31 | `/dev/chat`, `/dev/docs/**` | Dev-ассистент с RAG по CLAUDE.md + git MCP |
| `ReviewAgent` | 32 | `/review/pr` | AI code review по git diff |
| `SupportAgent` | 33 | `/support/**` | Саппорт-ассистент: пользователи, тикеты, MCP |
| `FilesAgent` | 34 | `/files/chat` | Файловые операции: read/search/write/diff |
| `GitCommitAgent` | 35 | `/git/commit` | Генерация commit-сообщений по diff |
| `ReflectionAgent` | 36 | `/reflection/chat` | Итеративная рефлексия: draft → critique → revised |

## Конфигурация

Все агенты используют:
- `AgentConfig.DEFAULT_SERVER_URL` (`http://10.0.2.2:8080`) — адрес сервера
- `CommonJson` — стандартная конфигурация kotlinx.serialization
- `HttpTimeout(30_000L)` — таймаут запроса 30 секунд
