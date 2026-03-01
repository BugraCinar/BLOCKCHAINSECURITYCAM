package com.example.blockchaincamera.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.blockchaincamera.util.Base64ImageUtil;

public class ImageProcessingServiceTest {

	private ImageProcessingService svc;

	@BeforeEach
	@SuppressWarnings("unused")
	void setup() {
		svc = new ImageProcessingService();
	}

	@Test
	@DisplayName("Identical PNG bytes should yield ~100 similarity")
	void identicalPngShouldBe100() throws Exception {
		BufferedImage img = solidImage(128, 96, Color.BLUE);
		byte[] a = toBytes(img, "png");
		byte[] b = toBytes(img, "png");
		var res = svc.compareTwoImagesBytes(a, b);
		assertTrue(res.similarityScore() >= 99.0, "Expected >=99 but was " + res.similarityScore());
	}

	@Test
	@DisplayName("Slight pixel change lowers similarity")
	void slightChangeLowersSimilarity() throws Exception {
		BufferedImage img1 = solidImage(128, 96, Color.GREEN);
		BufferedImage img2 = copy(img1);
		img2.setRGB(10, 10, Color.RED.getRGB());
		byte[] a = toBytes(img1, "png");
		byte[] b = toBytes(img2, "png");
		var res = svc.compareTwoImagesBytes(a, b);
		assertTrue(res.similarityScore() < 100.0, "Expected <100 but was " + res.similarityScore());
	}

	@Test
	@DisplayName("Rescaled same content remains highly similar")
	void rescaledStillHighSimilarity() throws Exception {
		BufferedImage img = gradientImage(200, 150);
		byte[] orig = toBytes(img, "png");
		BufferedImage scaled = scale(img, 300, 225);
		byte[] resized = toBytes(scaled, "png");
		var res = svc.compareTwoImagesBytes(orig, resized);
		assertTrue(res.similarityScore() >= 95.0, "Expected >=95 but was " + res.similarityScore());
	}

	@Test
	@DisplayName("Base64ImageUtil DEFLATE round-trip returns original bytes")
	void base64DeflateRoundTrip() {
		byte[] data = new byte[256];
		for (int i = 0; i < data.length; i++) data[i] = (byte) i;
		String enc = Base64ImageUtil.encode(data, Base64ImageUtil.Encoding.DEFLATE, true);
		assertNotNull(enc);
		// ensure no padding "=" exists when unpadded
		assertFalse(enc.endsWith("="));
		byte[] back = Base64ImageUtil.decode(enc, Base64ImageUtil.Encoding.DEFLATE);
		assertArrayEquals(data, back);
	}

	@Test
	@DisplayName("Plain Base64 padding handling works")
	void base64PaddingHandling() {
		byte[] data = new byte[] {1,2,3,4,5,6,7};
		String b64 = Base64.getEncoder().encodeToString(data); // with padding
		byte[] back = Base64ImageUtil.decode(b64.substring(0, b64.length()-2), Base64ImageUtil.Encoding.NONE); // drop padding
		assertArrayEquals(data, back);
	}

	// Helpers
	private static BufferedImage solidImage(int w, int h, Color color) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		try {
			g.setColor(color);
			g.fillRect(0, 0, w, h);
		} finally { g.dispose(); }
		return img;
	}

	private static BufferedImage gradientImage(int w, int h) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		try {
			for (int y = 0; y < h; y++) {
				float t = y / (float) (h - 1);
				g.setColor(new Color(t, 0.3f, 1f - t));
				g.drawLine(0, y, w, y);
			}
		} finally { g.dispose(); }
		return img;
	}

	private static BufferedImage copy(BufferedImage src) {
		BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = dst.createGraphics();
		try { g.drawImage(src, 0, 0, null); } finally { g.dispose(); }
		return dst;
	}

	private static BufferedImage scale(BufferedImage src, int w, int h) {
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = dst.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(src, 0, 0, w, h, null);
		} finally { g.dispose(); }
		return dst;
	}

	private static byte[] toBytes(BufferedImage img, String format) throws Exception {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			boolean ok = ImageIO.write(img, format, baos);
			if (!ok) throw new IllegalStateException("ImageIO cannot write format: " + format);
			return baos.toByteArray();
		}
	}
}
