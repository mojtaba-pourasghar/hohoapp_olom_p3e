package com.hoohooolom.app.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a number into the recorded clips that say it in Persian.
 *
 * Questions are generated on the fly, so they can never have one recording each. Instead هوهو
 * records the words: every number up to a hundred as a whole word, then the hundreds, «هزار» and
 * «و». «۳۴۵۶» becomes «سه هزار و چهارصد و پنجاه و شش» — four clips played back to back.
 */
public final class SpokenNumber {
    private SpokenNumber() {}

    /** The largest number that can be said from the recorded words. */
    public static final long MAX = 9999;

    public static List<String> clips(long value) {
        List<String> out = new ArrayList<>();
        if (value < 0 || value > MAX) return null;
        if (value == 0) {
            out.add("n_0");
            return out;
        }

        long rest = value;
        long thousands = rest / 1000;
        rest %= 1000;
        if (thousands > 0) {
            if (thousands > 1) out.addAll(under1000(thousands));
            out.add("n_1000");
        }
        if (rest > 0) {
            if (!out.isEmpty()) out.add("va");
            out.addAll(under1000(rest));
        }
        return out;
    }

    private static List<String> under1000(long value) {
        List<String> out = new ArrayList<>();
        long rest = value;
        long hundreds = rest / 100;
        rest %= 100;
        if (hundreds > 0) out.add("n_" + (hundreds * 100));
        if (rest > 0) {
            if (!out.isEmpty()) out.add("va");
            // every number up to a hundred is one recorded word, so it never sounds chopped
            out.add("n_" + rest);
        }
        return out;
    }
}
