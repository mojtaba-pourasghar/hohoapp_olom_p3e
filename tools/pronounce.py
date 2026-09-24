#!/usr/bin/env python3
"""
The spoken form of a narration line — what the voice should actually say.

A Persian speech engine reads «۶ × ۷ = ⬜» as silence or as English, and Persian leaves the short
vowels out, so it has to guess them — and it guesses wrong often enough to spoil a lesson (کسر،
مخرج، محور…). So every line in res/raw/audio_manifest.txt carries a third column: the same
sentence with its numbers and symbols written as words and every word given its vowels.

The vowels come from tools/vowels.py, which has a spelling for each word the lessons use. A word
already written with its vowels in the lesson text is left exactly as it is.

That third column is what the voice files are recorded from, and what the app speaks with the
device voice until they exist. If a word sounds wrong, fix it in tools/vowels.py and rebuild:

    python3 tools/make_manifest.py
    python3 tools/check_vowels.py      # says whether anything is still unspelled
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from vowels import VOWELS  # noqa: E402

# ── digits ───────────────────────────────────────────────────────────────────
# «نه» is written out here: on its own it is the word for «no», and only the vowel tells the
# voice that this one is the number nine
ONES = ["صفر", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نُه", "ده",
        "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده"]
TENS = {2: "بیست", 3: "سی", 4: "چهل", 5: "پنجاه", 6: "شصت", 7: "هفتاد", 8: "هشتاد", 9: "نود"}
HUNDREDS = {1: "صد", 2: "دویست", 3: "سیصد", 4: "چهارصد", 5: "پانصد",
            6: "ششصد", 7: "هفتصد", 8: "هشتصد", 9: "نهصد"}


def number_word(value):
    """۱۳۵۷ → «هزار و سیصد و پنجاه و هفت». Falls back to digit-by-digit above 9999."""
    if value < 0:
        return "منهای " + number_word(-value)
    if value > 9999:
        return " ".join(ONES[int(d)] for d in str(value))
    parts = []
    thousands, rest = divmod(value, 1000)
    if thousands:
        parts.append("هزار" if thousands == 1 else ONES[thousands] + " هزار")
    hundreds, rest = divmod(rest, 100)
    if hundreds:
        parts.append(HUNDREDS[hundreds])
    if rest < 20:
        if rest or not parts:
            parts.append(ONES[rest])
    else:
        tens, ones = divmod(rest, 10)
        parts.append(TENS[tens])
        if ones:
            parts.append(ONES[ones])
    return " و ".join(parts)


DIGITS = str.maketrans("۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩", "01234567890123456789")
# Arabic letterforms a Persian voice stumbles over
LETTERS = str.maketrans({"ي": "ی", "ك": "ک", "ة": "ه", "أ": "ا", "إ": "ا", "ؤ": "و"})

# ── symbols ──────────────────────────────────────────────────────────────────
# the same table as tts/SpokenText and tts/QuestionVoice — a voice cannot read «×» or «⬜»
SYMBOLS = [
    ("⬜", " چند "), ("×", " ضربدر "), ("÷", " تقسیم بر "), ("−", " منهای "),
    ("+", " به‌اضافه‌ی "), ("=", " مساوی "), ("→", " می‌شود "),
    ("٪", " درصد "), ("%", " درصد "), ("_", " جای خالی "),
    ("…", "، "), ("«", " "), ("»", " "), ("(", " "), (")", " "),
    ("—", "، "), ("–", "، "), ("⌫", " "), ("🎉", " "),
    ("‎", ""), ("‏", ""),
]

# ── giving every word its vowels ─────────────────────────────────────────────
KASRA = "\u0650"
MARKS = set("\u064B\u064C\u064D\u064E\u064F\u0650\u0651\u0652")
_DIGIT_RUN = re.compile(r"[0-9]+")
_TOKEN = re.compile(r"[\u0621-\u06CC\u200c\u064B-\u0652]+")


def _respell(token):
    """One word, spelled the way it is said. Unknown words are left alone."""
    bare, ezafe = token, ""
    if bare.endswith(KASRA):                  # «فصلِ هشت» — keep the ezafe, spell the word
        bare, ezafe = bare[:-1], KASRA
    if any(ch in MARKS for ch in bare):       # the lesson already wrote it out (نُه, مربّع)
        return token
    said = VOWELS.get(bare)
    return said + ezafe if said else token


def spoken(text, vowels=True):
    """
    The line rewritten the way it should be said out loud.

    With vowels=False the words are left as they are written and only the numbers and symbols
    become words — the way the column looked before the vowel table existed. Keep it as a way
    back if a voice ever turns out to read the vowelled spelling worse than the plain one.
    """
    if not text:
        return text
    out = text.translate(LETTERS).translate(DIGITS)

    # a fraction is read «صورت روی مخرج»; a slash anywhere else is just a pause
    out = re.sub(r"([0-9])\s*/\s*([0-9])", r"\1 روی \2", out)
    out = out.replace("/", " ")

    for symbol, word in SYMBOLS:
        out = out.replace(symbol, word)

    out = _DIGIT_RUN.sub(lambda m: " " + number_word(int(m.group())) + " ", out)
    if vowels:
        out = _TOKEN.sub(lambda m: _respell(m.group()), out)

    out = re.sub(r"\s+([،؛,])", r"\1", out)          # no space before a comma
    out = re.sub(r"([،؛])\1+", r"\1", out)             # and never two in a row
    return re.sub(r"\s{2,}", " ", out).strip()
