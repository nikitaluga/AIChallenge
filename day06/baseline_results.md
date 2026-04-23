# Day 06 — Baseline Results

**Model:** `openai/gpt-4o-mini` (no fine-tuning)  
**Examples:** 10 from eval.jsonl  

## Aggregate Scores

| Metric | Score |
|--------|-------|
| Valid JSON | 100% |
| Category accuracy | 90% |
| Priority accuracy | 50% |
| Sentiment accuracy | 70% |

## Per-Example Results

| # | Input (truncated) | Expected | Predicted | Cat | Pri | Sen |
|---|-------------------|----------|-----------|-----|-----|-----|
| 1 | Subject: Не могу войти в аккаунт  Description: Наж... | cat=auth pri=high sen=frustrated | cat=auth pri=high sen=frustrated | ✓ | ✓ | ✓ |
| 2 | Subject: Аккаунт заблокирован без объяснений  Desc... | cat=auth pri=critical sen=angry | cat=account pri=high sen=frustrated | ✗ | ✗ | ✗ |
| 3 | Subject: Двойное списание за подписку  Description... | cat=billing pri=critical sen=angry | cat=billing pri=high sen=frustrated | ✓ | ✗ | ✗ |
| 4 | Subject: Промокод не применяется при оплате  Descr... | cat=billing pri=medium sen=frustrated | cat=billing pri=medium sen=frustrated | ✓ | ✓ | ✓ |
| 5 | Subject: Приложение падает на Samsung Galaxy S23  ... | cat=crash pri=critical sen=angry | cat=crash pri=high sen=frustrated | ✓ | ✗ | ✗ |
| 6 | Subject: Приложение зависает на сплэш-скрине  Desc... | cat=crash pri=medium sen=frustrated | cat=crash pri=medium sen=frustrated | ✓ | ✓ | ✓ |
| 7 | Subject: Нужен API для интеграции с нашей CRM  Des... | cat=feature_request pri=high sen=neutral | cat=feature_request pri=medium sen=neutral | ✓ | ✗ | ✓ |
| 8 | Subject: Нужна выгрузка всех данных для GDPR  Desc... | cat=data_export pri=high sen=neutral | cat=data_export pri=medium sen=neutral | ✓ | ✗ | ✓ |
| 9 | Subject: Чат загружается очень долго  Description:... | cat=performance pri=high sen=frustrated | cat=performance pri=high sen=frustrated | ✓ | ✓ | ✓ |
| 10 | Subject: Как удалить аккаунт и все данные?  Descri... | cat=account pri=medium sen=neutral | cat=account pri=medium sen=neutral | ✓ | ✓ | ✓ |

## Criteria for Improvement

After fine-tuning, the model should achieve:

| Metric | Baseline | Target |
|--------|----------|--------|
| Valid JSON | 100% | 100% |
| Category accuracy | 90% | ≥95% |
| Priority accuracy | 50% | ≥85% |
| Sentiment accuracy | 70% | ≥90% |
| Response length | baseline | shorter (no explanations) |