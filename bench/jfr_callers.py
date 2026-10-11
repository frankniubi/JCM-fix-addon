import json, subprocess, sys, collections
jfr, target = sys.argv[1], sys.argv[2]
out = subprocess.run(["jfr", "print", "--json", "--events", "jdk.ExecutionSample", "--stack-depth", "128", jfr],
                     capture_output=True, text=True, encoding="utf-8", errors="replace").stdout
events = json.loads(out)["recording"]["events"]
callers = collections.Counter(); leaf = collections.Counter(); names = collections.Counter()
for e in events:
    v = e["values"]
    if v["sampledThread"]["javaName"] != "Render thread": continue
    fr = [f["method"]["type"]["name"] + "." + f["method"]["name"] for f in v["stackTrace"]["frames"]]
    for i, n in enumerate(fr):
        if target in n:
            names[n] += 1
            # walk outward to first frame that is not the target / a lambda / stream plumbing
            j = i + 1
            while j < len(fr) and (target in fr[j] or "java.util" in fr[j] or "lambda$" in fr[j]): j += 1
            callers[fr[j] if j < len(fr) else "?"] += 1
            leaf[fr[0]] += 1
            break
print("matched names:", names.most_common(5))
print("callers:"); [print(f"{c:6d} {n}") for n, c in callers.most_common(10)]
print("leaf:"); [print(f"{c:6d} {n}") for n, c in leaf.most_common(8)]
