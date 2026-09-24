package com.hoohooolom.app.data;

/** Hands the just-built/just-finished QuizSession from the Quiz screen to the Result screen. */
public final class QuizSessionHolder {
    private static QuizSession current;

    private QuizSessionHolder() {}

    public static void set(QuizSession session) {
        current = session;
    }

    public static QuizSession get() {
        return current;
    }
}
