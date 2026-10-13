#!/usr/bin/env python3
"""
Merge a LoRA adapter into its base model and save the full merged model.

Usage:
    python training/merge_lora.py
    python training/merge_lora.py --base_model Qwen/Qwen2.5-0.5B-Instruct --adapter_path training/outputs/liltii-lora --output_dir training/outputs/liltii-merged
"""

import argparse
import os

import torch
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoTokenizer


def parse_args():
    parser = argparse.ArgumentParser(
        description="Merge a LoRA adapter back into the base model"
    )
    parser.add_argument(
        "--base_model",
        type=str,
        default="Qwen/Qwen2.5-0.5B-Instruct",
        help="HuggingFace model ID of the base model",
    )
    parser.add_argument(
        "--adapter_path",
        type=str,
        default="training/outputs/liltii-lora",
        help="Path to the trained LoRA adapter directory",
    )
    parser.add_argument(
        "--output_dir",
        type=str,
        default="training/outputs/liltii-merged",
        help="Directory to save the merged model",
    )
    parser.add_argument(
        "--device",
        type=str,
        default="cpu",
        help="Device to load the base model on (cpu recommended for merging to avoid VRAM issues)",
    )
    return parser.parse_args()


def main():
    args = parse_args()

    print(f"=== Merge LoRA Adapter ===")
    print(f"Base model  : {args.base_model}")
    print(f"Adapter     : {args.adapter_path}")
    print(f"Output dir  : {args.output_dir}")
    print(f"Device      : {args.device}")
    print()

    # ------------------------------------------------------------------ #
    # 1. Load the base model in full precision (not quantized)            #
    # ------------------------------------------------------------------ #
    print("Loading base model in full precision...")
    base_model = AutoModelForCausalLM.from_pretrained(
        args.base_model,
        torch_dtype=torch.float16,
        device_map=args.device,
        trust_remote_code=True,
    )

    # ------------------------------------------------------------------ #
    # 2. Load the LoRA adapter on top of the base model                   #
    # ------------------------------------------------------------------ #
    print(f"Loading LoRA adapter from {args.adapter_path}...")
    model = PeftModel.from_pretrained(base_model, args.adapter_path)

    # ------------------------------------------------------------------ #
    # 3. Merge adapter weights into base model                            #
    # ------------------------------------------------------------------ #
    print("Merging LoRA weights into base model...")
    model = model.merge_and_unload()

    # ------------------------------------------------------------------ #
    # 4. Save the merged model                                            #
    # ------------------------------------------------------------------ #
    os.makedirs(args.output_dir, exist_ok=True)

    print(f"Saving merged model to {args.output_dir}...")
    model.save_pretrained(args.output_dir, safe_serialization=True)

    # Also save the tokenizer alongside the merged model
    tokenizer = AutoTokenizer.from_pretrained(args.base_model, trust_remote_code=True)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token
    tokenizer.save_pretrained(args.output_dir)

    print(f"\nMerged model saved to: {args.output_dir}")
    print("Done!")


if __name__ == "__main__":
    main()
