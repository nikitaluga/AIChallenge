---
name: Day 19 — MCP Tool Composition Pipeline
description: Pipeline of 3 MCP tools: get_weather → summarize_weather → save_to_file
type: project
---

Day 19 автоматический пайплайн из 3 MCP-инструментов реализован.

**Why:** День 19 курса AI — композиция MCP-инструментов, LLM сама цепочкой вызывает инструменты.

**How to apply:** При работе с Pipeline-экраном или агентом — понимать что это многошаговый tool-calling loop.

## Ключевые файлы
- `server/.../pipeline/PipelineRoutes.kt` — 3 инструмента + `/pipeline/files` эндпоинты
- `shared/.../data/agent/PipelineAgent.kt` — цикл tool-calling, макс. 5 итераций
- `shared/.../domain/model/PipelineModels.kt` — PipelineToolStep, PipelineChatMessage, SavedFileInfo
- `composeApp/.../pipeline/` — Contract, ViewModel, Screen

## Пайплайн
1. `get_weather(city)` — OWM API через WeatherService
2. `summarize_weather(weather_data)` — форматирует в одну строку `"Город: X°C, desc, влажность Y%, ветер Z м/с"`
3. `save_to_file(filename, content)` — пишет в `reports/` рядом с `schedules.json`

## UI
- Чат с chat bubbles (паттерн как SchedulerScreen)
- Каждый tool-call — отдельный badge: 🔍 get_weather / 📝 summarize_weather / 💾 save_to_file
- Нижняя панель сохранённых файлов (аналог SchedulesPanel)
- Таб "День 19" добавлен в App.kt
