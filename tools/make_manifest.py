#!/usr/bin/env python3
"""
Rebuilds res/raw/audio_manifest.txt from the lesson scripts, so the list of narration files is
never out of step with what هوهو actually says.

Every lesson step names its audio file first and the spoken line right after it, which is all
this needs: it walks the lesson sources chapter by chapter and writes «key | text | تلفظ» lines,
grouped under each chapter, and inside it under each page and each section.

    python3 tools/make_manifest.py
    python3 tools/make_manifest.py --plain   # third column without the vowels
"""
import glob
import hashlib
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pronounce import spoken as _spoken, number_word, HUNDREDS  # noqa: E402

# «--plain» writes the third column without the vowels — see tools/pronounce.py
VOWELS_ON = "--plain" not in sys.argv


def spoken(text):
    return _spoken(text, vowels=VOWELS_ON)

SRC = "app/src/main/java/com/hoohooolom/app/data"
OUT = "app/src/main/res/raw/audio_manifest.txt"

KEY = re.compile(r'"([pt]\d{3}_\d+x?)"\s*,\s*\n?\s*"((?:[^"\\]|\\.)*)"', re.S)
# «// ۱. ...» in the section lessons, «// ── صفحه‌ی ۷ — ... ──» in the page lessons
SECTION = re.compile(r'^\s*//\s*(?:(?:۰|[۱-۹][۰-۹]*|\d+)\.\s*(.+?)|──\s*(.+?)\s*──)\s*$', re.M)

CHAPTERS = [
    ("۱", "زنگ علوم"), ("۲", "خوراکی‌ها"), ("۳", "اندازه‌گیری مواد"), ("۴", "موادّ اطراف ما"),
    ("۵", "آب، ماده‌ی با ارزش"), ("۶", "زندگی ما و آب"), ("۷", "نور و مشاهده‌ی اجسام"),
    ("۸", "جست‌وجو کنیم و بسازیم"), ("۹", "نیرو، همه جا (۱)"), ("۱۰", "نیرو، همه جا (۲)"),
    ("۱۱", "بکارید و ببینید"), ("۱۲", "هر کدام جای خود (۱)"), ("۱۳", "هر کدام جای خود (۲)"),
    ("۱۴", "از گذشته تا آینده"),
]

# ── the words هوهو needs to read a generated question ─────────────────────────
# Questions and their hints are made up on the fly, so they cannot have one recording each.
# Instead every fixed piece of wording in project/quiz gets a recording, and numbers are said
# from recorded number words. tts/QuestionVoice splits a line exactly the same way at run time.

QUIZ = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "project", "quiz")
DIGITS = re.compile(r"[0-9۰-۹٠-٩]+")
LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')

# the same table as tts/QuestionVoice.SPOKEN — a speech engine cannot read «×» or «⬜»
SPOKEN = [
    ("⬜", "چند"), ("×", "ضربدر"), ("÷", "تقسیم بر"), ("−", "منهای"), ("–", "منهای"),
    ("+", "به‌اضافه‌ی"), ("=", "مساوی"), ("→", "می‌شود"), ("/", "روی"),
    ("«", " "), ("»", " "), ("،", " "), (".", " "), ("؟", " "), ("!", " "), (":", " "),
    ("؛", " "), ("(", " "), (")", " "), ("—", " "), ("…", " "), ("⌫", " "),
]


def spoken_form(piece):
    out = piece
    for symbol, word in SPOKEN:
        out = out.replace(symbol, " " + word + " ")
    return re.sub(r"\s+", " ", out).strip()


def phrase_key(piece):
    return "q_" + hashlib.sha1(piece.encode("utf-8")).hexdigest()[:10]


def question_words():
    """Every piece of wording a generated question can contain, plus the number words."""
    lines = ["\n\n# ══════════ واژه‌های خواندنِ سؤال‌های تمرین و آزمون ══════════",
             "# این‌ها جمله نیستند؛ تکه‌هایی‌اند که کنارِ هم گذاشته می‌شوند تا هر سؤالِ تولیدشده خوانده شود.",
             "\n# ---- عددها ----"]
    count = 0
    for value in range(0, 101):
        word = number_word(value)
        lines.append("n_%d | %s | %s" % (value, word, word))
        count += 1
    for hundred, word in sorted(HUNDREDS.items()):
        if hundred != 1:
            lines.append("n_%d | %s | %s" % (hundred * 100, word, word))
            count += 1
    lines.append("n_1000 | هزار | هزار")
    lines.append("va | و | و")
    count += 2

    lines.append("\n# ---- تکه‌های متنِ سؤال‌ها ----")
    seen = set()
    texts = []
    for path in sorted(glob.glob(os.path.join(QUIZ, "c*.txt"))):
        for raw in open(path, encoding="utf-8"):
            f = [x.strip() for x in raw.strip().split("|")]
            if not raw.strip() or raw.startswith("#"):
                continue
            if f[0] == "M":
                texts += [f[3], f[4]] + f[5:]
            elif f[0] == "T":
                texts += ["درست یا نادرست؟ " + f[3], f[5], "درست", "نادرست"]
    for text in texts:
        for piece in DIGITS.split(text):
            piece = spoken_form(piece)
            if not piece or piece in seen:
                continue
            seen.add(piece)
            key = phrase_key(piece)
            lines.append("%s | %s | %s" % (key, piece, spoken(piece)))
            count += 1
    return "\n".join(lines), count


HEADER = """# فهرست گفتارهای درس — هوهو علوم
#
# هر خط سه ستون دارد:
#     <نام فایل> | <متنی که هوهو می‌گوید> | <تلفظ آوایی برای ضبط صدا>
#
# ستون دوم همان چیزی است که در اپ نوشته و خوانده می‌شود.
# ستون سوم فقط برای ساختِ صداست: نمادها و عددها به واژه تبدیل شده‌اند و واژه‌هایی که موتور
# گفتار اشتباه می‌خواند (کسر، مخرج، محور، …) با اعراب نوشته شده‌اند. اپ این ستون را نمی‌خواند.
# اگر واژه‌ای هنوز بد تلفظ شد، آن را در جدولِ WORDS در tools/pronounce.py اصلاح کن.
#
# کلیدهایی که به x ختم می‌شوند، مثالِ بیشترِ همان گام هستند (دکمه‌ی «یک مثال دیگر بزن») و با
# سرعتِ کمتری ضبط می‌شوند. کلیدهایی که به f ختم می‌شوند، بازخوردِ بعد از جوابِ کودک‌اند.
#
# این فایل با دست نوشته نمی‌شود — از خودِ درس‌ها ساخته می‌شود:
#     python3 tools/make_manifest.py
#
# ── ساخت فایل‌های صوتی ────────────────────────────────────────────────
#     pip install edge-tts
#     python3 tools/make_voice.py
#
# اگر خودت ضبط می‌کنی، فایل را با همان نام در app/src/main/res/raw/ بگذار
# (مثلاً p007_01.ogg). پسوندهای ogg و mp3 و wav هر سه کار می‌کنند.
#
# اگر فایلی نباشد، اپ همان متن را با موتور گفتار فارسی دستگاه می‌خواند.
#
# تعداد کل گفتارها: {count}
"""


# the feedback هوهو gives after an answer is the last text argument of an mcq/num/build step;
# it is spoken too, so it needs a recording of its own — same key as the step, plus «f»
CALL = re.compile(r"LessonStep\.(?:mcq|num|build)\s*\(")


def call_literals(src, open_paren):
    """Top-level string arguments of the call whose '(' sits at open_paren, and where it ends."""
    i, depth, lits = open_paren, 0, []
    while i < len(src):
        c = src[i]
        if c == '"':
            j, buf = i + 1, []
            while j < len(src) and src[j] != '"':
                if src[j] == "\\":
                    buf.append(src[j:j + 2])
                    j += 2
                    continue
                buf.append(src[j])
                j += 1
            if depth == 1:                     # options inside Arrays.asList(...) sit deeper
                lits.append("".join(buf))
            i = j + 1
            continue
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return lits, i
        i += 1
    return lits, i


def feedback_marks(src):
    """«<key>f | بازخورد» for every step that answers the child back."""
    marks = []
    for m in CALL.finditer(src):
        lits, end = call_literals(src, m.end() - 1)
        if len(lits) < 2:
            continue
        key, why = lits[0], lits[-1]
        if why and why != key:
            marks.append((end, "line", (key + "f", why)))
    return marks


def read_block(path):
    """«key | text» lines of one source file, with its own heading comments in place."""
    src = open(path, encoding="utf-8").read()
    marks = [(m.start(), "section", (m.group(1) or m.group(2)).strip())
             for m in SECTION.finditer(src)]
    marks += [(m.start(), "line", (m.group(1), m.group(2))) for m in KEY.finditer(src)]
    marks += feedback_marks(src)
    marks.sort(key=lambda t: t[0])

    lines, count = [], 0
    for _, kind, value in marks:
        if kind == "section":
            lines.append("\n# ---- %s ----" % value)
        else:
            key, text = value
            text = text.replace("\\n", " ")
            lines.append("%s | %s | %s" % (key, text, spoken(text)))
            count += 1
    return lines, count


def main():
    blocks = []
    total = 0
    for index, (number, title) in enumerate(CHAPTERS, 1):
        chapter = ["\n\n# ══════════ درس %s — %s ══════════" % (number, title)]
        pages = os.path.join(SRC, "Chapter%dPages.java" % index)

        if os.path.exists(pages):
            lines, count = read_block(pages)
            if lines:
                chapter.append("\n## کتاب، صفحه به صفحه")
                chapter.extend(lines)
                total += count
        blocks.append("\n".join(chapter))

    for path in sorted(glob.glob(os.path.join(SRC, "Chapter*.java"))):
        name = os.path.basename(path)
        if not re.match(r"Chapter([1-9]|1[0-4])(Pages|Quiz)\.java$", name):
            print("note: %s is not in the chapter list and was skipped" % name)

    words, word_count = question_words()
    total += word_count

    with open(OUT, "w", encoding="utf-8") as f:
        f.write(HEADER.format(count=total))
        f.write("".join(blocks))
        f.write(words)
        f.write("\n")
    print("narration lines:", total, "(including", word_count, "question words)")


if __name__ == "__main__":
    main()
