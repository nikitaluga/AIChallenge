#!/usr/bin/env python3
"""Fine-tuning client: upload dataset → create job → poll status.
Run: python finetune_client.py [--train train.jsonl] [--suffix ticket-classifier]
"""

import argparse
import os
import sys
import time
from pathlib import Path

try:
    from openai import OpenAI
except ImportError:
    print("Error: openai package not installed. Run: pip install openai")
    sys.exit(1)

BASE_MODEL = "gpt-4o-mini-2024-07-18"
POLL_INTERVAL_SEC = 30


def upload_file(client: OpenAI, path: Path) -> str:
    print(f"Uploading {path.name} ({path.stat().st_size} bytes)...")
    with open(path, "rb") as f:
        response = client.files.create(file=f, purpose="fine-tune")
    file_id = response.id
    print(f"  File uploaded: {file_id}")
    return file_id


def create_job(client: OpenAI, file_id: str, suffix: str) -> str:
    print(f"\nCreating fine-tuning job (model={BASE_MODEL}, suffix={suffix})...")
    job = client.fine_tuning.jobs.create(
        training_file=file_id,
        model=BASE_MODEL,
        suffix=suffix,
        hyperparameters={"n_epochs": 3},
    )
    job_id = job.id
    print(f"  Job created: {job_id}")
    print(f"  Status: {job.status}")
    return job_id


def poll_job(client: OpenAI, job_id: str) -> None:
    print(f"\nPolling job {job_id} every {POLL_INTERVAL_SEC}s...")
    terminal_states = {"succeeded", "failed", "cancelled"}

    while True:
        job = client.fine_tuning.jobs.retrieve(job_id)
        status = job.status
        trained_tokens = getattr(job, "trained_tokens", None)
        fine_tuned_model = getattr(job, "fine_tuned_model", None)

        token_info = f"  tokens_trained={trained_tokens}" if trained_tokens else ""
        print(f"  [{time.strftime('%H:%M:%S')}] status={status}{token_info}")

        if status == "succeeded":
            print(f"\nFine-tuning complete!")
            print(f"  Fine-tuned model: {fine_tuned_model}")
            print(f"\nTo use the model, set in SupportRoutes.kt or call directly:")
            print(f'  model = "{fine_tuned_model}"')
            break
        elif status in terminal_states:
            print(f"\nJob ended with status: {status}")
            if job.error:
                print(f"  Error: {job.error}")
            break

        # Print recent events
        events = client.fine_tuning.jobs.list_events(fine_tuning_job_id=job_id, limit=3)
        for event in reversed(list(events.data)):
            print(f"    event: {event.message}")

        time.sleep(POLL_INTERVAL_SEC)


def main():
    parser = argparse.ArgumentParser(description="OpenAI fine-tuning client for ticket classifier")
    parser.add_argument("--train", default="train.jsonl", help="Training JSONL file path")
    parser.add_argument("--suffix", default="ticket-classifier", help="Model suffix for identification")
    parser.add_argument("--job-id", help="Resume polling an existing job ID (skip upload + create)")
    args = parser.parse_args()

    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        print("Error: OPENAI_API_KEY environment variable not set")
        sys.exit(1)

    client = OpenAI(api_key=api_key)

    if args.job_id:
        # Resume polling only
        poll_job(client, args.job_id)
        return

    train_path = Path(args.train)
    if not train_path.exists():
        print(f"Error: training file not found: {train_path}")
        sys.exit(1)

    print("=== Day 06 — Fine-tuning Client ===\n")
    file_id = upload_file(client, train_path)
    job_id = create_job(client, file_id, args.suffix)

    print(f"\nTo resume polling later: python finetune_client.py --job-id {job_id}")
    poll_job(client, job_id)


if __name__ == "__main__":
    main()
