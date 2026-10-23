#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Android strings.xml Translator

Translates Android string resources using Google Translate (via translate.googleapis.com)
while safely preserving:
- Android format specifiers (%1$s, %2$d, %3$s, %1$d%%, etc.)
- Unicode escape sequences (\\u00B7, \\u25C0, \\u25B6, \\u2013, \\u2764)
- XML escaping (\\', \\", &amp;, &lt;, &gt;)
- translatable="false" strings
- XML comments and layout structure
"""

import os
import re
import sys
import json
import time
import subprocess
import urllib.parse
from pathlib import Path

# Mapping of target locales to Google Translate language codes and Android resource folders
# (Based on translation-locales in the project root)
TARGET_CONFIGS = [
    {"name": "Hindi", "gt_lang": "hi", "folders": ["values-hi"]},
    {"name": "Spanish", "gt_lang": "es", "folders": ["values-es"]},
    {"name": "Portuguese (Brazil)", "gt_lang": "pt", "folders": ["values-pt-rBR"]},
    {"name": "Indonesian", "gt_lang": "id", "folders": ["values-in", "values-id"]},
    {"name": "French", "gt_lang": "fr", "folders": ["values-fr"]},
    {"name": "German", "gt_lang": "de", "folders": ["values-de"]},
    {"name": "Japanese", "gt_lang": "ja", "folders": ["values-ja"]},
    {"name": "Korean", "gt_lang": "ko", "folders": ["values-ko"]},
    {"name": "Russian", "gt_lang": "ru", "folders": ["values-ru"]},
    {"name": "Turkish", "gt_lang": "tr", "folders": ["values-tr"]},
    {"name": "Arabic", "gt_lang": "ar", "folders": ["values-ar"]},
    {"name": "Thai", "gt_lang": "th", "folders": ["values-th"]},
    {"name": "Vietnamese", "gt_lang": "vi", "folders": ["values-vi"]},
    {"name": "Chinese (Simplified)", "gt_lang": "zh-CN", "folders": ["values-zh-rCN"]},
]

STRING_TAG_RE = re.compile(
    r'^(?P<indent>\s*)<string\s+name="(?P<name>[^"]+)"(?P<extra>[^>]*)>(?P<content>.*?)</string>(?P<tail>.*)$'
)

def preprocess_text(text):
    """
    Replaces placeholders, unicode escapes, and special sequences with safe tokens.
    """
    placeholders = []

    def save_token(token):
        idx = len(placeholders)
        placeholders.append(token)
        return f"__V{idx}__"

    # 1. Protect literal escaped percent %%
    text = re.sub(r"%%", lambda m: save_token("%%"), text)

    # 2. Protect Android format specifiers like %1$s, %2$d, %s, %d
    text = re.sub(r"%\d+\$[a-zA-Z]|%[a-zA-Z]", lambda m: save_token(m.group(0)), text)

    # 3. Protect unicode escapes like \u00B7, \u25C0, etc.
    text = re.sub(r"\\u[0-9a-fA-F]{4}", lambda m: save_token(m.group(0)), text)

    # 4. Unescape XML escaped quotes for natural translation
    text = text.replace(r"\'", "'").replace(r'\"', '"')

    # 5. Unescape XML entities for natural translation
    text = text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")

    return text, placeholders

def postprocess_text(text, placeholders):
    """
    Restores placeholders, properly escapes quotes and XML characters.
    """
    # 1. Restore tokens
    for idx, orig in enumerate(placeholders):
        # MT engines might insert spaces around or inside tokens
        pattern = rf"__\s*V{idx}\s*__"
        text = re.sub(pattern, lambda m, r=orig: r, text)

    # 2. Escape XML characters: & must be escaped first, without breaking existing entities
    text = re.sub(r"&(?!(amp|lt|gt|quot|apos);)", "&amp;", text)
    text = text.replace("<", "&lt;").replace(">", "&gt;")

    # 3. Android string escaping:
    # Apostrophes: normalize any existing \' to ', then escape all ' to \'
    text = text.replace(r"\'", "'").replace("'", r"\'")
    # Quotes: normalize \" to ", then escape " to \"
    text = text.replace(r'\"', '"').replace('"', r'\"')

    # 4. Strip any leading/trailing extraneous whitespace that MT may add
    return text.strip()

def query_google_translate(text, target_lang, source_lang="en"):
    """
    Queries Google Translate API via curl to avoid TLS fingerprint blocks.
    """
    encoded_q = urllib.parse.quote(text)
    url = f"https://translate.googleapis.com/translate_a/single?client=gtx&sl={source_lang}&tl={target_lang}&dt=t&q={encoded_q}"
    cmd = ["curl", "-s", "--max-time", "15", url]
    
    for attempt in range(3):
        try:
            out = subprocess.check_output(cmd)
            data = json.loads(out)
            translated = "".join(seg[0] for seg in data[0] if seg and seg[0])
            return translated
        except Exception as e:
            time.sleep(1 + attempt)
            if attempt == 2:
                raise e

def translate_batch(batch_texts, target_lang, source_lang="en"):
    """
    Translates a batch of texts separated by newline. Falls back to 1-by-1 if line count changes.
    """
    if not batch_texts:
        return []
    
    combined = "\n".join(batch_texts)
    try:
        translated_blob = query_google_translate(combined, target_lang, source_lang)
        parts = translated_blob.split("\n")
        if len(parts) == len(batch_texts):
            return parts
        else:
            print(f"      [Warning] Batch line count mismatch ({len(parts)} vs {len(batch_texts)}). Translating individually...")
    except Exception as e:
        print(f"      [Warning] Batch error ({e}). Translating individually...")

    # Fallback to item-by-item translation
    results = []
    for item in batch_texts:
        try:
            res = query_google_translate(item, target_lang, source_lang)
            results.append(res)
        except Exception as e:
            print(f"      [Error] Failed to translate: {item[:30]}... ({e})")
            results.append(item)
        time.sleep(0.1)
    return results

def translate_file(input_file, res_dir, source_lang="en"):
    with open(input_file, "r", encoding="utf-8") as f:
        raw_lines = f.readlines()

    # Parse lines to identify translatable string items
    translatable_items = []  # list of (line_idx, name, orig_content, prep_text, placeholders)
    
    for idx, line in enumerate(raw_lines):
        m = STRING_TAG_RE.match(line)
        if m:
            name = m.group("name")
            extra = m.group("extra")
            content = m.group("content")
            
            # Check translatable="false"
            if 'translatable="false"' in extra:
                continue
            
            prep_text, placeholders = preprocess_text(content)
            translatable_items.append((idx, name, content, prep_text, placeholders))

    print(f"Found {len(translatable_items)} translatable strings in {input_file}")

    # Process each target configuration
    batch_size = 25

    for config in TARGET_CONFIGS:
        target_name = config["name"]
        gt_lang = config["gt_lang"]
        target_folders = config["folders"]
        
        print(f"\nTranslating into {target_name} ({gt_lang}) -> {target_folders}...")
        
        all_prep_texts = [item[3] for item in translatable_items]
        translated_prep_texts = []
        
        # Translate in batches
        for b_start in range(0, len(all_prep_texts), batch_size):
            b_end = min(b_start + batch_size, len(all_prep_texts))
            batch = all_prep_texts[b_start:b_end]
            res = translate_batch(batch, gt_lang, source_lang)
            translated_prep_texts.extend(res)
            # Polite pause to avoid any rate-limiting
            time.sleep(0.2)

        # Reconstruct output lines
        output_lines = list(raw_lines)
        for (line_idx, name, orig_content, prep_text, placeholders), translated_prep in zip(translatable_items, translated_prep_texts):
            post_text = postprocess_text(translated_prep, placeholders)
            # Reconstruct string tag
            m = STRING_TAG_RE.match(raw_lines[line_idx])
            indent = m.group("indent")
            extra = m.group("extra")
            tail = m.group("tail")
            new_line = f'{indent}<string name="{name}"{extra}>{post_text}</string>{tail}\n'
            output_lines[line_idx] = new_line

        # Write to all target folders
        for folder in target_folders:
            folder_path = Path(res_dir) / folder
            folder_path.mkdir(parents=True, exist_ok=True)
            out_file = folder_path / "strings.xml"
            with open(out_file, "w", encoding="utf-8") as f:
                f.writelines(output_lines)
            print(f"  -> Saved {out_file}")

    print("\nAll translations completed!")

if __name__ == "__main__":
    base_dir = Path(__file__).resolve().parent.parent
    input_file = base_dir / "app/src/main/res/values/strings.xml"
    res_dir = base_dir / "app/src/main/res"
    translate_file(input_file, res_dir)
