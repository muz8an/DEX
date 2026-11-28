package com.dex.util;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class QRCodeGeneratorTest {

    @Test
    void encodesVersion1AndValidatesFinderPatterns() {
        boolean[][] matrix = QRCodeGenerator.encode("http://dex.local");
        assertEquals(21, matrix.length);
        assertEquals(21, matrix[0].length);

        // Top-left 7x7 finder outer border must be dark, inner ring light, 3x3 core dark
        for (int i = 0; i < 7; i++) {
            assertTrue(matrix[0][i], "Top finder row 0 col " + i);
            assertTrue(matrix[6][i], "Top finder row 6 col " + i);
            assertTrue(matrix[i][0], "Top finder row " + i + " col 0");
            assertTrue(matrix[i][6], "Top finder row " + i + " col 6");
        }
        assertFalse(matrix[1][1]);
        assertTrue(matrix[3][3]);

        // Separators around top-left finder
        for (int i = 0; i < 8; i++) {
            assertFalse(matrix[7][i], "Separator row 7 col " + i);
            assertFalse(matrix[i][7], "Separator row " + i + " col 7");
        }

        // Dark module at (size - 8, 8)
        assertTrue(matrix[matrix.length - 8][8]);
    }

    @Test
    void encodesLargerUrlsIntoHigherVersions() {
        String localUrl = "http://192.168.100.123:31416/?token=0123456789abcdef0123456789abcdef";
        boolean[][] m = QRCodeGenerator.encode(localUrl);
        assertEquals(m.length, m[0].length);
        assertTrue(m.length >= 33 && (m.length - 17) % 4 == 0, "Expected Version >= 4 QR matrix");

        String longPayload = "https://dex-phi-bay.vercel.app/?data=" + "x".repeat(160);
        boolean[][] multiBlock = QRCodeGenerator.encode(longPayload);
        assertTrue(multiBlock.length >= 49, "Expected multi-block Version >= 8 QR matrix");
    }

    @Test
    void rendersImageWithQuietZone() {
        BufferedImage img = QRCodeGenerator.toImage("http://192.168.1.5:31416/?token=abc123", 240);
        assertEquals(240, img.getWidth());
        assertEquals(240, img.getHeight());
        // Corner pixel inside quiet zone must be pure white
        assertEquals(0xFFFFFFFF, img.getRGB(0, 0));
        assertEquals(0xFFFFFFFF, img.getRGB(239, 239));
    }

    @Test
    void rendersUniformTerminalString() {
        String terminalQr = QRCodeGenerator.toTerminalString("http://192.168.1.5:31416/?token=abc123");
        String[] lines = terminalQr.split("\\R");
        assertTrue(lines.length >= 14);
        int width = lines[0].length();
        assertTrue(width >= 29);
        for (String line : lines) {
            assertEquals(width, line.length(), "All QR terminal lines must have equal width");
        }
        assertEquals('\u2588', lines[0].charAt(0), "Top-left quiet zone corner should be filled block");
    }

    @Test
    void rejectsOversizedPayloadCleanly() {
        String oversized = "a".repeat(500);
        assertThrows(IllegalArgumentException.class, () -> QRCodeGenerator.encode(oversized));
    }
}
