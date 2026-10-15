#!/usr/bin/env python3
"""
Convert a merged HuggingFace model to GGUF format and quantize to Q2_K.

Uses llama.cpp's convert_hf_to_gguf.py as a subprocess. Falls back to
calling it as a module if the script is on the PATH.

Usage:
    python training/convert_to_gguf.py
    python training/convert_to_gguf.py --model_path training/outputs/liltii-merged --output_path training/outputs/liltii-v0.2-q2_k.gguf
"""

import argparse
import os
import subprocess
import sys


def find_converter_script():
    """Locate llama.cpp's convert_hf_to_gguf.py in common locations."""
    candidates = [
        # Project-local (preferred, matches Android third_party checkout)
        os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            "app", "src", "main", "cpp", "third_party", "llama.cpp",
            "convert_hf_to_gguf.py",
        ),
        # System PATH fallback
        "convert_hf_to_gguf.py",
    ]
    for candidate in candidates:
        if os.path.isfile(candidate):
            return candidate
    return None


def main():
    parser = argparse.ArgumentParser(
        description="Convert HuggingFace model to GGUF with Q2_K quantization"
    )
    parser.add_argument(
        "--model_path",
        type=str,
        default="training/outputs/liltii-merged",
        help="Path to the merged HuggingFace model directory",
    )
    parser.add_argument(
        "--output_path",
        type=str,
        default="training/outputs/liltii-v0.2-q2_k.gguf",
        help="Output GGUF file path",
    )
    parser.add_argument(
        "--converter",
        type=str,
        default=None,
        help="Path to convert_hf_to_gguf.py (auto-detected if not provided)",
    )
    args = parser.parse_args()

    print(f"=== Convert to GGUF ===")
    print(f"Model path  : {args.model_path}")
    print(f"Output path : {args.output_path}")
    print()

    converter = args.converter or find_converter_script()
    if converter is None:
        print("ERROR: Could not find convert_hf_to_gguf.py.")
        print("Install llama.cpp and ensure convert_hf_to_gguf.py is on your PATH,")
        print("or pass --converter /path/to/convert_hf_to_gguf.py")
        sys.exit(1)

    print(f"Using converter: {converter}")

    # Ensure output directory exists
    os.makedirs(os.path.dirname(os.path.abspath(args.output_path)), exist_ok=True)

    # Step 1: Convert to GGUF (FP16 or unquantized intermediate)
    # We produce a temporary GGUF, then quantize it.
    temp_gguf = args.output_path.replace(".gguf", "-temp-f16.gguf")

    convert_cmd = [
        sys.executable,
        converter,
        args.model_path,
        "--outfile", temp_gguf,
        "--outtype", "f16",
    ]
    print(f"Running conversion: {' '.join(convert_cmd)}")
    result = subprocess.run(convert_cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"Conversion failed:\n{result.stderr}")
        sys.exit(1)
    print("Conversion successful.")

    # Step 2: Quantize to Q2_K using llama.cpp's quantize tool
    # We look for the quantize binary in common locations.
    quantize_bin = find_quantize_binary()
    if quantize_bin is None:
        print("\nWARNING: Could not find llama.cpp 'quantize' binary.")
        print(f"Manually run: quantize {temp_gguf} {args.output_path} Q2_K")
        print(f"  (or build llama.cpp: cmake -B build && cmake --build build)")
        # Clean up temp file only if quantization won't run
        if os.path.exists(temp_gguf):
            print(f"\nTemporary GGUF left at: {temp_gguf}")
        sys.exit(1)

    quantize_cmd = [
        quantize_bin,
        temp_gguf,
        args.output_path,
        "Q2_K",
    ]
    print(f"\nRunning quantization: {' '.join(quantize_cmd)}")
    result = subprocess.run(quantize_cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"Quantization failed:\n{result.stderr}")
        sys.exit(1)

    # Clean up temp file
    if os.path.exists(temp_gguf):
        os.remove(temp_gguf)

    # Report output size
    size_mb = os.path.getsize(args.output_path) / (1024 * 1024)
    print(f"\nQuantized GGUF saved to: {args.output_path} ({size_mb:.1f} MB)")
    print("Done!")


def find_quantize_binary():
    """Locate the llama.cpp quantize binary."""
    candidates = [
        # Project-local build
        os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            "app", "src", "main", "cpp", "third_party", "llama.cpp",
            "build", "bin", "quantize",
        ),
        # System PATH
        "quantize",
        # Common install locations
        os.path.expanduser("~/llama.cpp/build/bin/quantize"),
        "/usr/local/bin/quantize",
    ]
    for candidate in candidates:
        if os.path.isfile(candidate):
            return candidate
        # Check PATH for bare binary name
        if not os.path.sep in candidate:
            try:
                which = subprocess.run(
                    ["which", candidate], capture_output=True, text=True
                )
                if which.returncode == 0 and which.stdout.strip():
                    return which.stdout.strip()
            except Exception:
                pass
    return None


if __name__ == "__main__":
    main()
