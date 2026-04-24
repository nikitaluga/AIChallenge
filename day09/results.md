# Day 09 — Multi-Stage Inference Results

**Fast model:** `openai/gpt-4o-mini`  **Strong model:** `openai/gpt-4o`

## Summary

| Metric | Monolithic (A) | Multi-Stage (B) |
|--------|----------------|-----------------|
| Decision accuracy (eval cases) | **9/10 = 90%** | **9/10 = 90%** |
| Total LLM calls | 15 (1× per case) | 45 (3–4× per case) |
| Stage 2 escalations (→ gpt-4o) | — | 5 |
| Estimated cost | $0.00135 | $0.01560 |
| Avg latency | 2724 ms | 10018 ms |

## Decision Accuracy (eval cases)

| # | Type | Expected | Mono | Multi | Mono✓ | Multi✓ | Mono score | Multi score |
|---|------|----------|------|-------|-------|--------|------------|-------------|
| 1 | strong_match | HIRE | HIRE | HIRE | ✓ | ✓ | 90 | 85 |
| 2 | clear_reject | REJECT | REJECT | REJECT | ✓ | ✓ | 10 | 0 |
| 3 | moderate | MAYBE | REJECT | REJECT | ✗ | ✗ | 30 | 25 |
| 4 | strong_match_2 | HIRE | HIRE | HIRE | ✓ | ✓ | 95 | 90 |
| 5 | clear_reject_2 | REJECT | REJECT | REJECT | ✓ | ✓ | 10 | 0 |
| 6 | good_fit | HIRE | HIRE | HIRE | ✓ | ✓ | 90 | 85 |
| 7 | borderline_reject | REJECT | REJECT | REJECT | ✓ | ✓ | 10 | 20 |
| 8 | moderate_2 | MAYBE | MAYBE | MAYBE | ✓ | ✓ | 70 | 60 |
| 9 | strong_match_3 | HIRE | HIRE | HIRE | ✓ | ✓ | 95 | 90 |
| 10 | reject_no_exp | REJECT | REJECT | REJECT | ✓ | ✓ | 10 | 20 |

## Edge Cases (no ground truth)

| # | Type | Mono decision | Multi decision | Stage2 model | Multi gaps |
|---|------|---------------|----------------|--------------|-----------|
| 11 | overqualified | MAYBE | MAYBE | strong |  |
| 12 | career_change | REJECT | MAYBE | strong | postgres, docker, career_change |
| 13 | noisy | MAYBE | HIRE | fast |  |
| 14 | fresh_grad | REJECT | REJECT | strong | Python 5yr+, Senior level experience, career gaps |
| 15 | ambiguous | REJECT | REJECT | fast | Postgres, experience level (5yr+) |

## Multi-Stage Pipeline Detail (edge cases)

### Case 11 (overqualified)

**Stage 1 extracted:** skills=['python', 'fastapi', 'postgres', 'docker', 'kafka'], exp_years=15, career_notes=overqualified

**Stage 2 scoring (strong):** score=70, match_level=MODERATE, gaps=[]

**Stage 3 final:** decision=MAYBE, score=70
summary: _The candidate is overqualified and has a moderate score, leading to a maybe decision._

### Case 12 (career_change)

**Stage 1 extracted:** skills=['python', 'scikit-learn', 'pandas', 'fastapi'], exp_years=7, career_notes=career_change

**Stage 2 scoring (strong):** score=60, match_level=MODERATE, gaps=['postgres', 'docker']

**Stage 3 final:** decision=MAYBE, score=60
summary: _The candidate has relevant skills and experience but is also undergoing a career change, leading to a moderate score and gaps in required skills._

### Case 13 (noisy)

**Stage 1 extracted:** skills=['python', 'fastapi', 'postgres', 'docker', 'kafka'], exp_years=5, career_notes=normal

**Stage 2 scoring (fast):** score=90, match_level=STRONG, gaps=[]

**Stage 3 final:** decision=HIRE, score=90
summary: _The candidate has a strong match with a high score and meets all required skills._

### Case 14 (fresh_grad)

**Stage 1 extracted:** skills=['python', 'fastapi', 'postgresql', 'docker'], exp_years=0, career_notes=fresh_grad

**Stage 2 scoring (strong):** score=20, match_level=WEAK, gaps=['Python 5yr+', 'Senior level experience']

**Stage 3 final:** decision=REJECT, score=20
summary: _The candidate is a fresh graduate with insufficient experience and a weak match level._

### Case 15 (ambiguous)

**Stage 1 extracted:** skills=['python', 'fastapi', 'mysql', 'docker'], exp_years=3, career_notes=normal

**Stage 2 scoring (fast):** score=40, match_level=WEAK, gaps=['Postgres', 'experience level (5yr+)']

**Stage 3 final:** decision=REJECT, score=40
summary: _The candidate is rejected due to a weak match level and a score below the threshold._

## Pipeline Architecture

```
Monolithic (A):
  raw CV → [gpt-4o-mini: one big prompt] → {decision, score, gaps, summary}

Multi-Stage (B):
  raw CV → [Stage 1: gpt-4o-mini] → {skills, exp_years, career_notes, ...}
         → [Stage 2: mini|gpt-4o*] → {score, match_level, gaps}
         → [Stage 3: gpt-4o-mini]  → {decision, score, gaps, summary}

  * Stage 2 escalates to gpt-4o when career_notes ≠ normal
```

Each stage has a short, focused prompt with strict JSON output.
Stage 1 sees raw text. Stage 2 sees only structured fields, not raw CV.
Stage 3 combines Stage 1 + Stage 2 outputs for final formatting.

---

## Выводы

### Точность
Оба варианта: **9/10** на структурированных eval-кейсах. Единственный провал: кейс 3 (`moderate`,
ожидалось MAYBE) — разработчик 3yr Python без FastAPI/Docker — оба дали REJECT.
Граница между REJECT и MAYBE требует явных правил скоринга; в обоих промптах их нет.

### Анализ edge cases

| Кейс | Mono | Multi | Победитель | Почему |
|------|------|-------|------------|--------|
| `career_change` | REJECT | **MAYBE** | **Multi** | Stage 1 определил `career_change`, Stage 2 эскалировал на gpt-4o, нюанс сохранён |
| `noisy` | **MAYBE** | HIRE | **Mono** | Stage 1 слишком хорошо очистил эмодзи/опечатки — Stage 2 потерял сигнал о качестве источника |
| `overqualified` | MAYBE | MAYBE | Ничья | Оба правильно |
| `fresh_grad` | REJECT | REJECT | Ничья | Оба корректно оштрафовали 0yr exp |
| `ambiguous` | REJECT | REJECT | Ничья | Оба слишком строги к 3yr/no-Postgres |

Multi-stage выигрывает там, где важна **структурированная промежуточная репрезентация** (аномалии карьерного пути).
Mono выигрывает там, где **сигналы сырого текста нельзя нормализовывать** (зашумлённый ввод).

### Стоимость и латентность

| Метрика | Monolithic | Multi-Stage | Разница |
|---------|-----------|-------------|---------|
| Стоимость (15 кейсов) | $0.00135 | $0.01560 | в 11.5× дороже |
| Средняя латентность | 2 724 мс | 10 018 мс | в 3.7× медленнее |
| Вызовов LLM | 15 (1×) | 45 (3×) | — |
| Эскалации Stage 2 | — | 5 / 15 | 33% |

### Когда использовать multi-stage инференс

Использовать multi-stage, когда:
- Вход неоднородный или многоязычный → Stage 1 нормализует до рассуждений
- Решение охватывает несколько независимых осей → каждый этап отвечает за одно
- Часть кейсов требует сильной модели → эскалация дешевле, чем gpt-4o на все кейсы
- Промежуточные результаты нужно логировать или аудировать

Оставаться на monolithic, когда:
- Входные данные однородны и структурированы
- Бюджет по стоимости/латентности ограничен
- Задача — простая классификация с малым числом полей