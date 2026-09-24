#!/usr/bin/env python3
"""
How much of what هوهو says is vowelled yet.

Walks every narration line in the manifest, takes the words the voice will read, and reports the
ones tools/vowels.py still has no spelling for — worst offenders first, so the next batch of
entries is always the one that matters most.

    python3 tools/check_vowels.py          # a summary and the 40 commonest gaps
    python3 tools/check_vowels.py --all    # every gap, one per line, for pasting into vowels.py
"""
import collections
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from vowels import VOWELS  # noqa: E402

MANIFEST = "app/src/main/res/raw/audio_manifest.txt"
TOKEN = re.compile(r"[ء-ی‌ً-ْ]+")
MARKS = "ًٌٍَُِّْ"


def main():
    missing, total, covered = collections.Counter(), 0, 0
    for line in open(MANIFEST, encoding="utf-8"):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        cols = line.split("|")
        if len(cols) < 3:
            continue
        for word in TOKEN.findall(cols[1]):
            bare = word.rstrip(MARKS)
            if not bare:
                continue
            total += 1
            if bare in VOWELS or any(c in MARKS for c in bare):
                covered += 1
            else:
                missing[bare] += 1

    print("words spoken: %d — vowelled: %d (%.1f%%)" % (total, covered, 100 * covered / max(total, 1)))
    print("still to write: %d forms (%d times spoken)" % (len(missing), sum(missing.values())))
    show = missing.most_common() if "--all" in sys.argv else missing.most_common(40)
    for word, count in show:
        print('    "%s": "",   # %d' % (word, count))


if __name__ == "__main__":
    main()
