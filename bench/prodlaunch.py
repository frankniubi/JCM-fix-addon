"""Launch a real launcher instance's client stack offline in a throwaway game dir, to test a jar exactly as players load it.

JCMACCEL_MC_DIR=<path to .minecraft> [JCMACCEL_MC_VERSION=1.20.1-Quilt] python bench/prodlaunch.py <gamedir> [extra -D jvm args...]
The instance itself is only read (libraries, assets, version json, natives); mods come from <gamedir>/mods.
"""
import json
import os
import subprocess
import sys
import uuid
from pathlib import Path

MC = Path(os.environ["JCMACCEL_MC_DIR"])
VERSION = os.environ.get("JCMACCEL_MC_VERSION", "1.20.1-Quilt")
game_dir = Path(sys.argv[1]).resolve()
extra = sys.argv[2:]
v = json.loads((MC / "versions" / VERSION / f"{VERSION}.json").read_text(encoding="utf-8"))


def lib_path(lib):
    art = lib.get("downloads", {}).get("artifact")
    if art and art.get("path"):
        return MC / "libraries" / art["path"]
    group, name, ver, *cls = lib["name"].split(":")
    file = f"{name}-{ver}" + (f"-{cls[0]}" if cls else "") + ".jar"
    return MC / "libraries" / Path(*group.split(".")) / name / ver / file


def allowed(rules):
    if not rules:
        return True
    ok = False
    for r in rules:
        os_rule = r.get("os")
        match = os_rule is None or os_rule.get("name") == "windows"
        if "features" in r:
            match = False
        if match:
            ok = r["action"] == "allow"
    return ok


cp = []
for lib in v["libraries"]:
    if not allowed(lib.get("rules")) or "natives" in lib:
        continue
    p = lib_path(lib)
    if p.is_file() and str(p) not in cp:
        cp.append(str(p))
cp.append(str(MC / "versions" / VERSION / f"{VERSION}.jar"))

subs = {
    "${natives_directory}": str(MC / "versions" / VERSION / "natives-windows-x86_64"),
    "${launcher_name}": "jcmaccel-test", "${launcher_version}": "1",
    "${classpath}": os.pathsep.join(cp), "${classpath_separator}": os.pathsep,
    "${library_directory}": str(MC / "libraries"),
    "${auth_player_name}": "JcmTest", "${version_name}": VERSION, "${game_directory}": str(game_dir),
    "${assets_root}": str(MC / "assets"), "${assets_index_name}": v["assetIndex"]["id"],
    "${auth_uuid}": str(uuid.uuid4()).replace("-", ""), "${auth_access_token}": "0", "${clientid}": "0",
    "${auth_xuid}": "0", "${user_type}": "legacy", "${version_type}": "release", "${user_properties}": "{}",
}


def expand(args):
    out = []
    for a in args:
        if isinstance(a, dict):
            if not allowed(a.get("rules")):
                continue
            vals = a["value"] if isinstance(a["value"], list) else [a["value"]]
        else:
            vals = [a]
        for s in vals:
            for k, val in subs.items():
                s = s.replace(k, val)
            out.append(s)
    return out


jvm = ["-Xmx6G"] + extra + expand(v["arguments"]["jvm"])
game = expand(v["arguments"]["game"]) + ["--width", "1600", "--height", "900"]
if any(a.startswith("-Djcmaccel.bench=") for a in extra):
    game += ["--quickPlaySingleplayer", "jcmbench"]
cmd = ["java"] + jvm + [v["mainClass"]] + game
log = open(game_dir / "prodlaunch.log", "w", encoding="utf-8", errors="replace")
sys.exit(subprocess.call(cmd, cwd=game_dir, stdout=log, stderr=subprocess.STDOUT))
