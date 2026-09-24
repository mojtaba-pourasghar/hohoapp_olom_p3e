#!/usr/bin/env python3
"""Turns project/quiz/c*.txt (one question per line) into data/Chapter<N>Quiz.java.

    python3 tools/make_quiz.py


  M|section|level|question|why|right|wrong1|wrong2|wrong3
  T|section|level|statement|true/false|why
"""
import glob, os, re, sys
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'app/src/main/java/com/hoohooolom/app/data')
def q(s): return '"' + s.replace('\\', '\\\\').replace('"', '\\"') + '"'
for path in sorted(glob.glob(os.path.join(ROOT, 'project/quiz/c*.txt'))):
    n = int(re.search(r'c(\d+)\.txt$', path).group(1))
    lines = [l.strip() for l in open(path, encoding='utf-8') if l.strip() and not l.startswith('#')]
    body = []
    for l in lines:
        f = [x.strip() for x in l.split('|')]
        if f[0] == 'M':
            assert len(f) >= 8, l
            body.append('            .mcq(%s, %s, %s, %s, %s)' % (int(f[1]), int(f[2]), q(f[3]), q(f[4]), ', '.join(q(x) for x in f[5:])))
        elif f[0] == 'T':
            assert len(f) == 6 and f[4] in ('true', 'false'), l
            body.append('            .tf(%s, %s, %s, %s, %s)' % (int(f[1]), int(f[2]), q(f[3]), f[4], q(f[5])))
        else:
            raise SystemExit('bad line: ' + l)
    src = """package com.hoohooolom.app.data;

import java.util.List;

/**
 * The hand-written practice / worksheet / exam questions of درس %d. Generated from
 * project/quiz/c%02d.txt by tools/make_quiz.py — edit the text file, not this one.
 */
final class Chapter%dQuiz {
    private Chapter%dQuiz() {}

    static final List<QuizBank.Q> ALL = new QuizBank.Builder(%d)
%s
            .build();
}
""" % (n, n, n, n, n - 1, '\n'.join(body))
    open(os.path.join(OUT, 'Chapter%dQuiz.java' % n), 'w', encoding='utf-8').write(src)
    print(path, len(body))
