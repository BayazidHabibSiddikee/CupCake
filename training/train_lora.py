#!/usr/bin/env python3
"""
LoRA fine-tuning script for LilTii-style Bengali-English emotion-tagged LLM.

Trains a QLoRA adapter on top of Qwen2.5-0.5B-Instruct using a small
few-shot dataset of Bangla-English conversations with emotion tags.

Usage:
    python training/train_lora.py
    python training/train_lora.py --base_model Qwen/Qwen2.5-0.5B-Instruct --data_path training/dataset/train.jsonl --output_dir training/outputs/liltii-lora --epochs 3
"""

import argparse
import json
import os
import sys

import torch
from datasets import load_dataset
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    BitsAndBytesConfig,
    DataCollatorForSeq2Seq,
    Trainer,
    TrainingArguments,
)


def parse_args():
    parser = argparse.ArgumentParser(
        description="Fine-tune a small LLM with LoRA for LilTii-style emotion-tagged responses"
    )
    parser.add_argument(
        "--base_model",
        type=str,
        default="Qwen/Qwen2.5-0.5B-Instruct",
        help="HuggingFace model ID to fine-tune",
    )
    parser.add_argument(
        "--data_path",
        type=str,
        default="training/dataset/train.jsonl",
        help="Path to the training JSONL dataset",
    )
    parser.add_argument(
        "--val_path",
        type=str,
        default="training/dataset/val.jsonl",
        help="Path to the validation JSONL dataset",
    )
    parser.add_argument(
        "--output_dir",
        type=str,
        default="training/outputs/liltii-lora",
        help="Directory to save the LoRA adapter",
    )
    parser.add_argument(
        "--max_seq_length",
        type=int,
        default=512,
        help="Maximum sequence length for tokenization",
    )
    parser.add_argument(
        "--epochs",
        type=int,
        default=3,
        help="Number of training epochs",
    )
    parser.add_argument(
        "--batch_size",
        type=int,
        default=4,
        help="Per-device batch size",
    )
    parser.add_argument(
        "--gradient_accumulation_steps",
        type=int,
        default=4,
        help="Gradient accumulation steps (effective batch = batch_size * this)",
    )
    parser.add_argument(
        "--learning_rate",
        type=float,
        default=2e-4,
        help="Learning rate for LoRA training",
    )
    parser.add_argument(
        "--lora_r",
        type=int,
        default=8,
        help="LoRA rank",
    )
    parser.add_argument(
        "--lora_alpha",
        type=int,
        default=16,
        help="LoRA alpha scaling factor",
    )
    return parser.parse_args()


def load_jsonl_dataset(path):
    """Load a JSONL file where each line is a dict with a 'messages' key."""
    return load_dataset("json", data_files=path, split="train")


def tokenize_function(tokenizer, max_seq_length):
    """Build a tokenizer closure that formats chat turns into a single prompt."""

    def _tokenize(example):
        # Build a chat-style prompt: each turn labeled by role
        messages = example["messages"]

        # Format using the model's chat template if available
        if tokenizer.chat_template:
            prompt = tokenizer.apply_chat_template(
                messages, tokenize=False, add_generation_prompt=False
            )
        else:
            # Fallback: simple role-based formatting
            parts = []
            for msg in messages:
                role = msg["role"]
                content = msg["content"]
                parts.append(f"<{role}>\n{content}\n")
            prompt = "\n".join(parts)

        tokenized = tokenizer(
            prompt,
            truncation=True,
            max_length=max_seq_length,
            padding=False,
        )
        # For causal LM training, labels = input_ids
        tokenized["labels"] = tokenized["input_ids"][:]
        return tokenized

    return _tokenize


def main():
    args = parse_args()

    print(f"=== LilTii LoRA Training ===")
    print(f"Base model  : {args.base_model}")
    print(f"Data path   : {args.data_path}")
    print(f"Val path    : {args.val_path}")
    print(f"Output dir  : {args.output_dir}")
    print(f"Max seq len : {args.max_seq_length}")
    print(f"Epochs      : {args.epochs}")
    print(f"Batch size  : {args.batch_size} x {args.gradient_accumulation_steps} grad accum")
    print(f"LR          : {args.learning_rate}")
    print(f"LoRA r/alpha: {args.lora_r} / {args.lora_alpha}")
    print()

    # ------------------------------------------------------------------ #
    # 1. Quantization config (4-bit NF4 for QLoRA)                        #
    # ------------------------------------------------------------------ #
    bnb_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_use_double_quant=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=torch.bfloat16
        if torch.cuda.is_bf16_supported()
        else torch.float16,
    )

    # ------------------------------------------------------------------ #
    # 2. Load model & tokenizer                                            #
    # ------------------------------------------------------------------ #
    print("Loading tokenizer...")
    tokenizer = AutoTokenizer.from_pretrained(args.base_model, trust_remote_code=True)

    # Qwen models use eos_token_id but may not have a dedicated pad token;
    # set it to eos for safe padding in the trainer.
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token
        tokenizer.pad_token_id = tokenizer.eos_token_id

    print("Loading base model (4-bit quantized)...")
    model = AutoModelForCausalLM.from_pretrained(
        args.base_model,
        quantization_config=bnb_config,
        device_map="auto",
        trust_remote_code=True,
    )

    # Enable gradient checkpointing to save VRAM
    model.gradient_checkpointing_enable()
    model = prepare_model_for_kbit_training(model)

    # ------------------------------------------------------------------ #
    # 3. LoRA configuration                                                #
    # ------------------------------------------------------------------ #
    lora_config = LoraConfig(
        r=args.lora_r,
        lora_alpha=args.lora_alpha,
        lora_dropout=0.05,
        bias="none",
        task_type="CAUSAL_LM",
        target_modules=["q_proj", "v_proj", "k_proj", "o_proj"],
    )

    model = get_peft_model(model, lora_config)
    model.print_trainable_parameters()

    # ------------------------------------------------------------------ #
    # 4. Load & tokenize datasets                                          #
    # ------------------------------------------------------------------ #
    print("Loading datasets...")
    train_dataset = load_jsonl_dataset(args.data_path)
    val_dataset = load_jsonl_dataset(args.val_path)

    print(f"  Train examples : {len(train_dataset)}")
    print(f"  Val examples   : {len(val_dataset)}")

    _tokenize = tokenize_function(tokenizer, args.max_seq_length)
    train_dataset = train_dataset.map(
        _tokenize, remove_columns=train_dataset.column_names, batched=False
    )
    val_dataset = val_dataset.map(
        _tokenize, remove_columns=val_dataset.column_names, batched=False
    )

    # ------------------------------------------------------------------ #
    # 5. Training arguments                                                #
    # ------------------------------------------------------------------ #
    training_args = TrainingArguments(
        output_dir=args.output_dir,
        num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch_size,
        per_device_eval_batch_size=args.batch_size,
        gradient_accumulation_steps=args.gradient_accumulation_steps,
        learning_rate=args.learning_rate,
        lr_scheduler_type="cosine",
        warmup_ratio=0.03,
        logging_steps=5,
        evaluation_strategy="epoch",
        save_strategy="epoch",
        save_total_limit=2,
        load_best_model_at_end=False,
        report_to="none",  # Set to "wandb" etc. if desired
        fp16=not torch.cuda.is_bf16_supported(),
        bf16=torch.cuda.is_bf16_supported(),
        gradient_checkpointing=True,
        optim="paged_adamw_8bit",
        seed=42,
    )

    data_collator = DataCollatorForSeq2Seq(
        tokenizer,
        model=model,
        padding=True,
        pad_to_multiple_of=8,
    )

    # ------------------------------------------------------------------ #
    # 6. Train                                                             #
    # ------------------------------------------------------------------ #
    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_dataset,
        eval_dataset=val_dataset,
        data_collator=data_collator,
        tokenizer=tokenizer,
    )

    print("Starting training...")
    trainer.train()

    # ------------------------------------------------------------------ #
    # 7. Save adapter                                                     #
    # ------------------------------------------------------------------ #
    os.makedirs(args.output_dir, exist_ok=True)
    model.save_pretrained(args.output_dir)
    tokenizer.save_pretrained(args.output_dir)

    print(f"\nLoRA adapter saved to: {args.output_dir}")
    print("Done!")


if __name__ == "__main__":
    main()
