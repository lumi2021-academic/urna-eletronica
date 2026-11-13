package com.lumi2021.urna.view;

import java.nio.charset.StandardCharsets;

public final class QrCode {

    private static final int MAX_VERSION = 5;
    private static final int[] DATA_CODEWORDS = {19, 34, 55, 80, 108};
    private static final int[] EC_CODEWORDS = {7, 10, 15, 20, 26};
    private static final int[][] ALIGNMENT = {{}, {6, 18}, {6, 22}, {6, 26}, {6, 30}};
    private static final int FORMAT_BITS_L = 1;

    private final int size;
    private final boolean[][] modules;
    private final boolean[][] isFunction;

    private QrCode(int version, byte[] codewords) {
        size = 17 + 4 * version;
        modules = new boolean[size][size];
        isFunction = new boolean[size][size];

        drawFunctionPatterns(version);
        drawCodewords(codewords);

        int bestMask = 0;
        int bestPenalty = Integer.MAX_VALUE;

        for (int mask = 0; mask < 8; mask++) {
            applyMask(mask);
            drawFormatBits(mask);
            int penalty = penalty();
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                bestMask = mask;
            }
            applyMask(mask);
        }

        applyMask(bestMask);
        drawFormatBits(bestMask);
    }

    public static boolean[][] encode(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);

        int version = 0;
        for (int v = 1; v <= MAX_VERSION; v++) {
            if (4 + 8 + 8 * data.length <= DATA_CODEWORDS[v - 1] * 8) {
                version = v;
                break;
            }
        }
        if (version == 0) throw new IllegalArgumentException("Texto grande demais para o QR Code");

        int dataCw = DATA_CODEWORDS[version - 1];
        int ecCw = EC_CODEWORDS[version - 1];

        byte[] buffer = new byte[dataCw];
        int bitLength = 0;
        bitLength = appendBits(buffer, bitLength, 0b0100, 4);
        bitLength = appendBits(buffer, bitLength, data.length, 8);
        for (byte b : data) bitLength = appendBits(buffer, bitLength, b & 0xFF, 8);

        for (int i = (bitLength + 7) / 8, pad = 0xEC; i < dataCw; i++, pad ^= 0xEC ^ 0x11) {
            buffer[i] = (byte) pad;
        }

        byte[] ec = reedSolomonRemainder(buffer, reedSolomonDivisor(ecCw));
        byte[] all = new byte[dataCw + ecCw];
        System.arraycopy(buffer, 0, all, 0, dataCw);
        System.arraycopy(ec, 0, all, dataCw, ecCw);

        return new QrCode(version, all).modules;
    }

    private static int appendBits(byte[] buffer, int bitLength, int value, int count) {
        for (int i = count - 1; i >= 0; i--, bitLength++) {
            if (((value >>> i) & 1) != 0) buffer[bitLength >>> 3] |= (byte) (0x80 >>> (bitLength & 7));
        }
        return bitLength;
    }

    // ---------- padrões de função ----------

    private void drawFunctionPatterns(int version) {
        for (int i = 0; i < size; i++) {
            setFunction(6, i, i % 2 == 0);
            setFunction(i, 6, i % 2 == 0);
        }

        drawFinder(3, 3);
        drawFinder(size - 4, 3);
        drawFinder(3, size - 4);

        int[] pos = ALIGNMENT[version - 1];
        for (int i = 0; i < pos.length; i++) {
            for (int j = 0; j < pos.length; j++) {
                boolean overlapsFinder =
                        (i == 0 && j == 0)
                        || (i == 0 && j == pos.length - 1)
                        || (i == pos.length - 1 && j == 0);
                if (!overlapsFinder) drawAlignment(pos[i], pos[j]);
            }
        }

        drawFormatBits(0);
    }

    private void drawFinder(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int x = cx + dx, y = cy + dy;
                if (x < 0 || x >= size || y < 0 || y >= size) continue;
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                setFunction(x, y, dist != 2 && dist != 4);
            }
        }
    }

    private void drawAlignment(int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                setFunction(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
            }
        }
    }

    private void drawFormatBits(int mask) {
        int data = FORMAT_BITS_L << 3 | mask;
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = (data << 10 | rem) ^ 0x5412;

        for (int i = 0; i <= 5; i++) setFunction(8, i, bit(bits, i));
        setFunction(8, 7, bit(bits, 6));
        setFunction(8, 8, bit(bits, 7));
        setFunction(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) setFunction(14 - i, 8, bit(bits, i));

        for (int i = 0; i < 8; i++) setFunction(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) setFunction(8, size - 15 + i, bit(bits, i));
        setFunction(8, size - 8, true);
    }

    private void setFunction(int x, int y, boolean dark) {
        modules[y][x] = dark;
        isFunction[y][x] = true;
    }

    private static boolean bit(int value, int i) {
        return ((value >>> i) & 1) != 0;
    }

    // ---------- dados ----------

    private void drawCodewords(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!isFunction[y][x] && i < data.length * 8) {
                        modules[y][x] = bit(data[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    private void applyMask(int mask) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean invert = switch (mask) {
                    case 0 -> (x + y) % 2 == 0;
                    case 1 -> y % 2 == 0;
                    case 2 -> x % 3 == 0;
                    case 3 -> (x + y) % 3 == 0;
                    case 4 -> (x / 3 + y / 2) % 2 == 0;
                    case 5 -> x * y % 2 + x * y % 3 == 0;
                    case 6 -> (x * y % 2 + x * y % 3) % 2 == 0;
                    default -> ((x + y) % 2 + x * y % 3) % 2 == 0;
                };
                if (!isFunction[y][x] && invert) modules[y][x] = !modules[y][x];
            }
        }
    }

    private int penalty() {
        int result = 0;

        for (int pass = 0; pass < 2; pass++) {
            for (int a = 0; a < size; a++) {
                int run = 1;
                for (int b = 1; b < size; b++) {
                    if (get(pass, a, b) == get(pass, a, b - 1)) {
                        run++;
                        if (run == 5) result += 3;
                        else if (run > 5) result++;
                    }
                    else {
                        run = 1;
                    }
                }
                for (int b = 0; b + 11 <= size; b++) {
                    if (matches(pass, a, b, new int[]{1, 0, 1, 1, 1, 0, 1, 0, 0, 0, 0})
                            || matches(pass, a, b, new int[]{0, 0, 0, 0, 1, 0, 1, 1, 1, 0, 1})) {
                        result += 40;
                    }
                }
            }
        }

        // N2: blocos 2x2
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean c = modules[y][x];
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) result += 3;
            }
        }

        int dark = 0;
        for (boolean[] row : modules) for (boolean m : row) if (m) dark++;
        int total = size * size;
        int k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1;
        return result + k * 10;
    }

    private boolean get(int pass, int a, int b) {
        return pass == 0 ? modules[a][b] : modules[b][a];
    }

    private boolean matches(int pass, int a, int start, int[] pattern) {
        for (int i = 0; i < pattern.length; i++) {
            if (get(pass, a, start + i) != (pattern[i] == 1)) return false;
        }
        return true;
    }

    // ---------- Reed-Solomon (GF(256), polinômio 0x11D) ----------

    private static byte[] reedSolomonDivisor(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                result[j] = (byte) multiply(result[j] & 0xFF, root);
                if (j + 1 < degree) result[j] ^= result[j + 1];
            }
            root = multiply(root, 0x02);
        }
        return result;
    }

    private static byte[] reedSolomonRemainder(byte[] data, byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (byte b : data) {
            int factor = (b ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) {
                result[i] ^= (byte) multiply(divisor[i] & 0xFF, factor);
            }
        }
        return result;
    }

    private static int multiply(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }
}
