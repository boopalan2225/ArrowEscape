#!/usr/bin/env python3
"""Synthesise the per-theme sound packs shipped in `app/src/main/res/raw`.

Each app theme gets its own voice. Classic keeps the original hand-made clips;
the other themes are generated here so the palette can be re-tuned by editing
one table instead of hunting for audio files.

Run:  python3 tools/generate_sfx.py
"""
from __future__ import annotations

import math
import os
import struct
import wave

SAMPLE_RATE = 44_100
RAW_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app", "src", "main", "res", "raw",
)

# A note name is friendlier than a float, and the maths is one line.
A4 = 440.0
SEMITONES = {"C": -9, "D": -7, "E": -5, "F": -4, "G": -2, "A": 0, "B": 2}


def hz(note: str) -> float:
    """'A4' -> 440.0. Accepts an optional '#' after the letter."""
    letter, rest = note[0], note[1:]
    sharp = rest.startswith("#")
    octave = int(rest[1:] if sharp else rest)
    steps = SEMITONES[letter] + (1 if sharp else 0) + 12 * (octave - 4)
    return A4 * (2 ** (steps / 12))


def envelope(index: int, total: int, attack: float, decay: float) -> float:
    """Percussive shape: quick ramp in, exponential tail out."""
    t = index / SAMPLE_RATE
    rise = min(1.0, t / attack) if attack > 0 else 1.0
    fall = math.exp(-t / decay)
    # Fade the last 3% to zero so no clip ends on a click.
    tail = min(1.0, (total - index) / (0.03 * total))
    return rise * fall * tail


def voice(
    note: str,
    seconds: float,
    harmonics=(1.0,),
    attack: float = 0.004,
    decay: float = 0.18,
    vibrato_hz: float = 0.0,
    vibrato_depth: float = 0.0,
    detune: float = 0.0,
):
    """One struck note rendered as floats in [-1, 1]."""
    base = hz(note)
    total = int(seconds * SAMPLE_RATE)
    samples = []
    for i in range(total):
        t = i / SAMPLE_RATE
        wobble = 1.0 + vibrato_depth * math.sin(2 * math.pi * vibrato_hz * t)
        value = 0.0
        for partial, weight in enumerate(harmonics, start=1):
            freq = base * partial * wobble
            value += weight * math.sin(2 * math.pi * freq * t)
            if detune:
                value += weight * 0.5 * math.sin(2 * math.pi * freq * (1 + detune) * t)
        samples.append(value * envelope(i, total, attack, decay))
    return samples


def mix(*layers):
    """Overlay layers of equal or differing length, longest wins."""
    length = max(len(layer) for layer in layers)
    out = [0.0] * length
    for layer in layers:
        for i, value in enumerate(layer):
            out[i] += value
    return out


def sequence(parts, gap: float = 0.0):
    """Play parts one after another, optionally overlapping (negative gap)."""
    out: list[float] = []
    cursor = 0
    for part in parts:
        end = cursor + len(part)
        if end > len(out):
            out.extend([0.0] * (end - len(out)))
        for i, value in enumerate(part):
            out[cursor + i] += value
        cursor += max(1, len(part) + int(gap * SAMPLE_RATE))
    return out


def write(name: str, samples, peak: float = 0.72) -> None:
    loudest = max(abs(s) for s in samples) or 1.0
    scale = peak / loudest
    frames = b"".join(
        struct.pack("<h", int(max(-1.0, min(1.0, s * scale)) * 32_767)) for s in samples
    )
    path = os.path.join(RAW_DIR, name)
    with wave.open(path, "w") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(SAMPLE_RATE)
        out.writeframes(frames)
    print(f"wrote {path} ({len(frames) // 1024} KB)")


# ── The palette: one entry per theme, three clips each ───────────────────

def midnight():
    """Deep, glassy bells with a long tail - the quiet-hours theme."""
    return {
        "success": voice("A5", 0.42, (1.0, 0.4, 0.22), decay=0.14),
        "error": voice("D3", 0.40, (1.0, 0.55, 0.3), attack=0.01, decay=0.11, vibrato_hz=22, vibrato_depth=0.02),
        "complete": sequence(
            [
                voice(n, 0.7, (1.0, 0.35, 0.18), decay=0.26)
                for n in ("D4", "A4", "D5", "F#5")
            ],
            gap=-0.52,
        ),
    }


def pirate():
    """Creaking wood, a gold-coin chime and a shanty cadence."""
    return {
        "success": voice("D4", 0.26, (1.0, 0.8, 0.55, 0.3), attack=0.002, decay=0.06, detune=0.01),
        "error": voice("A2", 0.36, (1.0, 0.9, 0.6, 0.35), attack=0.004, decay=0.09, vibrato_hz=9, vibrato_depth=0.05),
        "complete": sequence(
            [
                voice("D4", 0.45, (1.0, 0.6, 0.4), decay=0.14, detune=0.006),
                voice("F4", 0.45, (1.0, 0.6, 0.4), decay=0.14, detune=0.006),
                voice("A4", 0.45, (1.0, 0.6, 0.4), decay=0.14, detune=0.006),
                mix(
                    voice("D5", 0.8, (1.0, 0.5, 0.3), decay=0.24, vibrato_hz=6, vibrato_depth=0.015),
                    voice("A4", 0.8, (0.6, 0.3), decay=0.22, detune=0.008),
                ),
            ],
            gap=-0.3,
        ),
    }


def mono():
    """Pure sine clicks - nothing but the note, for the ink-and-paper theme."""
    return {
        "success": voice("B5", 0.14, (1.0,), attack=0.001, decay=0.03),
        "error": voice("B3", 0.18, (1.0, 0.3), attack=0.001, decay=0.04),
        "complete": sequence(
            [
                voice("B4", 0.22, (1.0,), decay=0.06),
                voice("F#5", 0.22, (1.0,), decay=0.06),
                voice("B5", 0.5, (1.0, 0.25), decay=0.16),
            ],
            gap=-0.1,
        ),
    }


def sunset():
    """Warm brassy chimes with a slow shimmer - the golden-hour theme."""
    return {
        "success": voice("C#5", 0.3, (1.0, 0.5, 0.3, 0.15), decay=0.09, detune=0.004),
        "error": voice("F3", 0.34, (1.0, 0.65, 0.4), attack=0.006, decay=0.09, vibrato_hz=14, vibrato_depth=0.03),
        "complete": sequence(
            [
                voice("F4", 0.55, (1.0, 0.45, 0.25), decay=0.18, detune=0.003),
                voice("A4", 0.55, (1.0, 0.45, 0.25), decay=0.18, detune=0.003),
                voice("C5", 0.55, (1.0, 0.45, 0.25), decay=0.18, detune=0.003),
                mix(
                    voice("F5", 0.9, (1.0, 0.4, 0.2), decay=0.3, vibrato_hz=5, vibrato_depth=0.01),
                    voice("C6", 0.9, (0.4, 0.2), decay=0.26),
                ),
            ],
            gap=-0.38,
        ),
    }


PACKS = {"midnight": midnight, "sunset": sunset, "pirate": pirate, "mono": mono}
CLIP_FILES = {
    "success": "{theme}_tap_success.wav",
    "error": "{theme}_tap_error.wav",
    "complete": "{theme}_level_complete.wav",
}


def main() -> None:
    for theme, build in PACKS.items():
        for clip, samples in build().items():
            write(CLIP_FILES[clip].format(theme=theme), samples)


if __name__ == "__main__":
    main()
