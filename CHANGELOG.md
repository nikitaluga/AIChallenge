# CHANGELOG

## [Unreleased]

## [Day 36] — Reflection Loop
- Добавлен экран «День 36» с итеративной рефлексией ответов LLM.
- `ReflectionAgent` (shared) — HTTP-клиент к POST `/reflection/chat`, поддержка rubric и maxIterations.
- `ReflectionRoutes` (server) — цикл рефлексии: draft → critique → revised → финальный ответ.
- UI: раскрывающиеся карточки итераций с score, critique и черновиком.

## [Day 35] — AI Git Commit Generator
- Добавлен экран «День 35» с генерацией commit-сообщений по git diff.
- `GitCommitAgent` (shared) — HTTP-клиент к POST `/git/commit`.
- `GitCommitRoutes` (server) — анализ diff через LLM, формирование Conventional Commits.
- UI: поле для вставки diff, кнопки копирования и вставки в буфер.

## [Day 34] — File Operations Assistant
- Добавлена новая секция в CLAUDE.md с задачами и действиями по обновлению документации.
- `FilesAgent` + `FilesAssistantRoutes`: 4 MCP-инструмента (read_file, search_in_files, write_file, generate_diff).
- Dry-run режим: write_file и generate_diff не применяют изменения без подтверждения.
- UI: split-панель (чат + предпросмотр файлов), 4 сценария использования.
