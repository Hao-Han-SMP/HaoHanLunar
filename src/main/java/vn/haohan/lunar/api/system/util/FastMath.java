package vn.haohan.lunar.core.system.util;

/**
 * High-performance trigonometric lookup table (LUT) for intensive particle and curve calculations.
 * Avoids repeated slow Math.sin / Math.cos calls during multi-point particle sequences.
 */
public final class FastMath {

    private static final int TABLE_SIZE = 4096;
    private static final int INDEX_MASK = TABLE_SIZE - 1;
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double RAD_TO_INDEX = TABLE_SIZE / TWO_PI;

    private static final double[] SIN_TABLE = new double[TABLE_SIZE];
    private static final double[] COS_TABLE = new double[TABLE_SIZE];

    static {
        for (int i = 0; i < TABLE_SIZE; i++) {
            double angle = (i * TWO_PI) / TABLE_SIZE;
            SIN_TABLE[i] = Math.sin(angle);
            COS_TABLE[i] = Math.cos(angle);
        }
    }

    private FastMath() {}

    /**
     * Fast sine calculation using lookup table.
     *
     * @param radians angle in radians
     * @return approximate sine value
     */
    public static double sin(double radians) {
        int index = ((int) Math.round(radians * RAD_TO_INDEX)) & INDEX_MASK;
        return SIN_TABLE[index];
    }

    /**
     * Fast cosine calculation using lookup table.
     *
     * @param radians angle in radians
     * @return approximate cosine value
     */
    public static double cos(double radians) {
        int index = ((int) Math.round(radians * RAD_TO_INDEX)) & INDEX_MASK;
        return COS_TABLE[index];
    }
}
