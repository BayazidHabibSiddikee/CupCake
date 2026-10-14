#!/usr/bin/env python3
"""
LoRA fine-tuning script for LilTii-style Bengali-English emotion-tagged LLM.

Trains a QLoRA adapter on top of a base model using single-turn and multi-turn
Bangla-English conversation data with emotion tags.

Usage:
    python training/train_lora.py --config training/configs/liltii_v02.yaml
    python training/train_lora.py --config training/configs/bangla_smollm_135m.yaml
    python training/train_lora.py --base_model Qwen/Qwen2.5-0.6B-Instruct --data_path training/dataset/train.jsonl --output_dir training/outputs/liltii-v0.2-lora --epochs 3
"""

import argparse
import json
import os
import sys

import torch
from datasets import load_dataset, concatenate_datasets
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    BitsAndBytesConfig,
    DataCollatorForSeq2Seq,
    Trainer,
    TrainingArguments,
)

try:
    import yaml

    HAS_YAML = True
except ImportError:
    HAS_YAML = False


def parse_args():
    parser = argparse.ArgumentParser(
        description="Fine-tune a small LLM with LoRA for LilTii-style emotion-tagged responses"
    )
    parser.add_argument("--config", type=str, default=None, help="YAML config file path")
    parser.add_argument(
        "--base_model",
        type=str,
        default="Qwen/Qwen2.5-0.6B-Instruct",
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
        "--multiturn_train",
        type=str,
        default="training/dataset/multiturn_train.jsonl",
        help="Path to multi-turn training JSONL dataset",
    )
    parser.add_argument(
        "--multiturn_val",
        type=str,
        default="training/dataset/multiturn_val.jsonl",
        help="Path to multi-turn validation JSONL dataset",
    )
    parser.add_argument(
        "--output_dir",
        type=str,
        default="training/outputs/liltii-v0.2-lora",
        help="Directory to save the LoRA adapter",
    )
    parser.add_argument(
        "--max_seq_length",
        type=int,
        default=2048,
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
    parser.add_argument(
        "--lora_dropout",
        type=float,
        default=0.05,
        help="LoRA dropout",
    )
    parser.add_argument(
        "--target_modules",
        type=str,
        default="q_proj,k_proj,v_proj,o_proj",
        help="Comma-separated target modules for LoRA",
    )
    return parser.parse_args()


def load_yaml_config(path):
    if not HAS_YAML:
        raise ImportError("PyYAML is required for --config. Install with: pip install pyyaml")
    with open(path, "r") as f:
        return yaml.safe_load(f)


def apply_config_to_args(args, config):
    """Map YAML config keys to argparse namespace."""
    mapping = {
        "base_model": "base_model",
        "output_dir": "output_dir",
        "max_seq_length": "max_seq_length",
        "lora_r": "lora_r",
        "lora_alpha": "lora_alpha",
        "lora_dropout": "lora_dropout",
        "train_batch_size": "batch_size",
        "gradient_accumulation_steps": "gradient_accumulation_steps",
        "learning_rate": "learning_rate",
        "num_train_epochs": "epochs",
    }
    for yaml_key, arg_key in mapping.items():
        if yaml_key in config:
            setattr(args, arg_key, config[yaml_key])
    if "target_modules" in config:
        args.target_modules = ",".join(config["target_modules"])
    if "dataset" in config:
        ds = config["dataset"]
        if "train" in ds:
            args.data_path = ds["train"]
        if "val" in ds:
            args.val_path = ds["val"]
        if "multiturn_train" in ds:
            args.multiturn_train = ds["multiturn_train"]
        if "multiturn_val" in ds:
            args.multiturn_val = ds["multiturn_val"]
    return args


def load_jsonl_dataset(path):
    """Load a JSONL file where each line is a dict with a 'messages' key."""
    if not os.path.exists(path):
        raise FileNotFoundError(f"Dataset not found: {path}")
    return load_dataset("json", data_files=path, split="train")


def _combine_datasets(single_path, multi_path):
    """Merge single-turn and multi-turn datasets into one."""
    datasets = []
    if single_path and os.path.exists(single_path):
        datasets.append(load_jsonl_dataset(single_path))
    if multi_path and os.path.exists(multi_path):
        datasets.append(load_jsonl_dataset(multi_path))
    if not datasets:
        raise ValueError("No valid dataset paths provided")
    if len(datasets) == 1:
        return datasets[0]
    return concatenate_datasets(datasets)


def tokenize_function(tokenizer, max_seq_length):
    """Build a tokenizer closure that formats chat turns into a single prompt."""

    def _tokenize(example):
        messages = example["messages"]

        if tokenizer.chat_template:
            prompt = tokenizer.apply_chat_template(
                messages, tokenize=False, add_generation_prompt=False
            )
        else:
            parts = []
            for msg in messages:
                role = msg["role"]
                content = msg["content"]
                parts.append(f"{role}\n{content}")
            prompt = "\n".join(parts)

        tokenized = tokenizer(
            prompt,
            truncation=True,
            max_length=max_seq_length,
            padding=False,
        )
        tokenized["labels"] = tokenized["input_ids"][:]
        return tokenized

    return _tokenize


def main():
    args = parse_args()

    # Load YAML config if provided, overriding defaults
    config = {}
    if args.config:
        config = load_yaml_config(args.config)
        args = apply_config_to_args(args, config)

    print(f"=== LilTii LoRA Training ===")
    print(f"Base model   : {args.base_model}")
    print(f"Data path    : {args.data_path}")
    print(f"Val path     : {args.val_path}")
    print(f"Multi-turn   : {args.multiturn_train} / {args.multiturn_val}")
    print(f"Output dir   : {args.output_dir}")
    print(f"Max seq len  : {args.max_seq_length}")
    print(f"Epochs       : {args.epochs}")
    print(f"Batch size   : {args.batch_size} x {args.gradient_accumulation_steps} grad accum")
    print(f"LR           : {args.learning_rate}")
    print(f"LoRA r/alpha : {args.lora_r} / {args.lora_alpha}")
    print()

    # 1. Quantization config (4-bit NF4 for QLoRA)
    bnb_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_use_double_quant=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=torch.bfloat16
        if torch.cuda.is_bf16_supported()
        else torch.float16,
    )

    # 2. Load model & tokenizer
    print("Loading tokenizer...")
    tokenizer = AutoTokenizer.from_pretrained(args.base_model, trust_remote_code=True)

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

    model.gradient_checkpointing_enable()
    model = prepare_model_for_kbit_training(model)

    # 3. LoRA configuration
    target_modules = [m.strip() for m in args.target_modules.split(",")]
    lora_config = LoraConfig(
        r=args.lora_r,
        lora_alpha=args.lora_alpha,
        lora_dropout=args.lora_dropout,
        bias="none",
        task_type="CAUSAL_LM",
        target_modules=target_modules,
    )

    model = get_peft_model(model, lora_config)
    model.print_trainable_parameters()

    # 4. Load & tokenize datasets
    print("Loading datasets...")
    combined_train = _combine_datasets(args.data_path, args.multiturn_train)
    combined_val = _combine_datasets(args.val_path, args.multiturn_val)

    print(f"  Train examples : {len(combined_train)}")
    print(f"  Val examples   : {len(combined_val)}")

    _tokenize = tokenize_function(tokenizer, args.max_seq_length)
    train_dataset = combined_train.map(
        _tokenize, remove_columns=combined_train.column_names, batched=False
    )
    val_dataset = combined_val.map(
        _tokenize, remove_columns=combined_val.column_names, batched=False
    )

    # 5. Training arguments
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
        report_to="none",
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

    # 6. Train
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

    # 7. Save adapter
    os.makedirs(args.output_dir, exist_ok=True)
    model.save_pretrained(args.output_dir)
    tokenizer.save_pretrained(args.output_dir)

    print(f"\nLoRA adapter saved to: {args.output_dir}")
    print("Done!")


if __name__ == "__main__":
    main()
