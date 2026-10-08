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
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pronounce import spoken as _spoken, number_word, HUNDREDS  # noqa: E402

# «--plain» writes the third column without the vowels — see tools/pronounce.py
VOWELS_ON = "--plain" not in sys.argv


def spoken(text):
    return _spoken(text, vowels=VOWELS_ON)


# Lines whose spoken form was fixed by hand in the voice studio. The manifest is generated, so
# an edit made there would be lost on the next rebuild — it is kept here instead, and applied
# last. This file is in git, so the correction travels with the project.
OVERRIDES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "voice-overrides.json")


def overrides():
    try:
        with open(OVERRIDES, encoding="utf-8") as fh:
            return json.load(fh)
    except (OSError, ValueError):
        return {}

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
    texts += hint_texts() + ui_texts()
    for text in texts:
        for piece in DIGITS.split(text):
            piece = spoken_form(piece)
            if not piece or piece in seen:
                continue
            seen.add(piece)
            key = phrase_key(piece)
            lines.append("%s | %s | %s" % (key, piece, FIXED.get(key) or spoken(piece)))
            count += 1
    return "\n".join(lines), count


# ── what the book's structure says on its own ─────────────────────────────────
BOOK = os.path.join(SRC, "Book.java")
FA = str.maketrans("0123456789", "۰۱۲۳۴۵۶۷۸۹")


def book_sections():
    """[(numberFa, [section names])] for the fourteen lessons, straight from Book.java."""
    src = open(BOOK, encoding="utf-8").read()
    out = []
    for m in re.finditer(r'new Chapter\(\s*\d+\s*,\s*"([^"]+)"\s*,\s*"[^"]*"\s*,\s*\d+\s*,\s*\d+\s*,(.*?)\)', src, re.S):
        out.append((m.group(1), LITERAL.findall(m.group(2))))
    return out


def section_pages():
    """{(chapter, section): [pages]} — which book pages each section's parts come from."""
    pages = {}
    for path in glob.glob(os.path.join(SRC, "Chapter*Pages.java")):
        for m in re.finditer(r"new LessonScript\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)", open(path, encoding="utf-8").read()):
            pages.setdefault((int(m.group(1)), int(m.group(2))), []).append(int(m.group(3)))
    return {k: sorted(v) for k, v in pages.items()}


def page_span(pages):
    first = str(pages[0]).translate(FA)
    return first + (" تا " + str(pages[-1]).translate(FA) if len(pages) > 1 else "")


def review_lines():
    """The section reviews (data/Lessons.java): its opening and closing lines, word for word."""
    sections, pages = book_sections(), section_pages()
    lines = ["\n\n# ══════════ مرورِ بخش‌ها ══════════",
             "# همان جمله‌هایی که data/Lessons.java برای اوّل و آخرِ هر مرور می‌سازد."]
    count = 0
    for ch, (number, names) in enumerate(sections):
        for sec, name in enumerate(names):
            ps = pages.get((ch, sec))
            if not ps:
                continue
            key = "ch%02d_s%d_" % (ch + 1, sec + 1)
            intro = ("مرورِ بخشِ «" + name + "» از درسِ " + number + ". این بخش صفحه‌ی "
                     + page_span(ps) + " کتاب است. بیا دوباره سؤال‌هایش را با هم جواب بدهیم تا خوب یادت بماند.")
            done = "آفرین! مرورِ بخشِ «" + name + "» تمام شد. حالا تمرین‌های همین بخش را حل کن."
            lines.append("%sintro | %s | %s" % (key, intro, spoken(intro)))
            lines.append("%sdone | %s | %s" % (key, done, spoken(done)))
            count += 2
    return lines, count


def hint_texts():
    """The «راهنما» of every quiz question (QuizFragment.hintFor), one per section."""
    sections, pages = book_sections(), section_pages()
    out = ["جواب درست:"]
    for ch, (number, names) in enumerate(sections):
        for sec, name in enumerate(names):
            ps = pages.get((ch, sec))
            where = (" صفحه‌ی " + page_span(ps) + " کتاب را دوباره ببین.") if ps else ""
            out.append("این سؤال از بخشِ «" + name + "» است." + where)
    return out


UI = "app/src/main/java/com/hoohooolom/app/ui"


def ui_texts():
    """Fixed lines هوهو says on the app's screens: each screen's tip and her nudges."""
    out = []
    for path in sorted(glob.glob(os.path.join(UI, "**", "*.java"), recursive=True)):
        src = open(path, encoding="utf-8").read()
        for m in re.finditer(r"entryTip\(\)\s*\{\s*return\s+((?:\"(?:[^\"\\]|\\.)*\"\s*\+?\s*)+);", src):
            out.append("".join(LITERAL.findall(m.group(1))))
        for m in re.finditer(r"\.(?:say|comfort|celebrate)\(\s*\"((?:[^\"\\]|\\.)*)\"\s*\)", src):
            out.append(m.group(1))
        for m in re.finditer(r"String nudge = [^;]*;", src, re.S):
            out += LITERAL.findall(m.group(0))
    return out


HEADER = """# فهرست گفتارهای درس — هوهو علوم
#
# هر خط سه ستون دارد:
#     <نام فایل> | <متنی که هوهو می‌گوید> | <تلفظ آوایی برای ضبط صدا>
#
# ستون دوم همان چیزی است که در اپ نوشته و خوانده می‌شود.
# ستون سوم مبنای ساختِ صداست: نمادها و عددها به واژه تبدیل شده‌اند و همه‌ی واژه‌ها اعراب دارند
# (جدولش در tools/vowels.py). اگر واژه‌ای بد خوانده شد، همان‌جا اصلاحش کن، یا متنِ همان یک خط
# را در استودیوی صداگذاری عوض کن (در tools/voice-overrides.json می‌ماند).
#
# کلیدها:
#   tPPP_NN     گامِ درس (PPP صفحه‌ی کتاب)      …x  «یک مثال دیگر»      …f  بازخوردِ بعد از جواب
#   chNN_sN_…   اوّل و آخرِ مرورِ هر بخش
#   n_… و va    واژه‌های عدد            q_…  تکه‌های ثابتِ سؤال‌ها، راهنماها و جمله‌های صفحه‌ها
#
# این فایل با دست نوشته نمی‌شود — از خودِ درس‌ها ساخته می‌شود:
#     python3 tools/make_manifest.py
#
# ── ساخت فایل‌های صوتی ────────────────────────────────────────────────
# با استودیوی صداگذاری (tools/voice-studio، آواشو). کلیپ‌ها و index.json روی هاست می‌روند:
#     /public_html/grade-3/olom/audio   ←→   http://mp-apdl.ir/grade-3/olom/audio/
# و اپ هرچه لازم دارد را از همان‌جا دانلود می‌کند. اگر کلیپی نباشد، اپ همان جمله را با
# موتور گفتار فارسی دستگاه می‌خواند.
#
# تعداد کل گفتارها: {count}
"""


# the feedback هوهو gives after an answer is the last text argument of an mcq/num/pick step;
# it is spoken too, so it needs a recording of its own — same key as the step, plus «f»
CALL = re.compile(r"LessonStep\.(?:mcq|num|build|pick)\s*\(")


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
            said = FIXED.get(key) or spoken(text)
            lines.append("%s | %s | %s" % (key, text, said))
            count += 1
    return lines, count


FIXED = {}


def main():
    global FIXED
    FIXED = overrides()
    if FIXED:
        print("اصلاحِ دستیِ متن: %d خط از %s" % (len(FIXED), os.path.basename(OVERRIDES)))

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

    reviews, review_count = review_lines()
    blocks.append("\n".join(reviews))
    total += review_count

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
