package com.dex.util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Pure Java ISO/IEC 18004 QR Code encoder (Byte mode, Error Correction Level L, Versions 1-12).
 * Generates module matrices, BufferedImage assets for Swing UI, and compact half-block terminal art.
 */
public final class QRCodeGenerator {

    private static final int MIN_VERSION = 1;
    private static final int MAX_VERSION = 12;

    // Level L total codewords (data + EC) per version (index 1..12)
    private static final int[] TOTAL_CODEWORDS = {
        0, 26, 44, 70, 100, 134, 172, 196, 242, 292, 346, 404, 466
    };

    // Level L EC codewords per block (index 1..12)
    private static final int[] EC_CODEWORDS_PER_BLOCK = {
        0, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24
    };

    // Level L number of EC blocks (index 1..12)
    private static final int[] NUM_EC_BLOCKS = {
        0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4
    };

    private QRCodeGenerator() {
    }

    /**
     * Encodes the given UTF-8 text into a square boolean matrix where {@code true} represents a dark module.
     */
    public static boolean[][] encode(String text) {
        if (text == null) {
            text = "";
        }
        byte[] dataBytes = text.getBytes(StandardCharsets.UTF_8);
        int version = selectVersion(dataBytes.length);

        byte[] dataCodewords = encodeDataCodewords(dataBytes, version);
        byte[] allCodewords = addEccAndInterleave(dataCodewords, version);

        int size = version * 4 + 17;
        boolean[][] modules = new boolean[size][size];
        boolean[][] isFunction = new boolean[size][size];

        drawFunctionPatterns(modules, isFunction, version, size);
        drawCodewords(modules, isFunction, allCodewords, size);

        int bestMask = 0;
        int bestPenalty = Integer.MAX_VALUE;
        for (int mask = 0; mask < 8; mask++) {
            boolean[][] candidate = copyMatrix(modules);
            applyMask(candidate, isFunction, mask, size);
            drawFormatBits(candidate, mask, size);
            int penalty = computePenalty(candidate, size);
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                bestMask = mask;
            }
        }

        applyMask(modules, isFunction, bestMask, size);
        drawFormatBits(modules, bestMask, size);
        return modules;
    }

    /**
     * Renders a crisp high-contrast QR Code {@link BufferedImage} with a 4-module white quiet zone.
     */
    public static BufferedImage toImage(String text, int targetSizePx) {
        boolean[][] matrix = encode(text);
        int modules = matrix.length;
        int border = 4;
        int totalModules = modules + border * 2;
        int imgSize = Math.max(targetSizePx, totalModules);

        BufferedImage image = new BufferedImage(imgSize, imgSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, imgSize, imgSize);

            int scale = Math.max(1, imgSize / totalModules);
            int renderedSize = totalModules * scale;
            int offset = (imgSize - renderedSize) / 2;

            g.setColor(Color.BLACK);
            for (int r = 0; r < modules; r++) {
                for (int c = 0; c < modules; c++) {
                    if (matrix[r][c]) {
                        int x = offset + (c + border) * scale;
                        int y = offset + (r + border) * scale;
                        g.fillRect(x, y, scale, scale);
                    }
                }
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * Renders a scannable compact UTF-8 half-block QR Code string with a white quiet zone
     * suitable for dark terminal consoles.
     */
    public static String toTerminalString(String text) {
        boolean[][] matrix = encode(text);
        int n = matrix.length;
        int border = 2;
        int total = n + border * 2;

        StringBuilder sb = new StringBuilder(total * total);
        for (int r = 0; r < total; r += 2) {
            for (int c = 0; c < total; c++) {
                boolean topDark = isDarkWithBorder(matrix, r - border, c - border, n);
                boolean bottomDark = (r + 1 < total)
                    ? isDarkWithBorder(matrix, r + 1 - border, c - border, n)
                    : false;

                // Light quiet zone on dark terminals: light module = filled white block
                boolean topLight = !topDark;
                boolean bottomLight = !bottomDark;

                if (topLight && bottomLight) {
                    sb.append('\u2588'); // █
                } else if (topLight) {
                    sb.append('\u2580'); // ▀
                } else if (bottomLight) {
                    sb.append('\u2584'); // ▄
                } else {
                    sb.append(' ');
                }
            }
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }

    /**
     * ASCII-only fallback QR renderer for streams whose charset cannot encode Unicode block glyphs.
     */
    public static String toAsciiTerminalString(String text) {
        boolean[][] matrix = encode(text);
        int n = matrix.length;
        int border = 2;
        int total = n + border * 2;
        StringBuilder sb = new StringBuilder(total * total * 2);
        for (int r = 0; r < total; r++) {
            for (int c = 0; c < total; c++) {
                boolean dark = isDarkWithBorder(matrix, r - border, c - border, n);
                sb.append(dark ? "  " : "##");
            }
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }

    private static boolean isDarkWithBorder(boolean[][] matrix, int r, int c, int n) {
        if (r < 0 || r >= n || c < 0 || c >= n) {
            return false; // Quiet zone is light
        }
        return matrix[r][c];
    }

    private static int selectVersion(int byteLength) {
        for (int v = MIN_VERSION; v <= MAX_VERSION; v++) {
            int dataCodewords = getNumDataCodewords(v);
            int charCountBits = v <= 9 ? 8 : 16;
            int headerBits = 4 + charCountBits;
            int capacityBytes = (dataCodewords * 8 - headerBits) / 8;
            if (byteLength <= capacityBytes) {
                return v;
            }
        }
        throw new IllegalArgumentException("Data too long for QR Version " + MAX_VERSION + " Level L: " + byteLength + " bytes");
    }

    private static int getNumDataCodewords(int version) {
        return TOTAL_CODEWORDS[version] - EC_CODEWORDS_PER_BLOCK[version] * NUM_EC_BLOCKS[version];
    }

    private static byte[] encodeDataCodewords(byte[] dataBytes, int version) {
        int numDataCodewords = getNumDataCodewords(version);
        BitBuffer bits = new BitBuffer(numDataCodewords * 8);

        // Mode indicator: Byte mode (0100)
        bits.appendBits(0b0100, 4);
        // Character count indicator
        int countBits = version <= 9 ? 8 : 16;
        bits.appendBits(dataBytes.length, countBits);

        for (byte b : dataBytes) {
            bits.appendBits(b & 0xFF, 8);
        }

        int totalDataBits = numDataCodewords * 8;
        int terminatorLen = Math.min(4, totalDataBits - bits.bitLength);
        bits.appendBits(0, terminatorLen);

        while (bits.bitLength % 8 != 0) {
            bits.appendBits(0, 1);
        }

        int padIndex = 0;
        while (bits.bitLength < totalDataBits) {
            bits.appendBits((padIndex % 2 == 0) ? 0xEC : 0x11, 8);
            padIndex++;
        }

        return bits.toByteArray();
    }

    private static byte[] addEccAndInterleave(byte[] data, int version) {
        int numBlocks = NUM_EC_BLOCKS[version];
        int blockEccLen = EC_CODEWORDS_PER_BLOCK[version];
        int totalCodewords = TOTAL_CODEWORDS[version];

        int numShortBlocks = numBlocks - (totalCodewords % numBlocks);
        int shortBlockLen = totalCodewords / numBlocks;
        int shortDataLen = shortBlockLen - blockEccLen;

        byte[] rsDivisor = reedSolomonComputeDivisor(blockEccLen);
        byte[][] blocks = new byte[numBlocks][];

        int offset = 0;
        for (int i = 0; i < numBlocks; i++) {
            int dataLen = shortDataLen + (i < numShortBlocks ? 0 : 1);
            byte[] blockData = Arrays.copyOfRange(data, offset, offset + dataLen);
            offset += dataLen;

            byte[] ecc = reedSolomonComputeRemainder(blockData, rsDivisor);
            byte[] fullBlock = new byte[shortDataLen + 1 + blockEccLen];
            System.arraycopy(blockData, 0, fullBlock, 0, dataLen);
            System.arraycopy(ecc, 0, fullBlock, shortDataLen + 1, ecc.length);
            blocks[i] = fullBlock;
        }

        byte[] result = new byte[totalCodewords];
        int k = 0;
        // Interleave data codewords
        for (int col = 0; col <= shortDataLen; col++) {
            for (int b = 0; b < numBlocks; b++) {
                if (col < shortDataLen || b >= numShortBlocks) {
                    result[k++] = blocks[b][col];
                }
            }
        }
        // Interleave error correction codewords
        for (int col = shortDataLen + 1; col < shortDataLen + 1 + blockEccLen; col++) {
            for (int b = 0; b < numBlocks; b++) {
                result[k++] = blocks[b][col];
            }
        }
        return result;
    }

    private static void drawFunctionPatterns(boolean[][] modules, boolean[][] isFunction, int version, int size) {
        // Timing patterns
        for (int i = 0; i < size; i++) {
            boolean dark = (i % 2 == 0);
            setFunctionModule(modules, isFunction, 6, i, dark);
            setFunctionModule(modules, isFunction, i, 6, dark);
        }

        // Finder patterns + separators (top-left, top-right, bottom-left)
        drawFinderPattern(modules, isFunction, 3, 3, size);
        drawFinderPattern(modules, isFunction, 3, size - 4, size);
        drawFinderPattern(modules, isFunction, size - 4, 3, size);

        // Alignment patterns
        int[] alignCoords = getAlignmentPatternPositions(version, size);
        int numAlign = alignCoords.length;
        for (int i = 0; i < numAlign; i++) {
            for (int j = 0; j < numAlign; j++) {
                boolean nearFinder = (i == 0 && j == 0)
                    || (i == 0 && j == numAlign - 1)
                    || (i == numAlign - 1 && j == 0);
                if (!nearFinder) {
                    drawAlignmentPattern(modules, isFunction, alignCoords[i], alignCoords[j]);
                }
            }
        }

        // Dark module
        setFunctionModule(modules, isFunction, 4 * version + 9, 8, true);

        // Reserve format information areas (will be overwritten per mask)
        reserveFormatBits(isFunction, size);

        // Draw version info (versions >= 7)
        if (version >= 7) {
            drawVersionInfo(modules, isFunction, version, size);
        }
    }

    private static void drawFinderPattern(boolean[][] modules, boolean[][] isFunction, int centerRow, int centerCol, int size) {
        for (int dr = -4; dr <= 4; dr++) {
            for (int dc = -4; dc <= 4; dc++) {
                int r = centerRow + dr;
                int c = centerCol + dc;
                if (r >= 0 && r < size && c >= 0 && c < size) {
                    int dist = Math.max(Math.abs(dr), Math.abs(dc));
                    boolean dark = (dist != 2 && dist != 4);
                    setFunctionModule(modules, isFunction, r, c, dark);
                }
            }
        }
    }

    private static void drawAlignmentPattern(boolean[][] modules, boolean[][] isFunction, int centerRow, int centerCol) {
        for (int dr = -2; dr <= 2; dr++) {
            for (int dc = -2; dc <= 2; dc++) {
                int dist = Math.max(Math.abs(dr), Math.abs(dc));
                setFunctionModule(modules, isFunction, centerRow + dr, centerCol + dc, dist != 1);
            }
        }
    }

    private static int[] getAlignmentPatternPositions(int version, int size) {
        if (version == 1) {
            return new int[0];
        }
        int numAlign = version / 7 + 2;
        int step = (version == 32) ? 26 : ((version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2)) * 2;
        int[] result = new int[numAlign];
        result[0] = 6;
        for (int i = numAlign - 1, pos = size - 7; i >= 1; i--, pos -= step) {
            result[i] = pos;
        }
        return result;
    }

    private static void reserveFormatBits(boolean[][] isFunction, int size) {
        for (int i = 0; i <= 5; i++) {
            isFunction[i][8] = true;
            isFunction[8][i] = true;
        }
        isFunction[7][8] = true;
        isFunction[8][8] = true;
        isFunction[8][7] = true;
        for (int i = 0; i < 8; i++) {
            isFunction[8][size - 1 - i] = true;
        }
        for (int i = 0; i < 7; i++) {
            isFunction[size - 1 - i][8] = true;
        }
    }

    private static void drawFormatBits(boolean[][] modules, int mask, int size) {
        // EC Level L format bits = 01
        int data = (0b01 << 3) | mask;
        int rem = data;
        for (int i = 0; i < 10; i++) {
            rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        }
        int bits = ((data << 10) | rem) ^ 0x5412;

        // First copy (top-left finder edges)
        for (int i = 0; i <= 5; i++) {
            modules[i][8] = getBit(bits, i);
        }
        modules[7][8] = getBit(bits, 6);
        modules[8][8] = getBit(bits, 7);
        modules[8][7] = getBit(bits, 8);
        for (int i = 9; i < 15; i++) {
            modules[8][14 - i] = getBit(bits, i);
        }

        // Second copy (top-right and bottom-left)
        for (int i = 0; i < 8; i++) {
            modules[8][size - 1 - i] = getBit(bits, i);
        }
        for (int i = 8; i < 15; i++) {
            modules[size - 15 + i][8] = getBit(bits, i);
        }
    }

    private static void drawVersionInfo(boolean[][] modules, boolean[][] isFunction, int version, int size) {
        int rem = version;
        for (int i = 0; i < 12; i++) {
            rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        }
        int bits = (version << 12) | rem;
        for (int i = 0; i < 18; i++) {
            boolean bit = getBit(bits, i);
            int a = size - 11 + (i % 3);
            int b = i / 3;
            setFunctionModule(modules, isFunction, a, b, bit);
            setFunctionModule(modules, isFunction, b, a, bit);
        }
    }

    private static void drawCodewords(boolean[][] modules, boolean[][] isFunction, byte[] data, int size) {
        int bitIdx = 0;
        int totalBits = data.length * 8;
        boolean upward = true;

        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) {
                right = 5;
            }
            for (int step = 0; step < size; step++) {
                int r = upward ? (size - 1 - step) : step;
                for (int offset = 0; offset < 2; offset++) {
                    int c = right - offset;
                    if (!isFunction[r][c]) {
                        boolean dark = false;
                        if (bitIdx < totalBits) {
                            dark = ((data[bitIdx >>> 3] >>> (7 - (bitIdx & 7))) & 1) != 0;
                            bitIdx++;
                        }
                        modules[r][c] = dark;
                    }
                }
            }
            upward = !upward;
        }
    }

    private static void applyMask(boolean[][] modules, boolean[][] isFunction, int mask, int size) {
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (!isFunction[r][c] && shouldInvert(mask, r, c)) {
                    modules[r][c] = !modules[r][c];
                }
            }
        }
    }

    private static boolean shouldInvert(int mask, int r, int c) {
        return switch (mask) {
            case 0 -> (r + c) % 2 == 0;
            case 1 -> r % 2 == 0;
            case 2 -> c % 3 == 0;
            case 3 -> (r + c) % 3 == 0;
            case 4 -> ((r / 2) + (c / 3)) % 2 == 0;
            case 5 -> ((r * c) % 2) + ((r * c) % 3) == 0;
            case 6 -> (((r * c) % 2) + ((r * c) % 3)) % 2 == 0;
            case 7 -> (((r + c) % 2) + ((r * c) % 3)) % 2 == 0;
            default -> false;
        };
    }

    private static int computePenalty(boolean[][] m, int size) {
        int penalty = 0;

        // Rule 1 & Rule 3 along rows and columns
        for (int r = 0; r < size; r++) {
            int runLen = 0;
            boolean runColor = false;
            int patternHist = 0;

            for (int c = 0; c < size; c++) {
                boolean dark = m[r][c];
                if (c == 0 || dark != runColor) {
                    if (runLen >= 5) {
                        penalty += 3 + (runLen - 5);
                    }
                    runColor = dark;
                    runLen = 1;
                } else {
                    runLen++;
                }

                patternHist = ((patternHist << 1) | (dark ? 1 : 0)) & 0x7FF;
                if (c >= 10 && (patternHist == 0b10111010000 || patternHist == 0b00001011101)) {
                    penalty += 40;
                }
            }
            if (runLen >= 5) {
                penalty += 3 + (runLen - 5);
            }
        }

        for (int c = 0; c < size; c++) {
            int runLen = 0;
            boolean runColor = false;
            int patternHist = 0;

            for (int r = 0; r < size; r++) {
                boolean dark = m[r][c];
                if (r == 0 || dark != runColor) {
                    if (runLen >= 5) {
                        penalty += 3 + (runLen - 5);
                    }
                    runColor = dark;
                    runLen = 1;
                } else {
                    runLen++;
                }

                patternHist = ((patternHist << 1) | (dark ? 1 : 0)) & 0x7FF;
                if (r >= 10 && (patternHist == 0b10111010000 || patternHist == 0b00001011101)) {
                    penalty += 40;
                }
            }
            if (runLen >= 5) {
                penalty += 3 + (runLen - 5);
            }
        }

        // Rule 2: 2x2 blocks of same color
        for (int r = 0; r < size - 1; r++) {
            for (int c = 0; c < size - 1; c++) {
                boolean color = m[r][c];
                if (color == m[r][c + 1] && color == m[r + 1][c] && color == m[r + 1][c + 1]) {
                    penalty += 3;
                }
            }
        }

        // Rule 4: balance of dark modules
        int dark = 0;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (m[r][c]) {
                    dark++;
                }
            }
        }
        int total = size * size;
        int k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1;
        penalty += Math.max(0, k) * 10;

        return penalty;
    }

    private static byte[] reedSolomonComputeDivisor(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                result[j] = (byte) reedSolomonMultiply(result[j] & 0xFF, root);
                if (j + 1 < degree) {
                    result[j] ^= result[j + 1];
                }
            }
            root = reedSolomonMultiply(root, 0x02);
        }
        return result;
    }

    private static byte[] reedSolomonComputeRemainder(byte[] data, byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (byte b : data) {
            int factor = (b ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) {
                result[i] ^= (byte) reedSolomonMultiply(divisor[i] & 0xFF, factor);
            }
        }
        return result;
    }

    private static int reedSolomonMultiply(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    private static void setFunctionModule(boolean[][] modules, boolean[][] isFunction, int r, int c, boolean dark) {
        modules[r][c] = dark;
        isFunction[r][c] = true;
    }

    private static boolean getBit(int x, int i) {
        return ((x >>> i) & 1) != 0;
    }

    private static boolean[][] copyMatrix(boolean[][] src) {
        boolean[][] copy = new boolean[src.length][];
        for (int i = 0; i < src.length; i++) {
            copy[i] = src[i].clone();
        }
        return copy;
    }

    private static final class BitBuffer {
        private final byte[] bytes;
        private int bitLength = 0;

        BitBuffer(int maxBits) {
            this.bytes = new byte[(maxBits + 7) / 8];
        }

        void appendBits(int val, int len) {
            for (int i = len - 1; i >= 0; i--) {
                boolean bit = ((val >>> i) & 1) != 0;
                if (bit) {
                    bytes[bitLength >>> 3] |= (byte) (1 << (7 - (bitLength & 7)));
                }
                bitLength++;
            }
        }

        byte[] toByteArray() {
            return bytes;
        }
    }
}
