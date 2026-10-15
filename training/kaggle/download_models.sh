#!/bin/bash
# Script to download trained models from Kaggle after training completes
# Usage: bash training/kaggle/download_models.sh <kaggle-username> <competition/notebook-name>

KAGGLE_USER=$1
NOTEBOOK_NAME=$2
OUTPUT_DIR="training/outputs/kaggle_models"

mkdir -p $OUTPUT_DIR

# Download the output files
kaggle kernels output $KAGGLE_USER/$NOTEBOOK_NAME -p $OUTPUT_DIR

echo "Models downloaded to $OUTPUT_DIR"
ls -lh $OUTPUT_DIR
