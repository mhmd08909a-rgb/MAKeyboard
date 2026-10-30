package com.ma.keyboard;

/** Pure size math (no Android dependencies, unit-tested on the JVM). All values are pixels. */
public final class FitMath {
    private FitMath() {}

    /**
     * Row height the keyboard really uses: what the user asked for, shrunk only when all rows
     * would not fit in [maxTotal] (0 = no limit). Never returns less than needed to show every row.
     */
    public static float fitRowHeight(int rows, float wanted, float maxTotal, float padding, float minRow) {
        if (rows <= 0) return wanted;
        if (maxTotal <= 0f) return wanted;
        float avail = Math.max(1f, maxTotal - padding);
        float rh = Math.min(wanted, avail / rows);
        if (rh < minRow && minRow * rows <= avail) rh = minRow;
        return rh;
    }

    /** Clamp a gap so a key can never shrink below (1 - 2*maxFrac) of its cell. */
    public static float clampGap(float gap, float cell, float maxFrac) {
        return Math.max(0f, Math.min(gap, cell * maxFrac));
    }

    /**
     * Calculator panel: returns {panelHeight, rowHeight}. The panel tries to match the keyboard
     * height, grows when the rows need more room, but never exceeds maxPanel; then rows share
     * whatever is left so the bottom row is always inside the panel.
     */
    public static float[] calcPanel(float keyboardHeight, float fixedTop, int rows, float preferredRow,
                                    float minRow, float maxPanel) {
        float need = fixedTop + rows * minRow;
        float panel = Math.max(keyboardHeight, need);
        if (maxPanel > 0f) panel = Math.min(panel, maxPanel);
        float rowH = (panel - fixedTop) / rows;
        rowH = Math.min(rowH, Math.max(preferredRow, minRow) * 1.25f);
        rowH = Math.max(1f, rowH);
        panel = Math.min(panel, fixedTop + rowH * rows);
        return new float[]{panel, rowH};
    }
}
