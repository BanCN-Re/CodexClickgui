"""Offline render of RhineLabUI's synthesized terminal cues (MIT, LBEILC).

Mirrors src/audio.ts `synthesizeSound`: sine oscillators with exponential frequency ramps,
attack + exponential decay envelopes, a seeded noise band-pass for "air", and the glass modes.
The browser master gain (0.8) is applied; no compressor is needed at these levels.
"""

from __future__ import annotations

import math
import os
import subprocess
import tempfile

import imageio_ffmpeg
import numpy as np
import soundfile as sf

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "carnation", "sounds", "ui")
RATE = 48000

NOISE = None


def noise_buffer():
    global NOISE
    if NOISE is None:
        seed = 773
        data = np.empty(RATE * 2)
        for i in range(len(data)):
            seed = (seed * 1664525 + 1013904223) & 0xFFFFFFFF
            data[i] = seed / 2147483648.0 - 1.0
        NOISE = data
    return NOISE


def envelope(n, gain, attack, duration):
    t = np.arange(n) / RATE
    a = min(attack, duration * 0.3)
    env = np.zeros(n)
    rise = t < a
    env[rise] = gain * t[rise] / max(a, 1e-9)
    decay = (~rise) & (t < duration)
    if np.any(decay):
        p = (t[decay] - a) / max(duration - a, 1e-9)
        env[decay] = gain * np.power(1e-5 / gain, p)
    tail = t >= duration
    if np.any(tail):
        p = np.clip((t[tail] - duration) / 0.012, 0, 1)
        env[tail] = 1e-5 * (1 - p)
    return env


def tone(f, to, gain, duration, delay=0.0, attack=0.006):
    n = int((duration + 0.02) * RATE)
    t = np.arange(n) / RATE
    ratio = to / f
    if abs(ratio - 1.0) < 1e-9:
        phase = 2 * math.pi * f * t
    else:
        k = math.log(ratio) / duration
        phase = 2 * math.pi * f * (np.exp(k * t) - 1) / k
    return np.sin(phase) * envelope(n, gain, attack, duration), delay


def air(f, to, gain, duration, delay=0.0, attack=0.008):
    n = int((duration + 0.02) * RATE)
    src = noise_buffer()[:n].copy()
    # Time-varying RBJ band-pass, coefficients updated per 64-sample block.
    out = np.zeros(n)
    zi = np.zeros(2)
    for start in range(0, n, 64):
        block = min(64, n - start)
        t = start / RATE
        p = min(1.0, t / max(duration, 1e-9))
        fc = f * math.pow(to / f, p)
        w0 = 2 * math.pi * fc / RATE
        alpha = math.sin(w0) / (2 * 0.8)
        b0, b1, b2 = alpha, 0.0, -alpha
        a0, a1, a2 = 1 + alpha, -2 * math.cos(w0), 1 - alpha
        b = np.array([b0 / a0, b1 / a0, b2 / a0])
        a = np.array([1.0, a1 / a0, a2 / a0])
        from scipy.signal import lfilter
        out[start:start + block], zi = lfilter(b, a, src[start:start + block], zi=zi)
    return out * envelope(n, gain, attack, duration), delay


def glass(fundamental, gain, decay, delay=0.0):
    modes = [(1, 1, 1), (1.47, 0.39, 0.66), (2.09, 0.21, 0.4), (2.73, 0.095, 0.25), (3.86, 0.035, 0.15)]
    parts = []
    for ratio, amplitude, damping in modes:
        freq = fundamental * ratio
        if freq > min(8500, RATE * 0.42):
            continue
        parts.append(tone(freq, freq, gain * amplitude, decay * damping, delay, 0.0012))
    parts.append(air(4800, 3600, gain * 0.24, 0.013, delay, 0.0008))
    return parts


def render(parts):
    end = max(delay + len(data) / RATE for data, delay in parts)
    out = np.zeros(int(end * RATE) + 1)
    for data, delay in parts:
        at = int(delay * RATE)
        out[at:at + len(data)] += data
    peak = np.abs(out).max()
    if peak > 0:
        out *= 0.8 / max(peak, 0.0001) * min(1.0, 0.9 / 0.8)
    return out


def build(name, parts):
    data = render(parts)
    path = os.path.join(OUT, name + ".ogg")
    with tempfile.TemporaryDirectory() as temp:
        wav = os.path.join(temp, name + ".wav")
        sf.write(wav, np.clip(data, -1, 1), RATE, format="WAV", subtype="PCM_16")
        subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-y", "-loglevel", "error", "-i", wav,
                        "-c:a", "libvorbis", "-q:a", "5", path], check=True)
    print("wrote", path, os.path.getsize(path))


def main():
    os.makedirs(OUT, exist_ok=True)

    build("brand", [
        tone(146.83, 146.83, 0.039, 0.72, 0, 0.08),
        tone(293.66, 293.66, 0.03, 0.62, 0.07, 0.07),
        tone(440, 440, 0.022, 0.54, 0.17, 0.055),
        air(420, 1750, 0.036, 0.7, 0, 0.13),
    ])

    build("confirm", [
        tone(640, 640, 0.039, 0.095, 0, 0.008),
        tone(960, 960, 0.026, 0.15, 0.095, 0.009),
    ])

    scan_parts = [air(1800, 3400, 0.025, 0.8, 0, 0.12)]
    for i in range(4):
        scan_parts.append(tone(760, 760, 0.025, 0.064, i * 0.19 + 0.15, 0.007))
    build("scan", scan_parts)

    build("welcome", [
        tone(293.66, 293.66, 0.034, 1.6, 0, 0.05),
        tone(440, 440, 0.034, 1.6, 0.095, 0.05),
        tone(659.25, 659.25, 0.034, 1.6, 0.19, 0.05),
        tone(739.99, 739.99, 0.034, 1.6, 0.285, 0.05),
        air(600, 1800, 0.065, 0.9, 0, 0.15),
    ])

    build("text", [
        air(2100, 1300, 0.033, 0.064, 0, 0.005),
        tone(1050, 1050, 0.012, 0.06, 0, 0.005),
    ])

    array_parts = [air(1600, 3300, 0.025, 0.8, 0, 0.12)]
    for i in range(5):
        array_parts += glass(1180 + i * 170, 0.043 - i * 0.005, 0.31, 0.05 + i * 0.105)
    build("array", array_parts)

    build("open", glass(1150, 0.071, 0.58) + glass(2180, 0.025, 0.36, 0.16)
          + [air(3100, 4400, 0.014, 0.25, 0.035, 0.025)])

    build("glass_tick", glass(1680, 0.064, 0.24))


if __name__ == "__main__":
    main()
