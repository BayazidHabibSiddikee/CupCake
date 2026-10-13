# LilTii Emotion-Tagged LLM — Local Training Pipeline

Fine-tune a small Bengali-English LLM (Qwen2.5-0.5B-Instruct) to emit
LilTii-style few-shot emotion tags using QLoRA. The output is a mobile-ready
GGUF model quantized to Q2_K.

## Pipeline Overview

```
  dataset/liltii_fewshot.jsonl          training/train_lora.py
              |                              |
              v                              v
        train.jsonl  val.jsonl      →  LoRA adapter (training/outputs/liltii-lora/)
                                                |
                                                v
                                        training/merge_lora.py
                                                |
                                                v
                                       merged HF model (training/outputs/liltii-merged/)
                                                |
                                                v
                                     training/convert_to_gguf.py
                                                |
                                                v
                               liltii-v0.2-q2_k.gguf (training/outputs/)
                                                |
                                                v
                           app/src/main/assets/models/liltii-v0.2-q2_k.gguf
```

## Prerequisites

- **GPU**: NVIDIA GPU with **8 GB+ VRAM** (RTX 3060 or better recommended).
  QLoRA on a 0.5 B model fits comfortably in 6-8 GB.
- **OS**: Linux (Ubuntu 20.04+) or macOS with CUDA-capable GPU.
- **Python**: 3.10 or 3.11.
- **CUDA**: 12.1+ (check with `nvidia-smi`).
- **llama.cpp**: cloned at `app/src/main/cpp/third_party/llama.cpp/` with the
  `quantize` binary built (`cmake -B build && cmake --build build -j`).

## Step-by-Step Instructions

### 1. Install Dependencies

```bash
pip install -r training/requirements.txt
```

If you hit `bitsandbytes` install issues on Linux, try:

```bash
pip install bitsandbytes --no-build-isolation
```

### 2. Prepare Dataset

The dataset is already prepared in `training/dataset/`:
- `liltii_fewshot.jsonl` — 800 augmented Bangla-English examples with emotion tags
- `train.jsonl` — 640 examples for training
- `val.jsonl` — 160 examples for validation

Each example follows the format:

```json
{"messages": [
  {"role": "user", "content": "oi, kemon acho?"},
  {"role": "assistant", "content": "[happy] *brightly* hey! I'm doing great..."}
]}
```

Bot responses follow the pattern: `[emotion] *action* mixed Bangla-English text`

### 3. Train LoRA Adapter

```bash
python training/train_lora.py
```

**Expected output:**
- LoRA adapter saved to `training/outputs/liltii-lora/`
- Training logs every 5 steps
- Validation loss at the end of each epoch

**Optional arguments:**

```bash
python training/train_lora.py \
  --base_model Qwen/Qwen2.5-0.5B-Instruct \
  --data_path training/dataset/train.jsonl \
  --val_path training/dataset/val.jsonl \
  --output_dir training/outputs/liltii-lora \
  --max_seq_length 512 \
  --epochs 3 \
  --batch_size 4 \
  --gradient_accumulation_steps 4 \
  --learning_rate 2e-4
```

**Hardware notes:**
- Effective batch size = `batch_size (4) × gradient_accumulation (4) = 16`
- With 4-bit QLoRA + gradient checkpointing, expect ~5-7 GB VRAM usage on a 0.5B model
- On RTX 3060 (12 GB): training takes ~30-60 minutes for 3 epochs

**LoRA config:**
- `r=8`, `lora_alpha=16`, target modules: `q_proj`, `v_proj`, `k_proj`, `o_proj`
- This keeps the adapter lightweight (~5-10 MB)

### 4. Merge LoRA Adapter into Base Model

```bash
python training/merge_lora.py
```

This merges the LoRA weights back into the base model and saves the full
merged model to `training/outputs/liltii-merged/`.

**Tip:** Run this on CPU if you run out of VRAM (add `--device cpu`).

### 5. Convert to GGUF

```bash
python training/convert_to_gguf.py
```

This:
1. Calls llama.cpp's `convert_hf_to_gguf.py` to produce an FP16 GGUF
2. Runs `quantize` with `Q2_K` for mobile deployment

**Output:** `training/outputs/liltii-v0.2-q2_k.gguf` (~40-60 MB)

**Troubleshooting:**
- If `quantize` binary is not found, build llama.cpp first:
  ```bash
  cd app/src/main/cpp/third_party/llama.cpp
  cmake -B build && cmake --build build -j$(nproc)
  ```
- If conversion fails, ensure you're using the matching llama.cpp version for
  Qwen2.5 models (the one bundled in the project is correct).

### 6. Deploy to Android App

```bash
# Copy the GGUF to Android assets
cp training/outputs/liltii-v0.2-q2_k.gguf app/src/main/assets/models/liltii-v0.2-q2_k.gguf
```

### 7. Update ModelDownloadManager

In `app/src/main/java/.../ModelDownloadManager.kt` (or wherever your model
registry lives), add an entry for the new model:

```kotlin
LILTII_V0_2_Q2_K("liltii-v0.2-q2_k", "liltii-v0.2-q2_k.gguf", ModelType.LOCAL)
```

Or if the model is bundled as an asset only (no download), ensure the
asset is referenced in your model loading logic so the app picks it up
from `assets/models/`.

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

## Project Structure

```
training/
├── dataset/
│   ├── liltii_fewshot.jsonl   # 50 raw examples
│   ├── train.jsonl            # 40 training examples
│   └── val.jsonl              # 10 validation examples
├── train_lora.py              # QLoRA fine-tuning script
├── merge_lora.py              # LoRA merge script
├── convert_to_gguf.py         # GGUF conversion + Q2_K quantization
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

This training pipeline is part of the CupCake project. The base model
Qwen/Qwen2.5-0.5B-Instruct is licensed under its own terms (Qwen License).
