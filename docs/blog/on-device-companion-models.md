# LinkedIn post — "I wanted a dumb model for an ESP32"

Status: draft, ready to post. Repo: https://github.com/BayazidHabibSiddikee/CupCake

---

I wanted to build a dumb little AI to run on an ESP32-S3.

Turns out you can't fit a 127 MB model into 8 MB of PSRAM. So the "dumb model" lives on my
phone, and the ESP32-S3 is just the body — sensors, BLE, vibes. Close enough.

So I picked two tiny open models and fine-tuned both on my own Banglish chat data:

→ Qwen2.5-0.5B-Instruct (494M)
→ rnnandi/bangla-smollm-135m (210M)

The honest result:

→ one of them got measurably *better*
→ the other got measurably *worse*

Both are public, because that's what "publishing research" means now:
• liltii: https://huggingface.co/bayazidhs04/liltii-v0.2-qwen2.5-0.5b
• smollm: https://huggingface.co/bayazidhs04/bangla-smollm-135m-cupcake

The goal was a companion that replies in short Banglish with an `[emotion]` tag and an
`*action*`, instead of a corporate assistant writing 200-character essays.

─── How I measured it ───
llama.cpp b11669, 4 CPU threads, all Q4_0, in-domain Banglish corpus:

• liltii (my Qwen2.5-0.5B fine-tune): BPB 1.35, 20.9 tok/s
• Qwen2.5-0.5B (the base it came from): BPB 1.56, 17.1 tok/s
• Llama-3.2-1B-Instruct: BPB 1.52, 9.5 tok/s
• SmolLM2-135M-Instruct: BPB 1.64, 33.9 tok/s
• bangla-smollm base: BPB 3.04
• bangla-smollm (my fine-tune): BPB 3.76  ← I made this one worse. It rambles. Published anyway.

(BPB = bits per byte. Perplexity isn't comparable across different tokenizers, BPB is. Only
liltii actually followed the persona: 52% emotion tags, 56% action beats, 56-char replies.)

─── Now the fun part: quantization ───

A model is just numbers. fp16 gives every weight 65,536 possible values. 4-bit gives it 16.
2-bit gives it 4.

Same as dropping a photo's color depth: fp16 is a 16-million-color photo, 4-bit is a 16-color
GIF, 2-bit is a Game Boy screen. Subtle differences round into the same value, and the detail blur.

Except it's smarter than that. Q4_0 groups 32 weights, stores ONE fp16 scale per group, and
keeps each weight as a 4-bit integer. At runtime it rebuilds:

    w ≈ scale × int

So you keep the original dynamic range at ~4.5 bits per weight.

Why 2-bit destroys small models: a 70B model has billions of redundant paths, so rounding noise
averages out. A 210M model has none — every weight is load-bearing. Crush it to 4 levels and
grammar, syntax and Bangla characters fall apart.

The counterintuitive part: my Q4 files are SMALLER than my Q2 files.

• SmolLM: Q4_0 = 127 MB vs Q2_K = 174 MB
• liltii: Q4_0 = 271 MB vs Q2_K = 323 MB

Why? These models have huge vocabularies (180k and 152k tokens) and the embedding matrix is most
of the parameters. k-quants (Q2_K) need tensor dimensions divisible by 256 — these aren't — so
llama.cpp silently falls back to higher precision on almost every tensor. Q4_0 works on every
tensor, and I also quantized the token embeddings and the output head, which most people don't.

Smaller file, less memory bandwidth, faster tokens, better output. Q4 wins on every axis. I
deleted the Q2 builds.

Anyone can run llama-quantize, though. What's actually mine: the fine-tuning (bases by Qwen and
rnnandi — full credit), the dataset, the export + embedding-quantization recipe, and the app
around it.

─── Repo (ESP32 firmware + Android app + training) ───
https://github.com/BayazidHabibSiddikee/CupCake

Next: distill the 494M into the 210M so I get liltii's personality at 2× the speed.
Or just stop. TBD.

#LLM #Quantization #llama.cpp #EdgeAI #ESP32 #Bangla #OnDeviceAI #OpenSource
