#!/usr/bin/env bash
# Structural check of every Java source, without an Android SDK.
#
# javac cannot resolve android.* or androidx.* here, so «cannot find symbol» and «package does
# not exist» are expected and filtered out. What is left is the kind of mistake that has nothing
# to do with Android — a stray brace, an annotation on the wrong thing, a method inside a method
# — and that class of error once cost a whole CI round trip to discover.
#
# Exits non-zero when a structural error is found, so it can gate a push.
set -uo pipefail
cd "$(dirname "$0")/.."

OUT=$(mktemp -d)
LIST="$OUT/sources.txt"
trap 'rm -rf "$OUT"' EXIT

find app/src/main/java -name "*.java" | sort > "$LIST"
javac -nowarn -proc:none -d "$OUT/classes" "@$LIST" 2>&1 \
  | grep -v "JAVA_TOOL_OPTIONS" > "$OUT/javac.txt"

# anything that is not «this Android class is missing» is a real problem
REAL=$(grep "error:" "$OUT/javac.txt" \
  | grep -vE "package [A-Za-z0-9_.]+ does not exist|cannot find symbol|symbol:|location:" \
  || true)

if [ -n "$REAL" ]; then
  echo "$REAL"
  echo
  echo "خطای ساختاری در کد جاوا — بالا را ببین."
  exit 1
fi

echo "java sources are structurally OK ($(wc -l < "$LIST") files)"
