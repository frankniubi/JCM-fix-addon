#!/usr/bin/env python3
"""
JFR analyzer for JCM rendering hotspots.
Extracts com.lx862.jcm.* methods from the render thread and prints aggregated wall-clock time.
"""
import sys
import subprocess
import json
from pathlib import Path
from collections import defaultdict

def main():
    if len(sys.argv) < 2:
        print("Usage: analyze_jcm.py <baseline.jfr>")
        sys.exit(1)

    jfr = Path(sys.argv[1])
    if not jfr.exists():
        print(f"JFR not found: {jfr}")
        sys.exit(1)

    # jfr print --events jdk.ExecutionSample --stack-depth 32 <file>
    cmd = ["jfr", "print", "--events", "jdk.ExecutionSample", "--stack-depth", "32", str(jfr)]
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")

    if result.returncode != 0:
        print(f"jfr print failed: {result.stderr}")
        sys.exit(1)

    # Parse execution samples
    jcm_samples = defaultdict(int)
    render_thread_samples = 0
    in_render_thread = False
    current_stack = []

    for line in result.stdout.splitlines():
        line = line.strip()

        # Thread line: jdk.ExecutionSample { ... sampledThread = "Render thread" }
        if "sampledThread" in line:
            in_render_thread = "Render thread" in line
            current_stack = []

        # Stack frame: - com.lx862.jcm.mod.render.text.TextRenderingManager.drawInternal
        if in_render_thread and line.startswith("-"):
            frame = line[1:].strip()
            current_stack.append(frame)

            # Count samples where JCM code is on the stack
            if "com.lx862.jcm" in frame or "com.lx862.mtrscripting" in frame:
                # Extract method name
                if "(" in frame:
                    method = frame.split("(")[0]
                else:
                    method = frame
                jcm_samples[method] += 1

        # End of event
        if line == "" and in_render_thread:
            if any("com.lx862" in f for f in current_stack):
                render_thread_samples += 1
            current_stack = []

    # Print results
    total = sum(jcm_samples.values())
    print(f"\n=== JCM Render Thread Samples (total: {total}) ===\n")

    for method, count in sorted(jcm_samples.items(), key=lambda x: -x[1])[:30]:
        pct = 100.0 * count / total if total > 0 else 0
        print(f"{count:5d} ({pct:5.1f}%)  {method}")

    print(f"\nRender thread samples with JCM on stack: {render_thread_samples}")
    print(f"JCM method invocations: {total}")

if __name__ == "__main__":
    main()
