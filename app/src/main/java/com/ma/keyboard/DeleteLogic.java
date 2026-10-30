package com.ma.keyboard;

/**
 * Pure text logic for ONE press of backspace (no Android dependencies, unit-tested on the JVM).
 *
 * Returns how many UTF-16 chars before the cursor make up the last "user-perceived character":
 *  - surrogate pairs are never split (fixes half-deleted emoji showing as a broken box)
 *  - emoji with skin tones, variation selectors (FE0F), keycaps (1️⃣), tag flags and
 *    ZWJ sequences (👨‍👩‍👧, 🏳️‍🌈) are removed as one unit
 *  - flags made of two regional indicators (🇮🇷) are removed as one unit
 *  - Persian/Arabic diacritics (harakat), ZWNJ (نیم‌فاصله) and plain letters are removed one at a time,
 *    which is what a Persian typist expects when fixing a mark
 *  - "\r\n" is treated as one line break
 */
public final class DeleteLogic {
    private DeleteLogic() {}

    public static final int ZWJ = 0x200D;

    public static boolean isExtender(int cp) {
        return cp == 0xFE0F || cp == 0xFE0E || cp == 0x20E3
                || (cp >= 0x1F3FB && cp <= 0x1F3FF)      // skin tones
                || (cp >= 0xE0020 && cp <= 0xE007F);     // tag sequence (subdivision flags)
    }

    public static boolean isRegional(int cp) { return cp >= 0x1F1E6 && cp <= 0x1F1FF; }

    /** Rough "is this an emoji-like pictograph" test, enough to decide whether a ZWJ glues two parts. */
    public static boolean isPictographic(int cp) {
        return cp >= 0x1F000 && cp <= 0x1FAFF
                || cp >= 0x2600 && cp <= 0x27BF
                || cp >= 0x2190 && cp <= 0x21FF
                || cp >= 0x2300 && cp <= 0x23FF
                || cp >= 0x2B00 && cp <= 0x2BFF
                || cp >= 0x25A0 && cp <= 0x25FF
                || cp == 0x00A9 || cp == 0x00AE || cp == 0x203C || cp == 0x2049
                || cp == 0x2122 || cp == 0x2139 || cp == 0x24C2 || cp == 0x3030
                || cp == 0x303D || cp == 0x3297 || cp == 0x3299;
    }

    public static int charsToDelete(CharSequence s) {
        if (s == null) return 0;
        int end = s.length();
        if (end == 0) return 0;
        if (end >= 2 && s.charAt(end - 1) == '\n' && s.charAt(end - 2) == '\r') return 2;

        int last = Character.codePointBefore(s, end);
        int start = end - Character.charCount(last);

        // Flags: pairs of regional indicators. Count the run to know where the pair boundary is.
        if (isRegional(last)) {
            int run = 0, p = end;
            while (p > 0) {
                int cp = Character.codePointBefore(s, p);
                if (!isRegional(cp)) break;
                run++; p -= Character.charCount(cp);
            }
            if (run >= 2 && run % 2 == 0) {
                int prev = Character.codePointBefore(s, start);
                return end - (start - Character.charCount(prev));
            }
            return end - start;
        }

        // Lone combining mark / ZWNJ / ordinary letter: one code point at a time,
        // unless it is an emoji extender (handled below).
        if (!isExtender(last) && !isPictographic(last)) return end - start;

        // Emoji cluster: walk backwards over  base [extenders]* ( ZWJ base [extenders]* )*
        int p = start;
        int cp = last;
        while (isExtender(cp) && p > 0) {           // swallow trailing extenders down to their base
            cp = Character.codePointBefore(s, p);
            p -= Character.charCount(cp);
        }
        while (p > 0) {
            int before = Character.codePointBefore(s, p);
            if (before != ZWJ || p - 1 <= 0) break;
            int q = p - 1;
            int c2 = Character.codePointBefore(s, q);
            if (!isPictographic(c2) && !isExtender(c2)) break;
            q -= Character.charCount(c2);
            while (isExtender(c2) && q > 0) {
                c2 = Character.codePointBefore(s, q);
                q -= Character.charCount(c2);
            }
            p = q;
        }
        return end - p;
    }
}
