#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script to convert words_tr.txt into a fixed-record memory-mapped binary dictionary (words_tr.bin).

Record Format:
- Word: 32 bytes (UTF-8 encoded, padded with \x00 null bytes)
- Frequency: 4 bytes (Int32 Big-Endian)
- Total Record Size: 36 bytes
"""

import os
import struct

RECORD_SIZE = 36
WORD_BYTES = 32

def convert_to_binary(input_file: str, output_file: str):
    if not os.path.exists(input_file):
        print(f"Error: Input file '{input_file}' not found.")
        return

    count = 0
    with open(input_file, 'r', encoding='utf-8') as infile, open(output_file, 'wb') as outfile:
        for line in infile:
            line = line.strip()
            if not line:
                continue

            parts = line.split()
            word = parts[0].lower()
            freq = int(parts[1]) if len(parts) > 1 else 0

            # Convert word to UTF-8 byte array
            word_bytes = word.encode('utf-8')

            # Ensure word fits within 32 bytes or truncate if too long
            if len(word_bytes) > WORD_BYTES:
                word_bytes = word_bytes[:WORD_BYTES]
            else:
                # Pad remaining space with \x00 null bytes up to 32 bytes
                word_bytes = word_bytes.ljust(WORD_BYTES, b'\x00')

            # Pack frequency as 4-byte Big-Endian Int32
            freq_bytes = struct.pack('>i', freq)

            # Write 36-byte record
            outfile.write(word_bytes)
            outfile.write(freq_bytes)
            count += 1

    print(f"Successfully converted {count} records ({count * RECORD_SIZE:,} bytes) to '{output_file}'.")

if __name__ == '__main__':
    base_dir = os.path.dirname(os.path.abspath(__file__))
    input_path = os.path.join(base_dir, 'app', 'src', 'main', 'assets', 'words_tr.txt')
    output_path = os.path.join(base_dir, 'app', 'src', 'main', 'assets', 'words_tr.bin')

    print(f"Reading from: {input_path}")
    print(f"Writing to:   {output_path}")
    convert_to_binary(input_path, output_path)
