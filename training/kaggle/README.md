# LilTii Banglish Emotion LLM — Kaggle Training Package

Fine-tune **Qwen/Qwen2.5-0.6B-Instruct** on Bengali-English (Banglish) emotion-tagged
conversations using QLoRA. Trained models emit responses in the LilTii format:

```
[emotion] *action* mixed Bangla-English text
```

## Files in This Package

| File | Purpose |
|------|---------|
| `train_liltii.ipynb` | Main Kaggle notebook — run this on Kaggle |
| `kaggle_metadata.json` | Kaggle kernel metadata (upload alongside the notebook) |
| `download_models.sh` | Script to download trained outputs from Kaggle |
| `../requirements.txt` | Python dependencies (updated with Kaggle tools) |

## Dataset Sources

The notebook combines three data sources:

| Source | Type | Description |
|--------|------|-------------|
| **CupCake LilTii** (embedded) | Local project data | ~20 representative Banglish examples with emotion tags; expandable to 2000 via local JSONL files |
| **ccdv/chat_instruct** | HuggingFace dataset | General multi-turn chat instruct data, filtered for Bangla/English content |
| **AarohiSingla/processed_bangla_conversations** | HuggingFace dataset | Processed Bangla conversation pairs |

All sources are normalized into the LilTii format: `{"messages": [{"role": "user", ...}, {"role": "assistant", ...}]}`

## How to Upload to Kaggle

### Step 1: Create a Kaggle Kernel

1. Go to [kaggle.com/kernels/new](https://www.kaggle.com/kernels/new)
2. Choose **"Notebook"** as the kernel type
3. Set metadata:
   - **Title:** `LilTii Banglish Emotion LLM Training`
   - **Language:** Python
   - **GPU:** Enabled (required for QLoRA)
   - **Internet:** Enabled (required to download HuggingFace models and datasets)
   - **Privacy:** Public (or Private if you prefer)

### Step 2: Upload Files

1. In the Kaggle kernel editor, click **"Add Data"** → **"Upload"**
2. Upload `train_liltii.ipynb`
3. Also upload `kaggle_metadata.json` (optional — Kaggle may use inline metadata)

Alternatively, use the Kaggle CLI:

```bash
# Initialize a Kaggle kernel metadata file
kaggle kernels init -p training/kaggle/

# Push the notebook as a Kaggle kernel
kaggle kernels push -p training/kaggle/
```

### Step 3: Verify GPU Settings

In the Kaggle notebook settings (right sidebar):
- **Accelerator**: GPU (T4 x2 recommended, or at least T4 x1)
- **Internet**: Connected

## How to Run the Notebook

Once uploaded, simply click **"Run"** in the Kaggle UI. The notebook will:

1. Install all dependencies (5-10 minutes)
2. Download base model Qwen/Qwen2.5-0.6B-Instruct (~1.2 GB)
3. Load and filter HuggingFace Bangla datasets
4. Preprocess data into LilTii format
5. Train QLoRA adapter (~30-90 minutes depending on GPU)
6. Merge LoRA into base model (~5-10 minutes)
7. Save outputs to `/kaggle/working/output/`

Monitor progress in the Kaggle notebook logs. Training logs appear every 5 steps.

## How to Download Trained Models

### Option 1: Kaggle UI

1. After training completes, go to the kernel's **"Output"** tab
2. Click **"Download"** to get all output files as a zip

### Option 2: Kaggle CLI

Use the provided `download_models.sh` script:

```bash
bash training/kaggle/download_models.sh bayazidhabibsiddikee liltii-banglish-emotion-llm
```

Or manually:

```bash
kaggle kernels output bayazidhabibsiddikee/liltii-banglish-emotion-llm -p training/outputs/kaggle_models/
```

### Option 3: Direct from Notebook

All files are saved to `/kaggle/working/output/`. Kaggle automatically persists these
as kernel output. You can also add a final cell to zip them:

```python
import shutil
shutil.make_archive("/kaggle/working/liltii_output", "zip", "/kaggle/working/output/")
```

## Expected Training Time and GPU Requirements

| GPU | VRAM | Expected Time (3 epochs) | Notes |
|-----|------|--------------------------|-------|
| T4 x2 (Kaggle default) | ~28 GB | ~20-40 minutes | Comfortable |
| T4 x1 | ~14 GB | ~30-60 minutes | Should fit, tight margin |
| V100 x1 | ~16 GB | ~20-40 minutes | Good |
| CPU only | N/A | **Very slow / fail** | Not recommended |

**Disk space:** ~3 GB (base model + adapter + merged model)

### Hyperparameters

| Parameter | Value |
|-----------|-------|
| Base model | Qwen/Qwen2.5-0.6B-Instruct |
| LoRA rank | 8 |
| LoRA alpha | 16 |
| Target modules | q_proj, k_proj, v_proj, o_proj |
| Batch size | 4 (effective 16 with grad accum) |
| Learning rate | 2e-4 (cosine schedule) |
| Epochs | 3 |
| Max sequence length | 2048 |
| Quantization | 4-bit NF4 (QLoRA) |

## GGUF Conversion (Local, Not on Kaggle)

Kaggle does not have `llama.cpp` tools. After downloading the merged model:

```bash
# 1. Clone and build llama.cpp
git clone https://github.com/ggerganov/llama.cpp.git
cd llama.cpp && cmake -B build && cmake --build build -j$(nproc)

# 2. Convert merged HF model to FP16 GGUF
python llama.cpp/convert_hf_to_gguf.py training/outputs/liltii-merged/ \
  --outfile training/outputs/liltii-v0.2-fp16.gguf \
  --outtype f16

# 3. Quantize to Q2_K for mobile deployment (~40-60 MB)
./llama.cpp/build/bin/quantize training/outputs/liltii-v0.2-fp16.gguf \
  training/outputs/liltii-v0.2-q2_k.gguf Q2_K
```

Then copy to the Android app:

```bash
cp training/outputs/liltii-v0.2-q2_k.gguf app/src/main/assets/models/
```

## Output Files

After training completes, the `/kaggle/working/output/` directory contains:

| File | Size | Description |
|------|------|-------------|
| `adapter_config.json` | ~1 KB | LoRA configuration |
| `adapter_model.safetensors` | ~10-20 MB | Trained LoRA weights |
| `config.json` | ~1 KB | Model config |
| `tokenizer.json` | ~1 MB | Tokenizer |
| `tokenizer_config.json` | ~1 KB | Tokenizer config |
| `model.safetensors` | ~600 MB | **Merged model** (this is the key file) |

## Troubleshooting

| Issue | Solution |
|-------|----------|
| CUDA out of memory | Reduce `per_device_train_batch_size` to 2, or use Kaggle T4 x2 |
| `bitsandbytes` install error | Usually auto-resolved on Kaggle; if not, restart runtime |
| HuggingFace dataset download fails | Check internet connectivity in Kaggle settings |
| Training very slow | Ensure GPU accelerator is enabled (not CPU) |
| Model not learning emotion tags | Increase training data or LoRA rank (`r=16`) |
| `ccdv/chat_instruct` not found | The notebook skips this gracefully and uses fallback data |

## Project Structure

```
training/
├── kaggle/
│   ├── kaggle_metadata.json      # Kaggle kernel metadata
│   ├── train_liltii.ipynb        # Main training notebook
│   ├── download_models.sh        # Download script
│   └── README.md                 # This file
├── configs/
│   └── liltii_v02.yaml           # Training config (reference)
├── dataset/
│   ├── liltii_fewshot.jsonl      # 2000 CupCake examples
│   ├── train.jsonl               # 1600 training examples
│   ├── val.jsonl                 # 400 validation examples
│   ├── multiturn_train.jsonl     # 1600 multi-turn conversations
│   └── multiturn_val.jsonl       # 400 multi-turn conversations
├── outputs/
│   └── kaggle_models/            # Downloaded Kaggle outputs
├── train_lora.py                 # Local training script (reference)
├── merge_lora.py                 # LoRA merge script
├── convert_to_gguf.py            # GGUF conversion script
├── requirements.txt              # Python dependencies
└── README.md                     # Full local training docs
```

## License

MIT. The base model `Qwen/Qwen2.5-0.6B-Instruct` is licensed under its own terms.
Open dataset sources retain their original licenses.
