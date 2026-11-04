import os
import subprocess
import tempfile

import imageio_ffmpeg

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VOICE = os.path.join(ROOT, "src", "main", "resources", "assets", "carnation", "sounds", "voice")
UI = os.path.join(ROOT, "src", "main", "resources", "assets", "carnation", "sounds", "ui")
FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()


def encode(src, dst):
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-i", src,
                    "-ar", "48000", "-ac", "2", "-c:a", "libvorbis", "-q:a", "5", dst], check=True)
    print(dst, os.path.getsize(dst))


def main():
    os.makedirs(VOICE, exist_ok=True)
    os.makedirs(UI, exist_ok=True)
    for i in range(1, 7):
        src = os.path.join(os.environ["TEMP"], f"dsh_line-{i}.mp3")
        encode(src, os.path.join(VOICE, f"line{i}.ogg"))
    encode(os.path.join(os.environ["TEMP"], "dsh_sfx-tick.mp3"), os.path.join(UI, "tick.ogg"))
    encode(os.path.join(os.environ["TEMP"], "dsh_sfx-ticks-run.mp3"), os.path.join(UI, "ticks_run.ogg"))


if __name__ == "__main__":
    main()
