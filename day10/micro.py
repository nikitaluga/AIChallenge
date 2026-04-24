#!/usr/bin/env python3
"""
Day 10 — Micro-model First.

MicroClassifier: TF-IDF + ComplementNaiveBayes trained on day06/train.jsonl.
Zero API calls. Predicts ticket category + confidence.

Why ComplementNB over LogisticRegression for small datasets:
- NB gives calibrated, discriminative probabilities even on ~50 training examples
- LR spreads probability uniformly at ~1/n_classes when training data is sparse

Usage:
    from micro import MicroClassifier, MicroResult
    clf = MicroClassifier()
    clf.fit_from_jsonl("../day06/train.jsonl", "../day06/eval.jsonl")
    result = clf.predict("Subject: ...\n\nDescription: ...")
    # result.status: "OK" | "UNSURE"
"""

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

try:
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.naive_bayes import ComplementNB
    from sklearn.pipeline import Pipeline
except ImportError:
    raise SystemExit("Run: pip install scikit-learn")

VALID_CATEGORIES = {"auth", "billing", "crash", "feature_request", "data_export", "performance", "account"}

DEFAULT_THRESHOLD = 0.50


@dataclass
class MicroResult:
    category: str      # predicted label
    confidence: float  # max(proba), 0.0–1.0
    status: str        # "OK" | "UNSURE"
    all_proba: dict    # {category: proba} for debugging


class MicroClassifier:
    """
    Local TF-IDF + ComplementNB ticket category classifier.
    No network calls. Fit once, predict instantly.

    ComplementNB chosen because it gives discriminative probabilities
    even on small datasets (~50 training examples), unlike LogisticRegression
    which spreads probability uniformly when data is sparse.
    """

    def __init__(self, threshold: float = DEFAULT_THRESHOLD) -> None:
        self.threshold = threshold
        self._pipeline: Pipeline = Pipeline([
            ("tfidf", TfidfVectorizer(
                ngram_range=(1, 2),
                min_df=1,
                sublinear_tf=False,
                analyzer="word",
            )),
            ("clf", ComplementNB(alpha=0.1)),
        ])
        self._classes: list[str] = []

    def fit_from_jsonl(self, *paths: str | Path) -> "MicroClassifier":
        texts, labels = [], []
        for path in paths:
            t, l = _load_jsonl(path)
            texts.extend(t)
            labels.extend(l)
        if not texts:
            raise ValueError(f"No training examples found in {paths}")
        self._pipeline.fit(texts, labels)
        self._classes = [str(c) for c in self._pipeline.classes_]
        return self

    def predict(self, text: str) -> MicroResult:
        if not self._classes:
            raise RuntimeError("Call fit_from_jsonl() before predict()")
        proba = self._pipeline.predict_proba([text])[0]
        all_proba = dict(zip(self._classes, [round(float(p), 4) for p in proba]))
        best_idx = int(proba.argmax())
        category = self._classes[best_idx]
        confidence = round(float(proba[best_idx]), 4)
        status = "OK" if confidence >= self.threshold else "UNSURE"
        return MicroResult(category=category, confidence=confidence, status=status, all_proba=all_proba)

    @property
    def is_fitted(self) -> bool:
        return bool(self._classes)


# ── Data loading ──────────────────────────────────────────────────────────────

def _load_jsonl(path: str | Path) -> tuple[list[str], list[str]]:
    texts, labels = [], []
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            record = json.loads(line)
            messages = record.get("messages", [])
            if len(messages) < 3:
                continue
            user_text = messages[1].get("content", "")
            assistant_raw = messages[2].get("content", "")
            label_data = json.loads(assistant_raw)
            category = label_data.get("category", "")
            if category in VALID_CATEGORIES and user_text:
                texts.append(user_text)
                labels.append(category)
        except (json.JSONDecodeError, KeyError, IndexError):
            continue
    return texts, labels
