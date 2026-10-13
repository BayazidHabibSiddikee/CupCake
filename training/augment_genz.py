import json
import random
import re
from pathlib import Path

random.seed(321)

BASE_DIR = Path(__file__).resolve().parent.parent
DATASET_DIR = BASE_DIR / "training" / "dataset"
OUTPUT_FILE = DATASET_DIR / "liltii_fewshot.jsonl"
TRAIN_FILE = DATASET_DIR / "train.jsonl"
VAL_FILE = DATASET_DIR / "val.jsonl"

# ---------------------------------------------------------------------------
# Gen Z / internet-culture Banglish augmentation
# ---------------------------------------------------------------------------

GENZ_PREFIXES = [
    "bruh ", "bro ", "sis ", "fam ", "no cap ", "fr ", "lowkey ", "highkey ",
    "deadass ", "real ", "bestie ", "g ", "gang ", "pov: ", "nvm ", "omg ",
    "lol ", "lmao ", "pls ", "plz ", "tho ", "ngl ", "tbh ", "iykyk ",
]
GENZ_CONNECTORS = [
    " but like ", " and like ", " so like ", " plus ", " also ",
    " fr ", " ngl ", " tbh ", " idk ", " smh ", " ikr ", " bruh ",
]
GENZ_FILLERS = [
    " like ", " actually ", " basically ", " i mean ", " kinda ",
    " sorta ", " yk? ", " yk what i mean ", " fr fr ",
]
GENZ_SLANG_NOUNS = [
    "vibe", "mood", "rizz", "aura", "skibidi", "sigma", "alpha", "beta",
    "fanum tax", "gyatt", "cap", "no cap", "bussin", "slay", "queen", "king",
    "hustle", "grind", "main character", "plot", "lore", "canon", "buff",
    "nerf", "meta", "glow up", "red flag", "green flag", "ick", "delusion",
    "touch grass", "skill issue", "based", "cringe", "mid", "w move", "l move",
]
GENZ_SLANG_VERBS = [
    "slay", "vibe", "chill", "grind", "hustle", "yeet", "rizz", "touch grass",
    "log off", "scroll", "stan", "ship", "cancel", "spam", "ping", "ghost",
    "flex", "hype", "cook", "serve", "flop", "clap", "drag", "snitch",
]
GENZ_SLANG_ADJ = [
    "slay", "bussin", "mid", "based", "cringe", "deadass", "cap", "no cap",
    "delulu", "solana", "gyatt", "skibidi", "sigma", "alpha", "omega",
    "real", "fake", "sus", "rizz", "fanum taxed", "glowing", "locked in",
]
GENZ_EMOJI = ["🔥", "💀", "😭", "😩", "👁️👄👁️", "🤓", "😎", "🥶", "🤡", "💅", "✨", "🫡", "🫠", "😤", "🫡", "👌", "💯"]
GENZ_PUNCT = ["!!", "!!1", "!1!1", " lmao", " pls", " fr", " tbh", " smh", " ikr"]

# Banglish word pool: Bangla -> English for code-mixing
BANGLISH_WORDS = {
    "ami": ["I", "me", "i"],
    "tumi": ["you", "u"],
    "tui": ["you", "u"],
    "amake": ["me"],
    "tomake": ["you", "u"],
    "amar": ["my", "myy"],
    "tomar": ["your", "ur"],
    "korchi": ["doing", "doin"],
    "korbo": ["will do", "gonna do"],
    "koro": ["do", "pls do"],
    "kori": ["do", "doin"],
    "parchi": ["can", "can do"],
    "parbo": ["can do", "will try"],
    "jabo": ["will go", "gonna go"],
    "ashbo": ["will come", "gonna come"],
    "jachhi": ["going", "goin"],
    "ashchi": ["coming", "comin"],
    "khawa": ["food", "khana"],
    "ghum": ["sleep", "zzz"],
    "khida": ["hungry", "hungry fr"],
    "valo": ["good", "bhalo", "good ngl"],
    "kharap": ["bad", "bad fr"],
    "bhalo": ["good", "good vibe"],
    "thik": ["okay", "alright", "okk"],
    "kono": ["some", "any"],
    "ekta": ["a", "an"],
    "shudhu": ["only", "just", "only fr"],
    "onek": ["very", "so much", "fr much"],
    "beshi": ["too much", "so much"],
    "kom": ["less", "lesss"],
    "age": ["before", "ago"],
    "ekhon": ["now", "rn"],
    "ajke": ["today", "today fr"],
    "kal": ["tomorrow", "tmrw"],
    "gayer": ["yesterday", "yday"],
    "kothay": ["where", "where tho"],
    "keno": ["why", "y tho"],
    "ki": ["what", "wth"],
    "kemon": ["how", "how tf"],
    "kobe": ["when", "when tho"],
    "na": ["no", "nah", "nope"],
    "hae": ["yes", "yess", "fr"],
    "dhonyobad": ["thanks", "thx", "ty"],
    "sorry": ["sorry", "srry"],
    "kicchu": ["nothing", "nothin"],
    "sob": ["all", "all of it"],
    "kichu": ["something", "smth"],
    "ar": ["and", "n"],
    "othoba": ["or", "orrr"],
    "kintu": ["but", "but like"],
    "jodi": ["if", "if like"],
    "tahole": ["then", "then like"],
}


def _swap_banglish(text: str, intensity: float = 0.4) -> str:
    if not text:
        return text
    for bangla, candidates in BANGLISH_WORDS.items():
        if random.random() < intensity:
            pattern = re.compile(r"(?<!\w)" + re.escape(bangla) + r"(?!\w)", flags=re.IGNORECASE)
            if pattern.search(text):
                text = pattern.sub(random.choice(candidates), text)
    return text


def _inject_gz_prefix(text: str) -> str:
    if not text or random.random() < 0.35:
        return text
    return f"{random.choice(GENZ_PREFIXES).strip()} {text}"


def _inject_gz_suffix(text: str) -> str:
    if not text or random.random() < 0.45:
        return text
    parts = [text]
    if random.random() < 0.55:
        parts.append(random.choice(GENZ_CONNECTORS).strip())
    if random.random() < 0.5:
        parts.append(random.choice(GENZ_FILLERS).strip())
    if random.random() < 0.35:
        parts.append(random.choice(GENZ_SLANG_NOUNS))
    if random.random() < 0.3:
        parts.append(random.choice(GENZ_SLANG_VERBS))
    if random.random() < 0.25:
        parts.append(random.choice(GENZ_SLANG_ADJ))
    if random.random() < 0.2:
        parts.append(random.choice(GENZ_EMOJI))
    if random.random() < 0.35:
        parts.append(random.choice(GENZ_PUNCT))
    out = " ".join(parts)
    return re.sub(r"\s+", " ", out).strip()


def _noisify(text: str, intensity: str = "light") -> str:
    if not text:
        return text
    if intensity == "light":
        return text
    out = text
    if random.random() < 0.25:
        out = re.sub(r"\b(\w+)\1\b", r"\1", out)
    if random.random() < 0.2:
        out = out.replace(" you", " u").replace(" your", " ur").replace(" are ", " r ")
    if random.random() < 0.15:
        out = re.sub(r"\b(why|what|how)\b", lambda m: m.group(1).upper(), out, flags=re.IGNORECASE)
    if random.random() < 0.1:
        out = out + " " + random.choice(["fr", "ngl", "tbh", "lol", "lmao", "smh"])
    return re.sub(r"\s+", " ", out).strip()


def augment_genz(example: dict, n_variants: int = 4) -> list[dict]:
    user = example["messages"][0]["content"]
    assistant = example["messages"][1]["content"]

    m = re.match(r"^\[(.+?)\]\s*\*(.+?)\*\s*(.*)$", assistant, re.DOTALL)
    if not m:
        return []
    emotion, action, body = m.group(1), m.group(2), m.group(3)

    intensities = ["light", "medium", "heavy"]
    variants = []
    for _ in range(n_variants):
        intensity = random.choice(intensities)
        swap_prob = 0.25 if intensity == "light" else 0.45 if intensity == "medium" else 0.65
        new_user = _inject_gz_prefix(user)
        new_user = _swap_banglish(new_user, intensity=swap_prob * 0.8)
        new_user = _inject_gz_suffix(new_user)
        new_user = _noisify(new_user, intensity=intensity)

        new_body = _swap_banglish(body, intensity=swap_prob)
        new_body = _inject_gz_suffix(new_body)
        new_body = _noisify(new_body, intensity=intensity)

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


def build_genz_dataset(target_size: int = 2000) -> list[dict]:
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
    max_attempts = target_size * 15
    while len(augmented) < target_size and attempts < max_attempts:
        attempts += 1
        base_ex = random.choice(base)
        variants = augment_genz(base_ex, n_variants=random.randint(2, 6))
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
    examples = build_genz_dataset(target_size=2000)
    save_dataset(examples)
