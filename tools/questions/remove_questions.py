#!/usr/bin/env python3
"""Removes questions (for example after a player report) from the three language banks at once.

Usage: python3 tools/questions/remove_questions.py g3-3-0-01 e17
"""
import json
import sys
from pathlib import Path

ASSETS = Path(__file__).resolve().parents[2] / "app/src/main/assets"


def main(ids):
    for lang in ("ar", "fr", "en"):
        path = ASSETS / f"questions_{lang}.json"
        bank = json.loads(path.read_text())
        kept = [q for q in bank if q["id"] not in ids]
        path.write_text(json.dumps(kept, ensure_ascii=False, indent=1))
        print(f"{path.name}: removed {len(bank) - len(kept)}, {len(kept)} left")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    main(set(sys.argv[1:]))
