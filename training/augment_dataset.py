import json
import random
import re
from pathlib import Path

random.seed(42)

BASE_DIR = Path(__file__).resolve().parent.parent
DATASET_DIR = BASE_DIR / "training" / "dataset"
OUTPUT_FILE = DATASET_DIR / "liltii_fewshot.jsonl"
TRAIN_FILE = DATASET_DIR / "train.jsonl"
VAL_FILE = DATASET_DIR / "val.jsonl"


# ---------------------------------------------------------------------------
# Lightweight paraphrase / variation helpers
# ---------------------------------------------------------------------------

PREFIXES = [
    "hey", "hi", "hello", "umm", "so", "listen", "okay so", "actually",
    "fyi", "psst", "yo", "look", "tell me", "just wondering",
]
QUESTION_SUFFIXES = [
    "", "??", "?!", "???", " lol", " tbh", " ig", " ngl",
]
BANGLA_PREFIXES = [
    "", "bhai, ", "dosto, ", "shudhu, ", "ache, ", "kono din, ",
]
BANGLA_SUFFIXES = [
    "", " vai", " re", " da", " ki?", " na?", " vaiya",
]
ACTION_VARIANTS = {
    "yawns": ["yawns loudly", "yawns cutely", "yawns", "yawns with stretched arms"],
    "jumps": ["jumps", "jumps up", "jolts up", "bounces up"],
    "tilts head": ["tilts head", "tilts head sideways", "tilts head curiously"],
    "giggles": ["giggles", "giggles softly", "giggles and covers mouth"],
    "sighs": ["sighs", "sighs loudly", "sighs softly"],
    "cries": ["cries", "cries softly", "sniffles", "tears up"],
    "glares": ["glares", "glares intensely", "glares back"],
    "stomps": ["stomps", "stomps foot", "stomps angrily"],
    "nods": ["nods", "nods slowly", "nods thoughtfully"],
    "smiles": ["smiles", "smiles brightly", "smiles softly", "grins"],
    "frowns": ["frowns", "frowns deeply", "frowns sadly"],
    "hugs": ["hugs you", "hugs tightly", "hugs back"],
    "claps": ["claps", "claps hands", "claps excitedly"],
    "blushes": ["blushes", "blushes deeply", "blushes and looks away"],
    "waves": ["waves", "waves cheerfully", "waves back"],
}
LENGTH_EXPANDERS = {
    "short": [
        "thik ache", "valo ache", "ki obostha", "ghum", "khelbo",
        "bhalo", "kharap", "sorry", "dhonyobad", "hae", "na",
    ],
    "medium": [
        "ami onek khushi", "amar khida korche", "tumi ki bolo?",
        "ajke onek boring", "amar ghum lagche", "life is good",
        "tumi amar best friend", "ami miss kori tomake",
    ],
    "long": [
        "ami ajke onek klanto feel korsi, khawa khawa ichhe korche na...",
        "tumi ki jano? ami ajke ekta amazing event e chilo...",
        "bangla onek sweet bhasha, ami eto proud feel korsi...",
    ],
}


def load_base_examples():
    examples = []
    with OUTPUT_FILE.open(encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            obj = json.loads(line)
            examples.append(obj)
    return examples


def paraphrase_user(text: str) -> str:
    variants = []

    # add casual prefix
    prefix = random.choice(PREFIXES)
    if prefix and not text.lower().startswith(prefix):
        variants.append(f"{prefix} {text}")

    # add bangla prefix
    bangla_prefix = random.choice(BANGLA_PREFIXES)
    if bangla_prefix and not text.lower().startswith(bangla_prefix):
        variants.append(f"{bangla_prefix}{text}")

    # lowercase variant
    if random.random() < 0.3:
        variants.append(text.lower())

    # question suffix
    if "?" not in text and random.random() < 0.4:
        suffix = random.choice(QUESTION_SUFFIXES)
        variants.append(f"{text}{suffix}")

    # typo-ish: drop a vowel occasionally
    if random.random() < 0.15 and len(text) > 5:
        pos = random.randint(1, len(text) - 2)
        noisy = text[:pos] + text[pos + 1 :]
        variants.append(noisy)

    # remove duplicates and original
    out = list(dict.fromkeys(variants))
    if text in out:
        out.remove(text)
    random.shuffle(out)
    return out


def vary_action(action: str) -> str:
    key = action.lower()
    if key in ACTION_VARIANTS:
        return random.choice(ACTION_VARIANTS[key])
    return action


def vary_reply(reply: str) -> str:
    parts = reply.split(" ")
    if len(parts) > 6 and random.random() < 0.35:
        # trim a random tail phrase
        cut = random.randint(3, max(3, len(parts) - 3))
        return " ".join(parts[:cut])
    if len(parts) < 12 and random.random() < 0.35:
        # append random medium expander
        return f"{reply} {random.choice(LENGTH_EXPANDERS['medium'])}"
    if random.random() < 0.2:
        # prepend bangla suffix as prefix occasionally
        return f"{random.choice(BANGLA_SUFFIXES).strip()} {reply}"
    return reply


def augment_example(example: dict, n_variants: int = 4) -> list[dict]:
    user = example["messages"][0]["content"]
    assistant = example["messages"][1]["content"]

    # parse [emotion] *action* body
    m = re.match(r"^\[(.+?)\]\s*\*(.+?)\*\s*(.*)$", assistant, re.DOTALL)
    if not m:
        return []
    emotion, action, body = m.group(1), m.group(2), m.group(3)

    variants = []
    user_paras = paraphrase_user(user)
    for u in user_paras[:n_variants]:
        new_action = vary_action(action) if random.random() < 0.5 else action
        new_body = vary_reply(body) if random.random() < 0.5 else body
        new_assistant = f"[{emotion}] *{new_action}* {new_body}"
        variants.append({
            "messages": [
                {"role": "user", "content": u},
                {"role": "assistant", "content": new_assistant},
            ]
        })
    return variants


def build_augmented_dataset(target_size: int = 800) -> list[dict]:
    base = load_base_examples()
    augmented = []
    seen = set()

    def key_of(example):
        return (
            example["messages"][0]["content"].lower(),
            example["messages"][1]["content"].lower(),
        )

    # keep originals
    for ex in base:
        augmented.append(ex)
        seen.add(key_of(ex))

    # expand until target
    attempts = 0
    max_attempts = target_size * 10
    while len(augmented) < target_size and attempts < max_attempts:
        attempts += 1
        base_ex = random.choice(base)
        variants = augment_example(base_ex, n_variants=random.randint(2, 5))
        for v in variants:
            k = key_of(v)
            if k not in seen:
                augmented.append(v)
                seen.add(k)
            if len(augmented) >= target_size:
                break

    random.shuffle(augmented)
    return augmented[:target_size]


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


if __name__ == "__main__":
    examples = build_augmented_dataset(target_size=800)
    save_dataset(examples)
