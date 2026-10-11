"""stackq.py file.stacks inclusive k1 k2 ... | callers KEY | children KEY"""
import sys, collections
lines = [l.rstrip("\n").split(";") for l in open(sys.argv[1], encoding="utf-8")]
n = len(lines); mode = sys.argv[2]; strip = lambda f: f.rsplit(":", 1)[0]
if mode == "inclusive":
    for k in sys.argv[3:]:
        c = sum(1 for s in lines if any(k in strip(f) for f in s)); print(f"{c:6d} {100*c/n:5.1f}%  {k}")
elif mode in ("callers", "children"):
    k = sys.argv[3]; cnt = collections.Counter(); tot = 0
    for s in lines:
        for i, f in enumerate(s):
            if k in strip(f):
                tot += 1
                j = i + 1 if mode == "callers" else i - 1
                cnt[s[j] if 0 <= j < len(s) else "<self>"] += 1
                break
    print(f"{k}: {tot} samples ({100*tot/n:.1f}%) of {n}")
    for f, c in cnt.most_common(int(sys.argv[4]) if len(sys.argv) > 4 else 15): print(f"{c:6d} {f}")
