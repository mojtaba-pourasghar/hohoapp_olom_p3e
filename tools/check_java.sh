#!/usr/bin/env bash
# Structural check of every Java source, without an Android SDK.
#
# javac cannot resolve android.* or androidx.* here, so «cannot find symbol» is expected for
# those and is filtered out. But it is NOT filtered when the symbol javac could not find is one
# of this app's own classes: a missing import of UiKit or Screen is a real error that would
# otherwise only show up a CI round trip later, and once did.
#
# What is left is the kind of mistake that has nothing to do with Android — a stray brace, an
# annotation on the wrong thing, a method spliced into another method, a forgotten import of one
# of our own classes.
#
# -Xmaxerrs matters: javac stops reporting after 100 errors by default, and the hundreds of
# expected «android.* is missing» ones used up that budget long before reaching the real one.
#
# Exits non-zero when a real error is found, so it can gate a push.
set -uo pipefail
cd "$(dirname "$0")/.."

OUT=$(mktemp -d)
LIST="$OUT/sources.txt"
trap 'rm -rf "$OUT"' EXIT

find app/src/main/java -name "*.java" | sort > "$LIST"
javac -nowarn -proc:none -Xmaxerrs 100000 -d "$OUT/classes" "@$LIST" 2>&1 \
  | grep -v "JAVA_TOOL_OPTIONS" > "$OUT/javac.txt"

python3 - "$LIST" "$OUT/javac.txt" <<'PY'
import os, re, sys

sources, output = sys.argv[1], sys.argv[2]
# every class this app defines, by its simple name — these must always resolve
ours = {os.path.basename(p).removesuffix(".java")
        for p in open(sources, encoding="utf-8").read().split()}

lines = open(output, encoding="utf-8").read().splitlines()
real = []
for i, line in enumerate(lines):
    if "error:" not in line:
        continue
    if "package " in line and "does not exist" in line:
        continue
    # the supertype itself is an Android class javac cannot see, so it cannot confirm the
    # override either; a genuinely misplaced @Override reads «annotation type not applicable»
    if "does not override or implement a method from a supertype" in line:
        continue
    if "cannot find symbol" in line:
        # javac names the symbol a line or two below; ours is a real miss, android's is not
        nearby = " ".join(lines[i + 1:i + 5])
        named = re.search(r"symbol:\s+\w+\s+([A-Za-z_][\w.]*)", nearby)
        if not named or named.group(1).split(".")[0] not in ours:
            continue
        real.append(line + "   ← " + named.group(1) + " is ours; an import is missing")
        continue
    if re.match(r"^\s*(symbol|location):", line):
        continue
    real.append(line)

if real:
    print("\n".join(real))
    print()
    print("خطای ساختاری در کد جاوا — بالا را ببین.")
    sys.exit(1)
PY
status=$?
[ $status -ne 0 ] && exit $status

echo "java sources are structurally OK ($(wc -l < "$LIST") files)"
