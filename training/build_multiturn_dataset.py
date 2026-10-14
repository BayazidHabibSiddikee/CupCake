import json
import random
from pathlib import Path

random.seed(777)

BASE_DIR = Path(__file__).resolve().parent.parent
DATASET_DIR = BASE_DIR / "training" / "dataset"
TRAIN_FILE = DATASET_DIR / "train.jsonl"
VAL_FILE = DATASET_DIR / "val.jsonl"
MULTI_TRAIN = DATASET_DIR / "multiturn_train.jsonl"
MULTI_VAL = DATASET_DIR / "multiturn_val.jsonl"


def load_examples():
    examples = []
    for path in [TRAIN_FILE, VAL_FILE]:
        with path.open(encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    examples.append(json.loads(line))
    return examples


def build_multiturn(examples, turns=(2, 4)):
    out = []
    # simple multi-turn builder: chain single-turn examples into short conversations
    for _ in range(len(examples)):
        length = random.randint(turns[0], turns[1])
        convo = []
        last_assistant = None
        for _ in range(length):
            ex = random.choice(examples)
            user = ex["messages"][0]["content"]
            assistant = ex["messages"][1]["content"]
            if last_assistant is not None:
                convo.append({"role": "user", "content": user})
                convo.append({"role": "assistant", "content": assistant})
            else:
                convo.append({"role": "user", "content": user})
                convo.append({"role": "assistant", "content": assistant})
            last_assistant = assistant
        out.append({"messages": convo})
    return out


def save_dataset(examples, train_path, val_path):
    train_path.parent.mkdir(parents=True, exist_ok=True)
    random.shuffle(examples)
    split = int(0.8 * len(examples))
    train = examples[:split]
    val = examples[split:]
    with train_path.open("w", encoding="utf-8") as f:
        for ex in train:
            f.write(json.dumps(ex, ensure_ascii=False) + "\n")
    with val_path.open("w", encoding="utf-8") as f:
        for ex in val:
            f.write(json.dumps(ex, ensure_ascii=False) + "\n")
    print(f"multiturn saved: total={len(examples)}, train={len(train)}, val={len(val)}")


if __name__ == "__main__":
    examples = load_examples()
    multi = build_multiturn(examples, turns=(2, 4))
    save_dataset(multi, MULTI_TRAIN, MULTI_VAL)
