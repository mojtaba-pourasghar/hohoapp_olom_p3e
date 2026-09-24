package com.hoohooolom.app.tts;

/**
 * Turns a written question into something the speech engine can actually read.
 *
 * The questions are written the way the textbook writes them — «۶ × ۷ = ⬜», «۴/۸», «→» — and a
 * text-to-speech engine either skips those symbols or spells them out in English, which is why
 * the voice sounded broken. Here every symbol becomes the Persian words a teacher would say.
 */
public final class SpokenText {
    private SpokenText() {}

    private static final String[][] REPLACEMENTS = {
        {"⬜", " چند "},
        {"×", " ضربدر "},
        {"÷", " تقسیم بر "},
        {"−", " منهای "},
        {"–", " منهای "},
        {"+", " به‌اضافه‌ی "},
        {"=", " مساوی "},
        {"→", " می‌شود "},
        {"…", " و ادامه دارد "},
        {"«", " "},
        {"»", " "},
        {"(", " "},
        {")", " "},
        {"_", " جای خالی "},
        {"٪", " درصد "},
        {"%", " درصد "},
    };

    public static String forSpeech(String written) {
        if (written == null || written.isEmpty()) return written;
        String out = written;

        // a fraction is read "صورت روی مخرج", but a plain slash elsewhere is just a pause
        out = out.replaceAll("([۰-۹0-9])\\s*/\\s*([۰-۹0-9])", "$1 روی $2");
        out = out.replace("/", " ");

        for (String[] pair : REPLACEMENTS) out = out.replace(pair[0], pair[1]);

        // "۵-" style trailing signs and stray dashes read as nothing useful
        out = out.replace(" - ", " منهای ");

        // an empty box at the end of a pattern is a question, not a word
        out = out.replace("،  چند", "، و بعد چند");

        return out.replaceAll("\\s{2,}", " ").trim();
    }
}
