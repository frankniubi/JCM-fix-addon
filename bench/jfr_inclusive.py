"""Inclusive render-thread profile: each sample counts a method at most once."""
import json, subprocess, sys, collections
jfr = sys.argv[1]
out = subprocess.run(["jfr", "print", "--json", "--events", "jdk.ExecutionSample", "--stack-depth", "128", jfr],
                     capture_output=True, text=True, encoding="utf-8", errors="replace").stdout
events = json.loads(out)["recording"]["events"]
total = 0; inc = collections.Counter(); jcm_any = 0; truncated = 0
for e in events:
    v = e["values"]
    if v["sampledThread"]["javaName"] != "Render thread": continue
    total += 1
    st = v["stackTrace"]
    if st.get("truncated"): truncated += 1
    names = {f["method"]["type"]["name"] + "." + f["method"]["name"] for f in st["frames"]}
    if any(n.startswith("com.lx862") for n in names): jcm_any += 1
    for n in names: inc[n] += 1
print(f"render samples {total}, truncated {truncated}, with any JCM frame {jcm_any} ({100*jcm_any/total:.1f}%)")
keys = sys.argv[2:] or ["com.lx862", "findClosePlatform", "requestArrivals", "PIDSRenderer", "DepartureTimer",
       "JsonPIDSPreset", "ScriptPIDSPreset", "getComponents", "RenderPIDS", "RenderRouteSign", "RenderPSDAPGDoor",
       "RenderRails", "RenderRailwaySign", "RenderStationName", "BlockEntityRenderDispatcher.render", "MainRenderer.render",
       "WorldRenderer.render", "ChunkBuilder", "tickBlockEntities", "ClientWorld.tickEntities"]
for k in keys:
    hits = [n for n in inc if k in n]
    c = sum(1 for e in events if e["values"]["sampledThread"]["javaName"] == "Render thread" and any(k in (f["method"]["type"]["name"] + "." + f["method"]["name"]) for f in e["values"]["stackTrace"]["frames"]))
    print(f"{c:6d} {100*c/total:5.1f}%  {k}")
