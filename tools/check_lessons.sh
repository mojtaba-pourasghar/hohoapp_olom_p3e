#!/usr/bin/env bash
# Syntax-checks and sanity-checks the lesson data classes with plain javac. They are pure Java
# (no Android imports), so a mistake in a Chapter*Pages.java or Chapter*Quiz.java shows up here
# in a second instead of waiting for a whole APK build on CI.
set -e
cd "$(dirname "$0")/.."
SRC=app/src/main/java/com/hoohooolom/app/data
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
javac -nowarn -d "$OUT" \
  "$SRC"/LessonKind.java "$SRC"/LessonStep.java "$SRC"/LessonScript.java "$SRC"/StageSpec.java \
  "$SRC"/Book.java "$SRC"/Lessons.java "$SRC"/PageLessons.java "$SRC"/PersianDigits.java \
  "$SRC"/QuizBank.java "$SRC"/QuizBuilder.java "$SRC"/QuizMode.java "$SRC"/QuizSession.java \
  "$SRC"/QuestionItem.java "$SRC"/Scene.java "$SRC"/Chapter*Pages.java "$SRC"/Chapter*Quiz.java \
  tools/CheckLessons.java
java -cp "$OUT" CheckLessons
