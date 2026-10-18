#!/usr/bin/env python3
"""Generates, verifies and de-duplicates trilingual quiz questions with Gemini.

Usage:
    export GEMINI_API_KEY=...            # optional when a proxy injects credentials
    python3 tools/questions/generate_bank.py generate   # writes batches to tools/questions/cache/
    python3 tools/questions/generate_bank.py verify     # second, independent answer for each question
    python3 tools/questions/generate_bank.py build      # filters, de-duplicates, writes app assets

Each step is resumable: finished batches are cached and skipped on the next run.
"""
import concurrent.futures
import json
import os
import random
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / "cache"
ASSETS = ROOT / "app/src/main/assets"
# Free-tier quotas are per model and per day (20 requests on the key used to build this bank),
# so the script rotates through several strong models and moves on when one runs out.
MODELS = os.environ.get("GEMINI_MODELS", "gemini-3.7-flash,gemini-3-flash-preview,gemini-3.6-flash").split(",")
STREAM_API = "https://generativelanguage.googleapis.com/v1beta/models/{}:streamGenerateContent?alt=sse"
exhausted = set()
LANGS = ("ar", "fr", "en")

# Same order as AppLanguage.topics in the app.
TOPICS = [
    ("General knowledge", "everyday facts, world culture, famous inventions, food, flags, records that never change"),
    ("Science", "physics, chemistry, biology, astronomy, the human body, mathematics"),
    ("History", "world history and Arab/Islamic history, dates, empires, famous figures"),
    ("Geography", "countries, capitals, rivers, mountains, deserts, seas, with a good share of the Arab world and Africa"),
    ("Sport", "football (World Cup, AFCON), Olympics, tennis, athletics; only records and facts that are settled history"),
    ("Religion and Islamic civilisation", "the Quran, prophets, Islamic history, scholars, architecture; factual, respectful, no sectarian or jurisprudential disputes"),
    ("Technology and programming", "computers, the internet, programming languages, famous companies and inventions, how things work"),
    ("Literature and language", "Arabic, French and world literature, poets, famous books, grammar and vocabulary curiosities"),
    ("Film and TV", "classic films, animation, famous directors and actors, Arab and Moroccan cinema; nothing that depends on recent box-office numbers"),
    ("The Maghreb", "Morocco, Algeria, Tunisia, Libya and Mauritania: history, dynasties, cities, monuments, culture, cuisine, geography"),
]
LEVELS = {
    1: "EASY: most adults know it (questions 1-5 of the show, worth 100 to 1,000 points)",
    2: "MEDIUM: a well-read person knows it (questions 6-10, worth 2,000 to 32,000 points)",
    3: "HARD: only enthusiasts or experts know it (questions 11-15, worth 64,000 to 1,000,000 points)",
}
PER_BATCH = int(os.environ.get("PER_BATCH", 40))
BATCHES_PER_CELL = 1  # 10 topics x 3 levels x 40 = 1,200 candidates
VERIFY_BATCH = 60

LOCALISED = {"type": "OBJECT", "properties": {
    "text": {"type": "STRING"},
    "options": {"type": "ARRAY", "items": {"type": "STRING"}},
    "explanation": {"type": "STRING"},
}, "required": ["text", "options", "explanation"]}

GENERATION_SCHEMA = {"type": "ARRAY", "items": {"type": "OBJECT", "properties": {
    "correctIndex": {"type": "INTEGER"},
    "ar": LOCALISED, "fr": LOCALISED, "en": LOCALISED,
}, "required": ["correctIndex", "ar", "fr", "en"]}}

VERIFY_SCHEMA = {"type": "ARRAY", "items": {"type": "OBJECT", "properties": {
    "id": {"type": "STRING"},
    "answerIndex": {"type": "INTEGER"},
    "confident": {"type": "BOOLEAN"},
    "ambiguous": {"type": "BOOLEAN"},
    "timeSensitive": {"type": "BOOLEAN"},
    "translationMismatch": {"type": "BOOLEAN"},
    "difficultyFits": {"type": "BOOLEAN"},
    "note": {"type": "STRING"},
}, "required": ["id", "answerIndex", "confident", "ambiguous", "timeSensitive", "translationMismatch", "difficultyFits"]}}


class DailyQuotaExceeded(Exception):
    pass


def call_gemini(prompt, schema, prefer=None, avoid=None, attempts=6):
    """Returns (parsed JSON, usage, model). Streams the answer (SSE) with thought summaries so bytes keep
    flowing while the model thinks: some proxies drop connections that stay silent for ~30 s."""
    body = json.dumps({
        "contents": [{"role": "user", "parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": 1.0,
            "responseMimeType": "application/json",
            "responseSchema": schema,
            "thinkingConfig": {"includeThoughts": True},
        },
    }).encode()
    headers = {"Content-Type": "application/json"}
    if os.environ.get("GEMINI_API_KEY"):
        headers["x-goog-api-key"] = os.environ["GEMINI_API_KEY"]
    for attempt in range(attempts):
        order = ([prefer] if prefer else []) + [m for m in MODELS if m != prefer]
        usable = [m for m in order if m not in exhausted]
        model = next((m for m in usable if m != avoid), usable[0] if usable else None)
        if model is None:
            raise DailyQuotaExceeded("every model is out of daily quota")
        try:
            request = urllib.request.Request(STREAM_API.format(model), data=body, headers=headers, method="POST")
            text, usage = [], {}
            with urllib.request.urlopen(request, timeout=900) as response:
                for raw in response:
                    line = raw.decode("utf-8").strip()
                    if not line.startswith("data:"):
                        continue
                    chunk = json.loads(line[5:])
                    usage = chunk.get("usageMetadata", usage)
                    for cand in chunk.get("candidates", []):
                        for part in cand.get("content", {}).get("parts", []):
                            if not part.get("thought"):
                                text.append(part.get("text", ""))
            return json.loads("".join(text)), usage, model
        except urllib.error.HTTPError as e:
            detail = e.read().decode("utf-8", "replace")
            if e.code == 429 and "PerDay" in detail:
                print(f"  ! {model} is out of daily quota", flush=True)
                exhausted.add(model)
                continue
            wait = min(120, 15 * 2 ** attempt) + random.uniform(0, 5)
            print(f"  ! {model}: HTTP {e.code}, retrying in {wait:.0f}s", flush=True)
            time.sleep(wait)
        except (urllib.error.URLError, TimeoutError, ConnectionError, KeyError, json.JSONDecodeError) as e:
            wait = min(120, 15 * 2 ** attempt) + random.uniform(0, 5)
            print(f"  ! {model}: {type(e).__name__}, retrying in {wait:.0f}s", flush=True)
            time.sleep(wait)
    raise RuntimeError("Gemini kept failing")


# ---------------------------------------------------------------- generate

def generation_prompt(topic, hint, level, avoid):
    avoid_text = "\n".join(f"- {q}" for q in avoid) or "- (none yet)"
    return f"""You write questions for a TV quiz show in the style of "Who Wants to Be a Millionaire",
for an app played mostly in Morocco and the Arab world, in Arabic, French and English.

Write {PER_BATCH} multiple-choice questions.
Topic: {topic} ({hint}).
Difficulty: {LEVELS[level]}.

Rules:
- Exactly 4 short options and exactly ONE indisputably correct answer. No "all of the above", no trick wording.
- Only stable facts: nothing about current office holders, current records, populations, prices or anything that may change.
- Every fact must be correct; if you are not sure, write a different question.
- Wrong options must be plausible and of the same kind as the answer.
- Vary the position of the correct answer (correctIndex 0 to 3) across the batch.
- Give the SAME question in three languages: "ar" in Modern Standard Arabic, "fr" in correct French, "en" in British English.
  The options must be in the same order in the three languages, so one correctIndex fits all three.
- explanation: one or two sentences giving the answer and a useful extra fact.
- Cover different sub-topics; do not repeat or closely paraphrase any of these existing questions:
{avoid_text}
"""


def existing_english():
    path = ASSETS / "questions_en.json"
    return [q["text"] for q in json.loads(path.read_text())] if path.exists() else []


def generate():
    CACHE.mkdir(exist_ok=True)
    cells = [(t, l, b) for t in range(len(TOPICS)) for l in LEVELS for b in range(BATCHES_PER_CELL)]

    def run(cell):
        topic, level, batch = cell
        out = CACHE / f"gen_t{topic}_l{level}_b{batch}.json"
        if out.exists():
            return
        # Later batches of the same cell avoid what earlier ones produced.
        avoid = []
        for previous in range(batch):
            prev = CACHE / f"gen_t{topic}_l{level}_b{previous}.json"
            if prev.exists():
                avoid += [q["en"]["text"] for q in json.loads(prev.read_text())]
        name, hint = TOPICS[topic]
        started = time.time()
        try:
            questions, usage, model = call_gemini(generation_prompt(name, hint, level, avoid), GENERATION_SCHEMA)
        except (DailyQuotaExceeded, RuntimeError) as e:
            print(f"  {out.name}: skipped ({e}); run again later to resume", flush=True)
            return
        for q in questions:
            q["topic"], q["difficulty"], q["model"] = topic, level, model
        out.write_text(json.dumps(questions, ensure_ascii=False, indent=1))
        print(f"  {out.name}: {len(questions)} questions in {time.time() - started:.0f}s, "
              f"{usage.get('totalTokenCount')} tokens", flush=True)

    # First batch of every cell in parallel, then the second batches (they need the first as "avoid" list).
    for batch in range(BATCHES_PER_CELL):
        todo = [c for c in cells if c[2] == batch]
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            list(pool.map(run, todo))


# ---------------------------------------------------------------- verify

def load_candidates():
    candidates = []
    for path in sorted(CACHE.glob("gen_t*_l*_b*.json")):
        m = re.match(r"gen_t(\d+)_l(\d)_b(\d+)\.json", path.name)
        for i, q in enumerate(json.loads(path.read_text())):
            q["id"] = f"g{m[1]}-{m[2]}-{m[3]}-{i:02d}"
            candidates.append(q)
    return candidates


def verify_prompt(batch):
    lines = []
    for q in batch:
        lines.append(json.dumps({
            "id": q["id"],
            "intendedDifficulty": q["difficulty"],
            "en": {"text": q["en"]["text"], "options": q["en"]["options"]},
            "fr": {"text": q["fr"]["text"], "options": q["fr"]["options"]},
            "ar": {"text": q["ar"]["text"], "options": q["ar"]["options"]},
        }, ensure_ascii=False))
    return """You are a strict fact-checker for a quiz show. For each question below, answer it YOURSELF
(you are not told the intended answer) and report:
- answerIndex: the index (0-3) of the option you believe is correct.
- confident: true only if you are sure of the fact.
- ambiguous: true if more than one option could be defended, or none is correct.
- timeSensitive: true if the answer could change over time or depends on recent events.
- translationMismatch: true if the en/fr/ar versions differ in meaning or the options are not in the same order.
- difficultyFits: false if the question is clearly much easier or harder than intendedDifficulty (1 easy, 2 medium, 3 hard).
- note: a few words when something is wrong.

Questions (one JSON object per line):
""" + "\n".join(lines)


def verify():
    candidates = load_candidates()
    batches = [candidates[i:i + VERIFY_BATCH] for i in range(0, len(candidates), VERIFY_BATCH)]

    def run(index):
        out = CACHE / f"verify_{index:03d}.json"
        if out.exists():
            return
        # Check with a different model than the one that wrote most of the batch, when one is available.
        writers = [q.get("model") for q in batches[index]]
        writer = max(set(writers), key=writers.count)
        try:
            verdicts, usage, model = call_gemini(verify_prompt(batches[index]), VERIFY_SCHEMA, avoid=writer)
        except (DailyQuotaExceeded, RuntimeError) as e:
            print(f"  {out.name}: skipped ({e}); run again later to resume", flush=True)
            return
        for v in verdicts:
            v["checker"] = model
        out.write_text(json.dumps(verdicts, ensure_ascii=False, indent=1))
        print(f"  {out.name}: {len(verdicts)} verdicts by {model} (questions by {writer}), "
              f"{usage.get('totalTokenCount')} tokens", flush=True)

    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(run, range(len(batches))))


# ---------------------------------------------------------------- build

STOP = set("the a an of in on is was which what who whom whose to for by and or with from at as did does do this that these "
           "how many much called known name first".split())


def words(text):
    return {w for w in re.findall(r"[a-z0-9]+", text.lower()) if w not in STOP}


def similar(a, b):
    wa, wb = words(a["en"]["text"]), words(b["en"]["text"])
    if not wa or not wb:
        return False
    overlap = len(wa & wb) / len(wa | wb)
    same_answer = a["en"]["options"][a["correctIndex"]].lower() == b["en"]["options"][b["correctIndex"]].lower()
    return overlap >= 0.6 or (same_answer and overlap >= 0.35)


def well_formed(q):
    if q["correctIndex"] not in range(4):
        return False
    for lang in LANGS:
        loc = q[lang]
        opts = [o.strip() for o in loc["options"]]
        if not loc["text"].strip() or not loc["explanation"].strip() or len(opts) != 4 or len(set(opts)) != 4 or not all(opts):
            return False
    return True


def build():
    candidates = {q["id"]: q for q in load_candidates()}
    verdicts = {}
    for path in sorted(CACHE.glob("verify_*.json")):
        for v in json.loads(path.read_text()):
            verdicts[v["id"]] = v

    reasons = {}
    kept = []
    existing = {lang: json.loads((ASSETS / f"questions_{lang}.json").read_text()) for lang in LANGS}
    existing_ids = {q["id"] for q in existing["en"]}
    reference = [
        {"en": q, "correctIndex": q["correctIndex"]} for q in existing["en"] if not q["id"].startswith("g")
    ]
    for qid, q in candidates.items():
        v = verdicts.get(qid)
        if not well_formed(q):
            reason = "malformed"
        elif v is None:
            reason = "not verified"
        elif v["answerIndex"] != q["correctIndex"]:
            reason = "checker disagrees"
        elif not v["confident"] or v["ambiguous"] or v["timeSensitive"]:
            reason = "uncertain/ambiguous/time-sensitive"
        elif v["translationMismatch"]:
            reason = "translation mismatch"
        elif not v.get("difficultyFits", True):
            reason = "wrong difficulty"
        elif any(similar(q, other) for other in reference + kept):
            reason = "duplicate"
        else:
            kept.append(q)
            continue
        reasons[reason] = reasons.get(reason, 0) + 1

    print(f"candidates: {len(candidates)}, kept: {len(kept)}, dropped: {reasons}")
    for level in LEVELS:
        print(f"  level {level}: {sum(q['difficulty'] == level for q in kept)}")

    topic_names = {
        "ar": ["ثقافة عامة", "علوم", "تاريخ", "جغرافيا", "رياضة", "دين وحضارة إسلامية",
               "تقنية وبرمجة", "أدب ولغة", "أفلام ومسلسلات", "المغرب العربي"],
        "fr": ["Culture générale", "Sciences", "Histoire", "Géographie", "Sport", "Religion et civilisation islamique",
               "Technologie et programmation", "Littérature et langue", "Cinéma et séries", "Le Maghreb"],
        "en": [t[0] for t in TOPICS[:8]] + ["Film and TV", "The Maghreb"],
    }
    for lang in LANGS:
        base = [q for q in existing[lang] if not q["id"].startswith("g")]  # keep the hand-written ones
        generated = [{
            "id": q["id"],
            "text": q[lang]["text"].strip(),
            "options": [o.strip() for o in q[lang]["options"]],
            "correctIndex": q["correctIndex"],
            "difficulty": q["difficulty"],
            "explanation": q[lang]["explanation"].strip(),
            "category": topic_names[lang][q["topic"]],
        } for q in kept]
        (ASSETS / f"questions_{lang}.json").write_text(json.dumps(base + generated, ensure_ascii=False, indent=1))
        print(f"  questions_{lang}.json: {len(base) + len(generated)} questions")
    _ = existing_ids


if __name__ == "__main__":
    {"generate": generate, "verify": verify, "build": build}[sys.argv[1] if len(sys.argv) > 1 else "generate"]()
