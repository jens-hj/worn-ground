#!/usr/bin/env python3
"""Run a graphical Minecraft task on an isolated Xvfb display: no window, sound or input on the desktop."""
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import time

root = Path(__file__).resolve().parents[1]
xvfb = shutil.which("Xvfb")
if xvfb is None:
    candidates = sorted(Path("/nix/store").glob("*-xorg-server-*/bin/Xvfb"))
    xvfb = str(candidates[-1]) if candidates else None
if not xvfb:
    raise SystemExit("Xvfb is required; run inside nix develop")
if len(sys.argv) < 2:
    raise SystemExit("Usage: headless.py ./gradlew runShowcase [options]")
started = time.time()
with (root / "build" / "xvfb.log").open("w") as log:
    display = subprocess.Popen([xvfb, "-displayfd", "1", "-screen", "0", "1920x1080x24", "-nolisten", "tcp"],
                               stdout=subprocess.PIPE, stderr=log, text=True)
    try:
        number = display.stdout.readline().strip()
        if not number.isdigit():
            raise SystemExit("Could not start isolated X display; see build/xvfb.log")
        environment = os.environ.copy()
        environment["DISPLAY"] = ":" + number
        environment["SDL_VIDEO_DRIVER"] = "x11"
        environment.pop("WAYLAND_DISPLAY", None)
        # Runtime tests must inherit the isolated display, even when a Gradle daemon exists.
        command = sys.argv[1:]
        if "gradlew" in Path(command[0]).name and "--no-daemon" not in command:
            command.append("--no-daemon")
        process = subprocess.Popen(command, env=environment, cwd=root, start_new_session=True)
        try:
            code = process.wait(timeout=45 * 60)
        except subprocess.TimeoutExpired:
            # This isolated process group belongs only to this invocation.
            os.killpg(process.pid, signal.SIGTERM)
            process.wait(timeout=15)
            raise SystemExit("Headless client exceeded 45 minutes")
        if code:
            raise SystemExit(code)
        marker = root / "build/showcase/result.txt"
        if not marker.exists() or marker.stat().st_mtime < started:
            raise SystemExit("Minecraft exited without finishing the showcase")
        print(marker.read_text())
    finally:
        display.terminate()
        display.wait(timeout=10)
