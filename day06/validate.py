#!/usr/bin/env python3
"""Validate JSONL fine-tuning dataset: format, roles, empty content."""

import json
import sys
from pathlib import Path

REQUIRED_ROLES = {"system", "user", "assistant"}
MIN_CONTENT_LEN = 5
MAX_CONTENT_LEN = 4096


def validate_file(path: Path) -> tuple[int, list[str]]:
    errors = []
    seen = set()
    valid_count = 0

    with open(path, encoding="utf-8") as f:
        for line_num, line in enumerate(f, start=1):
            line = line.strip()
            if not line:
                continue

            # JSON validity
            try:
                obj = json.loads(line)
            except json.JSONDecodeError as e:
                errors.append(f"Line {line_num}: invalid JSON — {e}")
                continue

            # Top-level structure
            if "messages" not in obj:
                errors.append(f"Line {line_num}: missing 'messages' key")
                continue

            messages = obj["messages"]
            if not isinstance(messages, list) or len(messages) < 2:
                errors.append(f"Line {line_num}: 'messages' must be a list with ≥2 items")
                continue

            # Role presence
            roles = {m.get("role") for m in messages}
            missing = REQUIRED_ROLES - roles
            if missing:
                errors.append(f"Line {line_num}: missing roles {missing}")
                continue

            # Content checks
            content_ok = True
            for msg in messages:
                role = msg.get("role", "?")
                content = msg.get("content", "")
                if not isinstance(content, str):
                    errors.append(f"Line {line_num}: role '{role}' content is not a string")
                    content_ok = False
                    break
                if len(content.strip()) < MIN_CONTENT_LEN:
                    errors.append(f"Line {line_num}: role '{role}' content too short ({len(content)} chars)")
                    content_ok = False
                    break
                if len(content) > MAX_CONTENT_LEN:
                    errors.append(f"Line {line_num}: role '{role}' content too long ({len(content)} chars)")
                    content_ok = False
                    break
            if not content_ok:
                continue

            # Assistant answer must be valid JSON
            assistant_content = next(
                (m["content"] for m in messages if m.get("role") == "assistant"), None
            )
            if assistant_content:
                try:
                    json.loads(assistant_content)
                except json.JSONDecodeError:
                    errors.append(f"Line {line_num}: assistant content is not valid JSON: {assistant_content[:60]}")
                    continue

            # Duplicate detection (by user content)
            user_content = next(
                (m["content"] for m in messages if m.get("role") == "user"), ""
            )
            if user_content in seen:
                errors.append(f"Line {line_num}: duplicate example (same user content)")
                continue
            seen.add(user_content)

            valid_count += 1

    return valid_count, errors


def main():
    files = sys.argv[1:] if len(sys.argv) > 1 else ["train.jsonl", "eval.jsonl"]
    all_ok = True

    for file_path in files:
        path = Path(file_path)
        if not path.exists():
            print(f"[SKIP] {file_path} — file not found")
            continue

        valid, errors = validate_file(path)
        total_lines = sum(1 for line in open(path, encoding="utf-8") if line.strip())

        print(f"\n{'='*50}")
        print(f"File: {file_path}")
        print(f"  Total lines : {total_lines}")
        print(f"  Valid       : {valid}")
        print(f"  Errors      : {len(errors)}")

        if errors:
            all_ok = False
            print("  Issues:")
            for err in errors:
                print(f"    ✗ {err}")
        else:
            print("  ✓ All examples valid")

    print()
    sys.exit(0 if all_ok else 1)


if __name__ == "__main__":
    main()
