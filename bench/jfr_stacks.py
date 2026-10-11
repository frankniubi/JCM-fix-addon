"""Convert a JFR to render-thread stacks (one line per sample, leaf first, ';'-joined) once; later queries read the .stacks file."""
import json, subprocess, sys
jfr = sys.argv[1]
out = subprocess.run(["jfr", "print", "--json", "--events", "jdk.ExecutionSample", "--stack-depth", "128", jfr],
                     capture_output=True, text=True, encoding="utf-8", errors="replace").stdout
with open(jfr[:-4] + ".stacks", "w", encoding="utf-8") as f:
    for e in json.loads(out)["recording"]["events"]:
        v = e["values"]
        if v["sampledThread"]["javaName"] != "Render thread": continue
        f.write(";".join(fr["method"]["type"]["name"].replace("/", ".") + "." + fr["method"]["name"] + ":" + str(fr["lineNumber"]) for fr in v["stackTrace"]["frames"]) + "\n")
