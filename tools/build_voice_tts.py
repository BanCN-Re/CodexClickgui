"""Rebuild the six boot voice lines with Edge neural TTS, then give them the
terminal-announcement character the dsh startup screen uses.

Usage:
    python tools/build_voice_tts.py                 # default voice
    python tools/build_voice_tts.py --voice en-US-AndrewNeural
    python tools/build_voice_tts.py --audition      # 4 candidates of line 1 into run/voice-audition
"""

from __future__ import annotations

import argparse
import asyncio
import os
import subprocess

import edge_tts
import imageio_ffmpeg

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VOICE_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "carnation", "sounds", "voice")
AUDITION_DIR = os.path.join(ROOT, "run", "voice-audition")
FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()

# The six slots of the boot timeline, in cue order.
LINES = [
    # (slot, spoken text, rate, pitch)
    ("line1", "Rhine Lab secure shell. Requesting access.", "-6%", "-1Hz"),
    ("line2", "Identity record confirmed.", "-8%", "-1Hz"),
    ("line3", "Archive mounted.", "-8%", "-1Hz"),
    ("line4", "Structure analysis running.", "-6%", "-1Hz"),
    ("line5", "Permission scan complete.", "-7%", "-1Hz"),
    ("line6", "Welcome to Rhine Lab. Clearance granted.", "-10%", "-2Hz"),
]

CANDIDATES = [
    "en-US-AvaNeural",
    "en-US-AndrewNeural",
    "en-US-AriaNeural",
    "en-GB-SoniaNeural",
]

# Narrow band + light room slap = a voice coming out of a terminal speaker,
# not a studio read. Kept gentle so the words stay easy to follow.
RADIO = (
    "highpass=f=140,"
    "lowpass=f=7600,"
    "acompressor=threshold=-20dB:ratio=3:attack=8:release=140,"
    "aecho=0.9:0.55:24:0.14,"
    "loudnorm=I=-15:TP=-1.5:LRA=8"
)


async def synth(text: str, voice: str, rate: str, pitch: str, out_mp3: str) -> None:
    await edge_tts.Communicate(text, voice, rate=rate, pitch=pitch).save(out_mp3)


def encode(mp3: str, ogg: str) -> None:
    subprocess.run(
        [FFMPEG, "-y", "-loglevel", "error", "-i", mp3,
         "-af", RADIO,
         "-ar", "48000", "-ac", "2", "-c:a", "libvorbis", "-q:a", "5", ogg],
        check=True,
    )
    print(f"{os.path.basename(ogg):12s} {os.path.getsize(ogg):7d} B")


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--voice", default="en-US-AvaNeural")
    ap.add_argument("--audition", action="store_true")
    args = ap.parse_args()

    if args.audition:
        os.makedirs(AUDITION_DIR, exist_ok=True)
        text = LINES[0][1]
        for voice in CANDIDATES:
            mp3 = os.path.join(AUDITION_DIR, f"{voice}.mp3")
            ogg = os.path.join(AUDITION_DIR, f"{voice}.ogg")
            await synth(text, voice, "-6%", "-1Hz", mp3)
            encode(mp3, ogg)
        return

    os.makedirs(VOICE_DIR, exist_ok=True)
    for slot, text, rate, pitch in LINES:
        mp3 = os.path.join(VOICE_DIR, f"{slot}.mp3")
        ogg = os.path.join(VOICE_DIR, f"{slot}.ogg")
        await synth(text, args.voice, rate, pitch, mp3)
        encode(mp3, ogg)
        os.remove(mp3)


if __name__ == "__main__":
    asyncio.run(main())
