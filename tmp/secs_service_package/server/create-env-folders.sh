#!/usr/bin/env bash

# Read the specified file line by line
while IFS= read -r line; do
    # Check if the line contains an assignment (KEY=VALUE)
    if [[ "$line" =~ ^[[:space:]]*([^=]+)=([^#]+) ]]; then
        key="${BASH_REMATCH[1]}"
        value="${BASH_REMATCH[2]}"

        # Check if the value appears to be an absolute filepath
        if [[ "$value" =~ ^/ ]]; then
            echo "Making sure directory '$value' exists ('$line')"
            mkdir -p "$value"
        fi
    fi
done < "$1"
