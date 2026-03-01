package com.example.blockchaincamera.service;

// imports - add these if missing
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.RasterFormatException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class ImageProcessingService {
    
    private static final String PERFECT_REFERENCE = "static/images/perfect_kasikci.jpg";
    private volatile byte[] referenceImagePPM = null;
    private volatile boolean referenceLoaded = false;
    
    // PPM P6 Configuration
    private static final int STANDARD_WIDTH = 256;
    private static final int STANDARD_HEIGHT = 256;
    private static final int MAX_COLOR_VALUE = 255;

    // Tunables (can be overridden via application.properties)
    @Value("${app.image.similarity.ssimWeight:0.4}")
    private double ssimWeight;

    @Value("${app.image.similarity.dhashWeight:0.6}")
    private double dhashWeight;

    @Value("${app.image.similarity.perfectDhash:0.985}")
    private double perfectDhash; // if dHash >= this, treat as near-identical

    @Value("${app.image.similarity.mode:weighted}")
    private String mode; // weighted | max

    @Value("${app.image.similarity.cropTolerant:false}")
    private boolean cropTolerant; // enable scan over small crops/offsets for dHash
    
    public ImageProcessingService() {
        System.out.println("ImageProcessingService initialized - Using PPM P6 format");
        System.out.println("Reference image: " + PERFECT_REFERENCE);
        loadReferenceImage();
    }

    /**
     * Main method: Compare current image with previous
     */
    public ImageComparisonResult compareTwoImagesBytes(byte[] previous, byte[] current) {
        try {
            if (previous == null || current == null || previous.length == 0 || current.length == 0) {
                return compareImageBytes(current);
            }

            // Convert both images to PPM P6 format
            byte[] previousPPM = convertToPPM(previous);
            byte[] currentPPM = convertToPPM(current);

            if (previousPPM == null || currentPPM == null) {
                return new ImageComparisonResult(0.0, "Image conversion failed");
            }

            // Exact byte comparison first (will be true if same image)
            if (Arrays.equals(previousPPM, currentPPM)) {
                return new ImageComparisonResult(100.0, determineAnalysisResult(100.0));
            }

            // Calculate similarity using pixel-by-pixel comparison
            double similarity = calculatePPMSimilarity(previousPPM, currentPPM);
            
            System.out.printf("PPM Comparison: Previous(len=%d) vs Current(len=%d) = %.2f%%\n", 
                previousPPM.length, currentPPM.length, similarity);
            
            return new ImageComparisonResult(similarity, determineAnalysisResult(similarity));

        } catch (Exception e) {
            System.err.println("Error in compareTwoImagesBytes: " + e.getMessage());
            return new ImageComparisonResult(0.0, "Comparison failed: " + e.getMessage());
        }
    }

    // Explicit component logger for runtime debugging
    public void debugSimilarityComponents(byte[] imgA, byte[] imgB) {
        try {
            if (imgA == null || imgB == null) {
                System.out.println("[SIM-DEBUG] One of the images is null; skipping component log.");
                return;
            }
            BufferedImage a = normalizeToBufferedImage(imgA);
            BufferedImage b = normalizeToBufferedImage(imgB);
            if (a == null || b == null) {
                System.out.println("[SIM-DEBUG] Failed to decode images for component log.");
                return;
            }
            if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
                a = resize(a, 256, 256);
                b = resize(b, 256, 256);
            }
            double ssim = computeSSIM(a, b);
            double dh   = computeDHashSimilarity(a, b);
            System.out.printf("[SIM-DEBUG] Components -> SSIM: %.3f, dHash: %.3f\n", ssim, dh);
        } catch (Exception ex) {
            System.out.println("[SIM-DEBUG] Error logging components: " + ex.getMessage());
        }
    }

    /**
     * Compare current image with reference
     */
    public ImageComparisonResult compareImageBytes(byte[] current) {
        try {
            if (current == null || current.length == 0) {
                return new ImageComparisonResult(50.0, determineAnalysisResult(50.0));
            }

            ensureReferenceLoaded();
            
            if (referenceImagePPM == null) {
                return new ImageComparisonResult(50.0, "Reference image not available");
            }

            // Convert current image to PPM
            byte[] currentPPM = convertToPPM(current);
            if (currentPPM == null) {
                return new ImageComparisonResult(0.0, "Current image conversion failed");
            }

            // Calculate similarity
            double similarity = calculatePPMSimilarity(referenceImagePPM, currentPPM);
            
            System.out.printf("PPM Reference Comparison: Current(len=%d) vs Reference(len=%d) = %.2f%%\n", 
                currentPPM.length, referenceImagePPM.length, similarity);
            
            return new ImageComparisonResult(similarity, determineAnalysisResult(similarity));

        } catch (Exception e) {
            System.err.println("Error in compareImageBytes: " + e.getMessage());
            return new ImageComparisonResult(50.0, "Reference comparison failed: " + e.getMessage());
        }
    }

    /**
     * Convert any image format to PPM P6 (binary) format
     */
    private byte[] convertToPPM(byte[] imageBytes) {
        try {
            // Read image from bytes
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                return null;
            }

            // Resize to standard dimensions
            BufferedImage resized = resizeImage(image);
            
            // Convert to PPM P6 format
            return convertBufferedImageToPPM(resized);

        } catch (Exception e) {
            System.err.println("Error converting to PPM: " + e.getMessage());
            return null;
        }
    }

    /**
     * Convert BufferedImage to PPM P6 binary format
     */
    private byte[] convertBufferedImageToPPM(BufferedImage image) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        // PPM P6 Header
        String header = "P6\n" + width + " " + height + "\n" + MAX_COLOR_VALUE + "\n";
        output.write(header.getBytes(StandardCharsets.US_ASCII));
        
        // Binary pixel data (RGB)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                
                // Extract RGB components
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                
                // Write RGB bytes directly
                output.write(red);
                output.write(green);
                output.write(blue);
            }
        }
        
        return output.toByteArray();
    }

// Replace old implementation with a robust hybrid metric
private double calculatePPMSimilarity(byte[] img1, byte[] img2) {
    try {
        BufferedImage a = normalizeToBufferedImage(img1);
        BufferedImage b = normalizeToBufferedImage(img2);
        if (a == null || b == null) {
            return 0.0;
        }
        // Normalize to same size only if needed to avoid double smoothing
        int target = 256;
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            a = resize(a, target, target);
            b = resize(b, target, target);
        }

        double ssim = computeSSIM(a, b);             // 0..1
        double dhSim = computeDHashSimilarity(a, b); // 0..1

        // Optional: try to mitigate cropping by scanning small center crops and offsets on b
        if (cropTolerant) {
            double bestCropDh = cropTolerantDhashMax(a, b);
            if (bestCropDh > dhSim) {
                System.out.printf("[SIM-DEBUG] Crop-tolerant dHash improved: base=%.3f -> max=%.3f\n", dhSim, bestCropDh);
                dhSim = bestCropDh;
            }
        }
        System.out.printf("Similarity components -> SSIM: %.3f, dHash: %.3f\n", ssim, dhSim);

        // If dHash is almost perfect, clamp to 1.0 (handles JPEG re-encode/noise cases)
        if (dhSim >= perfectDhash) {
            return 100.0;
        }

        double hybrid;
        if ("max".equalsIgnoreCase(mode)) {
            hybrid = Math.max(ssim, dhSim);
        } else {
            // normalize weights if misconfigured
            double sum = ssimWeight + dhashWeight;
            double ws = sum == 0 ? 0.5 : (ssimWeight / sum);
            double wd = sum == 0 ? 0.5 : (dhashWeight / sum);
            hybrid = ws * ssim + wd * dhSim;
        }
        // Return as 0..100
        return Math.max(0.0, Math.min(100.0, hybrid * 100.0));
    } catch (Exception e) {
        // As a last resort, try dHash via ImageIO only
        try {
            BufferedImage a = readWithImageIO(img1);
            BufferedImage b = readWithImageIO(img2);
            if (a == null || b == null) return 0.0;
            a = resize(a, 256, 256);
            b = resize(b, 256, 256);
            return computeDHashSimilarity(a, b) * 100.0;
        } catch (Exception ignore) {
            return 0.0;
        }
    }
}

// Decode to BufferedImage (PPM first, else ImageIO fallback)
private BufferedImage normalizeToBufferedImage(byte[] data) throws IOException {
    BufferedImage ppm = tryDecodePPM(data);
    if (ppm != null) return toSRGB(ppm);
    BufferedImage generic = readWithImageIO(data);
    if (generic != null) return toSRGB(generic);
    return null;
}

private BufferedImage readWithImageIO(byte[] data) throws IOException {
    try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
        return ImageIO.read(in);
    }
}

private BufferedImage toSRGB(BufferedImage src) {
    if (src == null) return null;
    // Ensure we operate in sRGB
    if (src.getColorModel().getColorSpace().isCS_sRGB()) return src;
    ColorConvertOp op = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_sRGB), null);
    BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
    op.filter(src, dst);
    return dst;
}

private BufferedImage resize(BufferedImage src, int w, int h) {
    BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = dst.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.drawImage(src, 0, 0, w, h, null);
    g.dispose();
    return dst;
}

// --- SSIM (global) over grayscale ---
private double computeSSIM(BufferedImage a, BufferedImage b) {
    int w = a.getWidth();
    int h = a.getHeight();
    if (w != b.getWidth() || h != b.getHeight()) return 0.0;

    // Convert to grayscale double arrays
    double[] ga = new double[w * h];
    double[] gb = new double[w * h];
    toGrayscale(a, ga);
    toGrayscale(b, gb);

    // Means
    double meanA = 0, meanB = 0;
    for (int i = 0; i < ga.length; i++) {
        meanA += ga[i];
        meanB += gb[i];
    }
    int n = ga.length;
    meanA /= n;
    meanB /= n;

    // Variances and covariance
    double varA = 0, varB = 0, cov = 0;
    for (int i = 0; i < n; i++) {
        double da = ga[i] - meanA;
        double db = gb[i] - meanB;
        varA += da * da;
        varB += db * db;
        cov  += da * db;
    }
    varA /= (n - 1);
    varB /= (n - 1);
    cov  /= (n - 1);

    // SSIM constants
    double L = 255.0;      // dynamic range
    double k1 = 0.01, k2 = 0.03;
    double C1 = (k1 * L) * (k1 * L);
    double C2 = (k2 * L) * (k2 * L);

    double numerator   = (2 * meanA * meanB + C1) * (2 * cov + C2);
    double denominator = (meanA * meanA + meanB * meanB + C1) * (varA + varB + C2);
    if (denominator == 0) return 1.0;
    double ssim = numerator / denominator;
    // Clamp to [0,1]
    if (Double.isNaN(ssim)) return 0.0;
    return Math.max(0.0, Math.min(1.0, ssim));
}

private void toGrayscale(BufferedImage img, double[] out) {
    int i = 0;
    for (int y = 0; y < img.getHeight(); y++) {
        for (int x = 0; x < img.getWidth(); x++) {
            int rgb = img.getRGB(x, y);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = (rgb) & 0xFF;
            // Luma approximation
            out[i++] = 0.299 * r + 0.587 * g + 0.114 * b;
        }
    }
}

// --- dHash (64-bit) and similarity ---
private double computeDHashSimilarity(BufferedImage a, BufferedImage b) {
    long ha = dHash64(a);
    long hb = dHash64(b);
    int dist = Long.bitCount(ha ^ hb); // 0..64
    return 1.0 - (dist / 64.0);
}

private long dHash64(BufferedImage src) {
    // Resize to 9x8 grayscale
    BufferedImage small = resize(src, 9, 8);
    int[] gray = new int[9 * 8];
    int idx = 0;
    for (int y = 0; y < 8; y++) {
        for (int x = 0; x < 9; x++) {
            int rgb = small.getRGB(x, y);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = (rgb) & 0xFF;
            gray[idx++] = (int)Math.round(0.299 * r + 0.587 * g + 0.114 * b);
        }
    }
    // Build 64-bit hash by comparing adjacent pixels horizontally
    long hash = 0L;
    int bit = 0;
    for (int y = 0; y < 8; y++) {
        for (int x = 0; x < 8; x++) {
            int left  = gray[y * 9 + x];
            int right = gray[y * 9 + x + 1];
            if (left > right) {
                hash |= (1L << bit);
            }
            bit++;
        }
    }
    return hash;
}

// --- Crop-tolerant dHash search: test small scales and offsets, take max ---
private double cropTolerantDhashMax(BufferedImage refA, BufferedImage imgB) {
    // Scales and offsets kept small for performance
    double[] scales = new double[]{0.90, 0.95, 1.00};
    double[] shifts = new double[]{-0.06, 0.0, 0.06}; // fraction of width/height
    double best = computeDHashSimilarity(refA, imgB);
    int targetW = refA.getWidth();
    int targetH = refA.getHeight();
    for (double s : scales) {
        for (double dx : shifts) {
            for (double dy : shifts) {
                BufferedImage crop = safeCenterCrop(imgB, s, dx, dy);
                if (crop == null) continue;
                BufferedImage resized = resize(crop, targetW, targetH);
                double d = computeDHashSimilarity(refA, resized);
                if (d > best) best = d;
            }
        }
    }
    return best;
}

private BufferedImage safeCenterCrop(BufferedImage src, double scale, double dxFrac, double dyFrac) {
    if (scale <= 0.0 || scale > 1.0) return null;
    int w = src.getWidth();
    int h = src.getHeight();
    int cw = Math.max(1, (int)Math.round(w * scale));
    int ch = Math.max(1, (int)Math.round(h * scale));
    int baseX = (w - cw) / 2;
    int baseY = (h - ch) / 2;
    int x = baseX + (int)Math.round(dxFrac * w);
    int y = baseY + (int)Math.round(dyFrac * h);
    // Clamp inside bounds
    x = Math.max(0, Math.min(x, w - cw));
    y = Math.max(0, Math.min(y, h - ch));
    try {
        return src.getSubimage(x, y, cw, ch);
    } catch (RasterFormatException | IllegalArgumentException e) {
        return null;
    }
}

// --- PPM decoder (P6 and basic P3), with comments and MaxVal support ---
private BufferedImage tryDecodePPM(byte[] data) {
    if (data == null || data.length < 2) return null;
    // Quick check
    if (!(data[0] == 'P' && (data[1] == '6' || data[1] == '3'))) {
        return null;
    }
    int[] pos = new int[]{0};
    String magic = readToken(data, pos);
    if (!"P6".equals(magic) && !"P3".equals(magic)) {
        return null;
    }
    String wTok = readToken(data, pos);
    String hTok = readToken(data, pos);
    String maxTok = readToken(data, pos);
    if (wTok == null || hTok == null || maxTok == null) return null;

    int w = Integer.parseInt(wTok);
    int h = Integer.parseInt(hTok);
    int maxVal = Integer.parseInt(maxTok);
    if (w <= 0 || h <= 0 || maxVal <= 0) return null;

    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

    if ("P6".equals(magic)) {
        // After header, there is a single whitespace before binary data
        if (pos[0] < data.length && isWhitespace(data[pos[0]])) pos[0]++;
        int expected = w * h * 3;
        if (data.length - pos[0] < expected) return null;
        int i = pos[0];
        int p = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = data[i++] & 0xFF;
                int g = data[i++] & 0xFF;
                int b = data[i++] & 0xFF;
                if (maxVal != 255) {
                    r = (int)Math.round((r / (double)maxVal) * 255.0);
                    g = (int)Math.round((g / (double)maxVal) * 255.0);
                    b = (int)Math.round((b / (double)maxVal) * 255.0);
                }
                int rgb = (r << 16) | (g << 8) | b;
                img.setRGB(x, y, rgb);
                p += 3;
            }
        }
        return img;
    } else { // P3 ASCII
        int x = 0, y = 0;
        while (y < h) {
            String rs = readToken(data, pos);
            String gs = readToken(data, pos);
            String bs = readToken(data, pos);
            if (rs == null || gs == null || bs == null) return null;
            int r = Integer.parseInt(rs);
            int g = Integer.parseInt(gs);
            int b = Integer.parseInt(bs);
            if (maxVal != 255) {
                r = (int)Math.round((r / (double)maxVal) * 255.0);
                g = (int)Math.round((g / (double)maxVal) * 255.0);
                b = (int)Math.round((b / (double)maxVal) * 255.0);
            }
            int rgb = (r << 16) | (g << 8) | b;
            img.setRGB(x, y, rgb);
            x++;
            if (x == w) { x = 0; y++; }
        }
        return img;
    }
}

// Read next token, skipping whitespace and # comments
private String readToken(byte[] data, int[] posRef) {
    int i = posRef[0];
    int n = data.length;

    // skip whitespace and comments
    while (i < n) {
        if (data[i] == '#') {
            // skip until end of line
            while (i < n && data[i] != '\n' && data[i] != '\r') i++;
        } else if (isWhitespace(data[i])) {
            i++;
        } else break;
    }
    if (i >= n) { posRef[0] = i; return null; }

    int start = i;
    while (i < n && !isWhitespace(data[i]) && data[i] != '#') i++;

    String token = new String(data, start, i - start, StandardCharsets.US_ASCII);
    posRef[0] = i;
    return token;
}

private boolean isWhitespace(byte b) {
    return b == ' ' || b == '\t' || b == '\n' || b == '\r' || b == '\f';
}

    /**
     * Resize image to standard dimensions
     */
    private BufferedImage resizeImage(BufferedImage original) {
        BufferedImage resized = new BufferedImage(ImageProcessingService.STANDARD_WIDTH, ImageProcessingService.STANDARD_HEIGHT, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2d = resized.createGraphics();
        try {
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, 
                                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, 
                                java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            g2d.drawImage(original, 0, 0, ImageProcessingService.STANDARD_WIDTH, ImageProcessingService.STANDARD_HEIGHT, null);
        } finally {
            g2d.dispose();
        }
        return resized;
    }

    /**
     * Load reference image and convert to PPM
     */
    private void loadReferenceImage() {
        try {
            BufferedImage refImage;
            ClassPathResource res = new ClassPathResource(PERFECT_REFERENCE);
            if (res.exists()) {
                try (var is = res.getInputStream()) {
                    refImage = ImageIO.read(is);
                }
            } else {
                refImage = ImageIO.read(java.nio.file.Path.of(PERFECT_REFERENCE).toFile());
            }
            
            if (refImage == null) {
                throw new IllegalStateException("Cannot load reference image: " + PERFECT_REFERENCE);
            }
            
            // Convert to PPM
            BufferedImage resized = resizeImage(refImage);
            referenceImagePPM = convertBufferedImageToPPM(resized);
            referenceLoaded = true;
            
            System.out.printf("Reference image loaded and converted to PPM: %d bytes\n", 
                referenceImagePPM.length);
            
        } catch (Exception e) {
            System.err.println("Failed to load reference image: " + e.getMessage());
            referenceImagePPM = null;
            referenceLoaded = false;
        }
    }

    /**
     * Ensure reference image is loaded
     */
    private void ensureReferenceLoaded() {
        if (!referenceLoaded || referenceImagePPM == null) {
            synchronized (this) {
                if (!referenceLoaded || referenceImagePPM == null) {
                    loadReferenceImage();
                }
            }
        }
    }

    /**
     * Determine analysis result based on similarity score
     */
    private String determineAnalysisResult(double similarity) {
        if (similarity >= 95.0) {
            return "Perfect Diamond - Highest Quality";
        } else if (similarity >= 85.0) {
            return "High Quality Diamond - Minor Variations";
        } else if (similarity >= 70.0) {
            return "Good Quality Diamond - Some Defects";
        } else if (similarity >= 50.0) {
            return "Moderate Quality Diamond - Visible Defects";
        } else {
            return "Low Quality Diamond - Significant Issues";
        }
    }

    /**
     * Test method to select random image
     */
    public String selectRandomTestImage() {
        String[] testImages = {
            "static/images/sample_perfect_kasikci.jpg",
            "static/images/defect1_kasikci.jpg", 
            "static/images/defect2_kasikci.jpg"
        };
        return testImages[new java.util.Random().nextInt(testImages.length)];
    }

    /**
     * Result record
     */
    public record ImageComparisonResult(double similarityScore, String analysisResult) {}

}