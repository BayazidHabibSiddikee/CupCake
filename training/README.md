# LilTii Emotion-Tagged LLM — Local Training Pipeline

Fine-tune small Bengali-English LLMs to emit LilTii-style few-shot emotion tags
using QLoRA. Two target models:
- **LilTii-v0.2** — `Qwen/Qwen2.5-0.6B-Instruct`
- **Bangla SmolLM 135m** — `rnnandi/bangla-smollm-135m`

Outputs are mobile-ready GGUF models quantized to Q2_K.

## Pipeline Overview

```
  dataset/*.jsonl                 training/train_lora.py --config ...
       |                                    |
       v                                    v
 single-turn + multi-turn       →  LoRA adapter (training/outputs/<model>-lora/)
                                                 |
                                                 v
                                         training/merge_lora.py
                                                 |
                                                 v
                                merged HF model (training/outputs/<model>-merged/)
                                                 |
                                                 v
                                     training/convert_to_gguf.py
                                                 |
                                                 v
                               <model>-v0.2-q2_k.gguf (training/outputs/)
                                                 |
                                                 v
                           app/src/main/assets/models/<model>-v0.2-q2_k.gguf
```

## Prerequisites

- **GPU**: NVIDIA GPU with **8 GB+ VRAM** (RTX 3060 or better recommended).
  QLoRA on a 0.6B or 135M model fits comfortably in 6-8 GB.
- **OS**: Linux (Ubuntu 20.04+) or macOS with CUDA-capable GPU.
- **Python**: 3.10 or 3.11.
- **CUDA**: 12.1+ (check with `nvidia-smi`).
- **llama.cpp**: cloned at `app/src/main/cpp/third_party/llama.cpp/` with the
  `quantize` binary built (`cmake -B build && cmake --build build -j`).

## 1. Install Dependencies

```bash
pip install -r training/requirements.txt
```

If you hit `bitsandbytes` install issues on Linux, try:

```bash
pip install bitsandbytes --no-build-isolation
```

## 2. Prepare Dataset

The dataset can be generated or expanded with the provided scripts:

```bash
# Base dataset: 152 examples
python training/build_dataset.py

# Style-preserving augmentation: ~800 examples
python training/augment_dataset.py

# Banglish-heavy augmentation: ~1200 examples
python training/augment_banglish.py

# GenZ / internet-culture Banglish: ~2000 examples
python training/augment_genz.py

# Multi-turn conversations derived from current dataset
python training/build_multiturn_dataset.py
```

Final dataset files in `training/dataset/`:
- `liltii_fewshot.jsonl` — 2000 Bangla-English examples with emotion tags
- `train.jsonl` — 1600 examples for training
- `val.jsonl` — 400 examples for validation
- `multiturn_train.jsonl` — 1600 multi-turn conversations
- `multiturn_val.jsonl` — 400 multi-turn conversations

Each example follows the format:

```json
{"messages": [
  {"role": "user", "content": "bhai, why so serious?"},
  {"role": "assistant", "content": "[confused] *raises eyebrow* tumi ki serious? hashao!"}
]}
```

Bot responses follow the pattern: `[emotion] *action* mixed Bangla-English text`

## 3. Train LoRA Adapter

Two ready-made configs are provided:

```bash
# Train LilTii-v0.2 on Qwen2.5-0.6B-Instruct
python training/train_lora.py --config training/configs/liltii_v02.yaml

# Train Bangla SmolLM 135m
python training/train_lora.py --config training/configs/bangla_smollm_135m.yaml
```

Or override manually:

```bash
python training/train_lora.py \
  --base_model Qwen/Qwen2.5-0.6B-Instruct \
  --data_path training/dataset/train.jsonl \
  --val_path training/dataset/val.jsonl \
  --multiturn_train training/dataset/multiturn_train.jsonl \
  --multiturn_val training/dataset/multiturn_val.jsonl \
  --output_dir training/outputs/liltii-v0.2-lora \
  --max_seq_length 2048 \
  --epochs 3 \
  --batch_size 4 \
  --gradient_accumulation_steps 4 \
  --learning_rate 2e-4
```

**Expected output:**
- LoRA adapter saved to `training/outputs/<model>-lora/`
- Training logs every 5 steps
- Validation loss at the end of each epoch

**Hardware notes:**
- Effective batch size = `batch_size (4) × gradient_accumulation (4) = 16`
- With 4-bit QLoRA + gradient checkpointing, expect ~5-7 GB VRAM usage on a 0.6B model
- On RTX 3060 (12 GB): training takes ~30-60 minutes for 3 epochs

**LoRA config:**
- `r=8`, `lora_alpha=16`, target modules: `q_proj`, `k_proj`, `v_proj`, `o_proj`
- This keeps the adapter lightweight (~5-10 MB)

## 4. Merge LoRA Adapter into Base Model

```bash
python training/merge_lora.py --base_model Qwen/Qwen2.5-0.6B-Instruct --lora_dir training/outputs/liltii-v0.2-lora --output_dir training/outputs/liltii-merged
```

This merges the LoRA weights back into the base model and saves the full
merged model to `training/outputs/<model>-merged/`.

**Tip:** Run this on CPU if you run out of VRAM.

## 5. Convert to GGUF

```bash
python training/convert_to_gguf.py --merged_dir training/outputs/liltii-merged --output_path training/outputs/liltii-v0.2-q2_k.gguf
```

This:
1. Calls llama.cpp's `convert_hf_to_gguf.py` to produce an FP16 GGUF
2. Runs `quantize` with `Q2_K` for mobile deployment

**Output:** `training/outputs/<model>-v0.2-q2_k.gguf` (~40-60 MB)

**Troubleshooting:**
- If `quantize` binary is not found, build llama.cpp first:
  ```bash
  cd app/src/main/cpp/third_party/llama.cpp
  cmake -B build && cmake --build build -j$(nproc)
  ```
- If conversion fails, ensure you're using the matching llama.cpp version for
  the target model family.

## 6. Deploy to Android App

```bash
# Copy the GGUF to Android assets
cp training/outputs/liltii-v0.2-q2_k.gguf app/src/main/assets/models/liltii-v0.2-q2_k.gguf
```

## 7. Update ModelDownloadManager

In `app/src/main/java/.../ModelDownloadManager.kt`, add entries for both models:

```kotlin
LILTII_V0_2_Q2_K("liltii-v0.2-q2_k", "liltii-v0.2-q2_k.gguf", ModelType.LOCAL),
BANGLA_SMOLLM_135M_Q2_K("bangla-smollm-135m-v0.2-q2_k", "bangla-smollm-135m-v2_q2_k.gguf", ModelType.LOCAL),
```

## Architecture Note

These two models have **different architectures** and **cannot be merged together**:

- `LilTii-v0.2` uses `Qwen/Qwen2.5-0.6B-Instruct` → **Qwen2** architecture
- `Bangla SmolLM 135m` uses `rnnandi/bangla-smollm-135m` → **LLaMA** architecture

You must train, merge, and convert each model **independently**. The LoRA adapters,
merged models, and GGUF outputs are all separate. You can deploy both GGUF files
to the Android app and switch between them at runtime.

## Multi-Turn / History Support

Yes — both models can learn short-term history if trained with multi-turn data.
This repo now includes:

- `training/dataset/multiturn_train.jsonl`
- `training/dataset/multiturn_val.jsonl`
- `training/build_multiturn_dataset.py`
- `--multiturn_train` and `--multiturn_val` flags in `train_lora.py`

Recommended settings for history-aware behavior:
- `max_seq_length: 2048`
- `max_turns: 2-4` in multi-turn builder
- Keep replies reasonably short during training so multiple turns fit

## Dataset Format Reference

### Emotion Tags

Supported emotions in training data:
- `[sleepy]` — tired, drowsy, needs rest
- `[confused]` — puzzled, uncertain, seeking clarity
- `[surprised]` — shocked, excited by unexpected news
- `[happy]` — joyful, celebratory, warm
- `[angry]` — frustrated, annoyed, upset
- `[sad]` — empathetic, comforting, melancholic
- `[thinking]` — contemplative, analytical, thoughtful

### Conversation Topics Covered

| Topic | Example Count |
|-------|--------------|
| Greetings | 3 |
| Food & cooking | 4 |
| Sleep & tiredness | 4 |
| Questions about Bangladesh | 3 |
| Emotions & feelings | 6 |
| Games & fun | 3 |
| Boredom | 2 |
| Love & affection | 4 |
| Annoyance & frustration | 3 |
| Nostalgia & sadness | 3 |
| General chat | 15 |
| GenZ/internet slang | expanded via augmentation |

## Project Structure

```
training/
├── configs/
│   ├── liltii_v02.yaml
│   └── bangla_smollm_135m.yaml
├── dataset/
│   ├── liltii_fewshot.jsonl   # 2000 examples
│   ├── train.jsonl            # 1600 examples
│   ├── val.jsonl              # 400 examples
│   ├── multiturn_train.jsonl  # 1600 multi-turn conversations
│   └── multiturn_val.jsonl    # 400 multi-turn conversations
├── train_lora.py              # QLoRA fine-tuning script
├── merge_lora.py              # LoRA merge script
├── convert_to_gguf.py         # GGUF conversion + Q2_K quantization
├── build_dataset.py           # Base dataset generator
├── augment_dataset.py         # Style-preserving augmentation
├── augment_banglish.py        # Banglish-heavy augmentation
├── augment_genz.py            # GenZ/internet-culture augmentation
├── build_multiturn_dataset.py # Multi-turn conversation builder
├── requirements.txt           # Python dependencies
└── README.md                  # This file
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `CUDA out of memory` | Reduce `--batch_size` to 2, or use `--device cpu` for merging |
| `bitsandbytes import error` | Install matching CUDA version: `pip install bitsandbytes --no-build-isolation` |
| `convert_hf_to_gguf.py not found` | Ensure llama.cpp submodule is checked out at the expected path |
| `quantize binary not found` | Build llama.cpp: `cd app/src/main/cpp/third_party/llama.cpp && cmake -B build && cmake --build build -j` |
| Model too large on device | Use Q2_K (current) or Q3_K_S for smaller size at slight quality cost |
| Emotion tags not learned | Increase training data, or increase `--lora_r` to 16 |
| History feels broken | Increase `--max_seq_length` to 2048 and use multi-turn dataset |

## Expected Model Behavior

After fine-tuning, the model should respond to user messages with:
1. An emotion tag in brackets: `[happy]`, `[sad]`, etc.
2. An italicized action: `*grins*`, `*sighs*`, etc.
3. Mixed Bangla-English text that matches the emotional tone

Example:
```
User: ami ajke onek bukhar ache
Bot: [sad] *concerned* oh no... ami tomar sathe ache. tomar temperature ache ki? drink water and rest, okay?
```

## License

This training pipeline is part of the CupCake project. The base models
`Qwen/Qwen2.5-0.6B-Instruct` and `rnnandi/bangla-smollm-135m` are licensed
under their own terms.
