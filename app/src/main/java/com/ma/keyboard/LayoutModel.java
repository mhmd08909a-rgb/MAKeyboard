package com.ma.keyboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure layout rules (no Android dependencies, unit-tested on the JVM).
 *
 * A layout page is a list of rows of stable key ids (e.g. "c:ض#0", "t:DEL#0").
 * Rules that keep the keyboard usable:
 *  - the bottom row (space, enter, ?123, language...) can only be reordered inside itself
 *  - system keys (ids starting with "t:" : shift, delete, enter, ...) stay in their own row
 *  - character keys may move between all rows except the bottom row
 *  - no row (other than the bottom row) may become empty
 *  - every key of the default layout appears exactly once: unknown ids are dropped,
 *    duplicates are ignored and missing keys are put back in their default row
 */
public final class LayoutModel {
    private LayoutModel() {}

    public static boolean isSystem(String id) { return id.startsWith("t:"); }

    /** Can the key that is in [fromRow] (current position) go to [toRow]? baseRow = its row in the default layout. */
    public static boolean canMove(int baseRow, String id, int toRow, int rowCount) {
        int bottom = rowCount - 1;
        if (toRow < 0 || toRow >= rowCount) return false;
        if (baseRow == bottom || toRow == bottom) return baseRow == toRow;
        if (isSystem(id)) return baseRow == toRow;
        return true;
    }

    public static Map<String, Integer> baseRows(List<List<String>> base) {
        Map<String, Integer> m = new HashMap<>();
        for (int r = 0; r < base.size(); r++) for (String id : base.get(r)) m.put(id, r);
        return m;
    }

    /** Merge a saved layout with the current default so nothing can be lost or duplicated. */
    public static List<List<String>> reconcile(List<List<String>> base, List<List<String>> custom, int maxKeysPerRow) {
        if (custom == null || custom.size() != base.size()) return copy(base);
        Map<String, Integer> home = baseRows(base);
        int n = base.size();
        List<List<String>> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new ArrayList<>());
        Set<String> used = new HashSet<>();
        for (int r = 0; r < n; r++) {
            for (String id : custom.get(r)) {
                Integer h = home.get(id);
                if (h == null || used.contains(id)) continue;
                if (!canMove(h, id, r, n)) continue;
                out.get(r).add(id);
                used.add(id);
            }
        }
        for (int r = 0; r < n; r++) {
            List<String> row = base.get(r);
            for (int i = 0; i < row.size(); i++) {
                String id = row.get(i);
                if (used.contains(id)) continue;
                List<String> target = out.get(r);
                target.add(Math.min(i, target.size()), id);
                used.add(id);
            }
        }
        for (int r = 0; r < n; r++) {
            if (out.get(r).isEmpty()) return copy(base);
            if (maxKeysPerRow > 0 && r != n - 1 && out.get(r).size() > maxKeysPerRow) return copy(base);
        }
        return out;
    }

    /**
     * Move a key. toIdx is an insertion index computed on the row as it looks BEFORE the move.
     * Returns false (and changes nothing) when the move breaks a rule.
     */
    public static boolean move(List<List<String>> rows, Map<String, Integer> home, int fromRow, int fromIdx,
                               int toRow, int toIdx, int maxKeysPerRow) {
        int n = rows.size();
        if (fromRow < 0 || fromRow >= n || toRow < 0 || toRow >= n) return false;
        List<String> src = rows.get(fromRow);
        if (fromIdx < 0 || fromIdx >= src.size()) return false;
        String id = src.get(fromIdx);
        Integer h = home.get(id);
        if (h == null || !canMove(h, id, toRow, n)) return false;
        List<String> dst = rows.get(toRow);
        if (fromRow != toRow) {
            if (src.size() <= 1) return false;                         // would empty the row
            if (maxKeysPerRow > 0 && toRow != n - 1 && dst.size() + 1 > maxKeysPerRow) return false;
        }
        int idx = Math.max(0, Math.min(toIdx, dst.size()));
        if (fromRow == toRow) {
            if (idx == fromIdx || idx == fromIdx + 1) return false;    // dropped on itself
            if (idx > fromIdx) idx--;
        }
        src.remove(fromIdx);
        dst.add(Math.min(idx, dst.size()), id);
        return true;
    }

    public static List<List<String>> copy(List<List<String>> rows) {
        List<List<String>> out = new ArrayList<>();
        for (List<String> r : rows) out.add(new ArrayList<>(r));
        return out;
    }
}
