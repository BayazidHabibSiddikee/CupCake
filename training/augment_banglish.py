import json
import random
import re
from pathlib import Path

random.seed(123)

BASE_DIR = Path(__file__).resolve().parent.parent
DATASET_DIR = BASE_DIR / "training" / "dataset"
OUTPUT_FILE = DATASET_DIR / "liltii_fewshot.jsonl"
TRAIN_FILE = DATASET_DIR / "train.jsonl"
VAL_FILE = DATASET_DIR / "val.jsonl"

# ---------------------------------------------------------------------------
# Banglish augmentation: English words + Bangla grammar/case markers
# ---------------------------------------------------------------------------

BANGLISH_PREFIXES = [
    "bhai ", "dosto ", "yaar ", "bhaiya ", "dada ", "apu ",
    "shudhu ", "sry ", "thx ", "omg ", "lol ", "bro ", "sis ",
]
BANGLISH_CONNECTORS = [
    "but ", "and ", "so ", "then ", "also ", "still ", "btw ",
    "bt ", "anyway ", "plus ", "plus amar ",
]
BANGLISH_FILLERS = [
    "like ", "actually ", "basically ", "i mean ", "tbh ", "ngl ",
    "fr ", "alright ", "okay ", "hmm ", "umm ",
]
BANGLISH_BANGLISH = [
    ("ami", "I"),
    ("tumi", "you"),
    ("tui", "you"),
    ("amake", "me"),
    ("tomake", "you"),
    ("amar", "my"),
    ("tomar", "your"),
    ("korchi", "doing"),
    ("korbo", "will do"),
    ("koro", "do"),
    ("kori", "do"),
    ("parchi", "can"),
    ("parbo", "will be able"),
    ("jabo", "will go"),
    ("ashbo", "will come"),
    ("jachhi", "going"),
    ("ashchi", "coming"),
    ("khawa", "food"),
    ("ghum", "sleep"),
    ("khida", "hungry"),
    ("valo", "good"),
    ("kharap", "bad"),
    ("bhalo", "good"),
    ("thik", "okay"),
    ("kono", "some/any"),
    ("ekta", "a"),
    ("shudhu", "only"),
    ("onek", "very/a lot"),
    ("beshi", "too much"),
    ("kom", "less"),
    ("age", "before"),
    ("ekhon", "now"),
    ("ajke", "today"),
    ("kal", "tomorrow"),
    ("gayer", "yesterday"),
    ("kothay", "where"),
    ("keno", "why"),
    ("ki", "what"),
    ("kemon", "how"),
    ("kobe", "when"),
    ("na", "no/not"),
    ("hae", "yes"),
    ("dhonyobad", "thanks"),
    ("sorry", "sorry"),
    ("kicchu", "nothing"),
    ("kono", "any"),
    ("sob", "all"),
    ("kichu", "something"),
    ("ar", "and"),
    ("othoba", "or"),
    ("kintu", "but"),
    ("jodi", "if"),
    ("tahole", "then"),
]

BANGLISH_NOUNS = [
    "vibe", "mood", "game", "plan", "schedule", "call", "meeting",
    "assignment", "deadline", "stress", "drama", "chill", "snack",
    "break", "nap", "dream", "planning", "homework", "test", "exam",
]
BANGLISH_VERBS = [
    "text", "call", "ping", "reply", "send", "share", "scroll",
    "vibe", "chill", "charge", "log out", "focus", "study",
    "grind", "sleep", "wake", "eat", "snack", "play",
]
BANGLISH_ADJECTIVES = [
    "tired", "sleepy", "bored", "annoyed", "happy", "sad",
    "angry", "excited", "confused", "surprised", "low-key",
    "dead", "woke", "cringe", "based", "soft",
]


def _apply_banglish_swap(text: str, p: float = 0.35) -> str:
    if not text:
        return text
    # swap standalone Bangla words with English equivalents
    for bangla, english in BANGLISH_BANGLISH:
        pattern = re.compile(r"(?<!\w)" + re.escape(bangla) + r"(?!\w)", flags=re.IGNORECASE)
        if pattern.search(text) and random.random() < p:
            text = pattern.sub(english, text)
    return text


def _inject_banglish_markers(text: str, intensity: str = "medium") -> str:
    if not text:
        return text
    if intensity == "light":
        return text
    parts = [text]

    if intensity in ("medium", "heavy"):
        if random.random() < 0.6:
            parts.insert(0, random.choice(BANGLISH_PREFIXES).strip())
        if random.random() < 0.5:
            parts.append(random.choice(BANGLISH_CONNECTORS).strip())
        if random.random() < 0.5:
            parts.append(random.choice(BANGLISH_FILLERS).strip())

    if intensity == "heavy":
        if random.random() < 0.5 and BANGLISH_NOUNS:
            parts.append(random.choice(BANGLISH_NOUNS))
        if random.random() < 0.4 and BANGLISH_VERBS:
            parts.append(random.choice(BANGLISH_VERBS))
        if random.random() < 0.35 and BANGLISH_ADJECTIVES:
            parts.append(random.choice(BANGLISH_ADJECTIVES))

    out = " ".join(parts)
    out = re.sub(r"\s+", " ", out).strip()
    return out


def augment_with_banglish(example: dict, n_variants: int = 3) -> list[dict]:
    user = example["messages"][0]["content"]
    assistant = example["messages"][1]["content"]

    m = re.match(r"^\[(.+?)\]\s*\*(.+?)\*\s*(.*)$", assistant, re.DOTALL)
    if not m:
        return []
    emotion, action, body = m.group(1), m.group(2), m.group(3)

    variants = []
    intensities = ["light", "medium", "heavy"]
    for _ in range(n_variants):
        intensity = random.choice(intensities)
        new_user = _inject_banglish_markers(user, intensity=intensity)
        new_body = _apply_banglish_swap(body, p=0.45 if intensity == "heavy" else 0.25)
        new_assistant = f"[{emotion}] *{action}* {new_body}"
        variants.append({
            "messages": [
                {"role": "user", "content": new_user},
                {"role": "assistant", "content": new_assistant},
            ]
        })
    return variants


def load_examples():
    examples = []
    with OUTPUT_FILE.open(encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            examples.append(json.loads(line))
    return examples


def save_dataset(examples: list[dict]):
    DATASET_DIR.mkdir(parents=True, exist_ok=True)
    with OUTPUT_FILE.open("w", encoding="utf-8") as f:
        for ex in examples:
            f.write(json.dumps(ex, ensure_ascii=False) + "\n")
    split = int(0.8 * len(examples))
    train = examples[:split]
    val = examples[split:]
    with TRAIN_FILE.open("w", encoding="utf-8") as f:
        for ex in train:
            f.write(json.dumps(ex, ensure_ascii=False) + "\n")
    with VAL_FILE.open("w", encoding="utf-8") as f:
        for ex in val:
            f.write(json.dumps(ex, ensure_ascii=False) + "\n")
    print(f"Saved {len(examples)} examples -> train={len(train)}, val={len(val)}")


def build_banglish_dataset(target_size: int = 1200) -> list[dict]:
    base = load_examples()
    augmented = []
    seen = set()

    def key_of(example):
        return (
            example["messages"][0]["content"].lower(),
            example["messages"][1]["content"].lower(),
        )

    for ex in base:
        augmented.append(ex)
        seen.add(key_of(ex))

    attempts = 0
    max_attempts = target_size * 12
    while len(augmented) < target_size and attempts < max_attempts:
        attempts += 1
        base_ex = random.choice(base)
        variants = augment_with_banglish(base_ex, n_variants=random.randint(2, 5))
        for v in variants:
            k = key_of(v)
            if k not in seen:
                augmented.append(v)
                seen.add(k)
            if len(augmented) >= target_size:
                break

    random.shuffle(augmented)
    return augmented[:target_size]


if __name__ == "__main__":
    examples = build_banglish_dataset(target_size=1200)
    save_dataset(examples)
