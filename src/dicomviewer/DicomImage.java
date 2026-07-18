//
// DicomImage.java
// Turns parsed DICOM pixel data into BufferedImage frames, applying
// window/level, modality rescale and photometric interpretation (port of
// image.js; createImageBitmap replaced by javax.imageio.ImageIO).
//

package dicomviewer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

public final class DicomImage {

    // Linear window (center / width) in rescaled value units.
    public static final class Window {
        public double center;
        public double width;
        public Window(double center, double width) { this.center = center; this.width = width; }
        public Window copy() { return new Window(center, width); }
    }

    public static final class Range {
        public final double min;
        public final double max;
        public Range(double min, double max) { this.min = min; this.max = max; }
    }

    public static final class Info {
        public int rows;
        public int columns;
        public int samplesPerPixel;
        public int bitsAllocated;
        public int bitsStored;
        public boolean pixelRepresentationSigned;
        public int planarConfiguration;
        public String photometric;
        public int numberOfFrames;
        public double rescaleSlope;
        public double rescaleIntercept;
    }

    public final Info info;
    public int frameCount = 0;
    public Window defaultWindow = new Window(128, 256);
    public Range valueRange = new Range(0, 255);
    public boolean grayscale = false;

    private final List<int[]> grayFrames = new ArrayList<>();       // stored values
    private final List<BufferedImage> renderedFrames = new ArrayList<>();
    private int storedMin = 0;
    private int storedMax = 0;

    private DicomImage(Info info) { this.info = info; }

    public boolean isColor() { return info.samplesPerPixel >= 3; }
    public boolean supportsWindowing() { return grayscale; }

    public static DicomImage decode(ParsedFile file) throws DicomImageException {
        DicomDataset ds = file.dataset;
        DicomElement pixelElement = file.get(DicomTag.PIXEL_DATA);
        if (pixelElement == null) {
            throw new DicomImageException("This DICOM object contains no image pixel data.");
        }

        int rows = ds.getIntOr(DicomTag.ROWS, 0);
        int cols = ds.getIntOr(DicomTag.COLUMNS, 0);
        if (rows <= 0 || cols <= 0) throw new DicomImageException("missing image dimensions");

        Info info = new Info();
        info.rows = rows;
        info.columns = cols;
        info.samplesPerPixel = ds.getIntOr(DicomTag.SAMPLES_PER_PIXEL, 1);
        info.bitsAllocated = ds.getIntOr(DicomTag.BITS_ALLOCATED, 16);
        info.bitsStored = ds.getIntOr(DicomTag.BITS_STORED, ds.getIntOr(DicomTag.BITS_ALLOCATED, 16));
        info.pixelRepresentationSigned = ds.getIntOr(DicomTag.PIXEL_REPRESENTATION, 0) == 1;
        info.planarConfiguration = ds.getIntOr(DicomTag.PLANAR_CONFIGURATION, 0);
        String pm = ds.getString(DicomTag.PHOTOMETRIC_INTERP);
        info.photometric = (pm != null ? pm : "MONOCHROME2").toUpperCase();
        info.numberOfFrames = Math.max(1, ds.getIntOr(DicomTag.NUMBER_OF_FRAMES, 1));
        info.rescaleSlope = ds.getDoubleOr(DicomTag.RESCALE_SLOPE, 1);
        info.rescaleIntercept = ds.getDoubleOr(DicomTag.RESCALE_INTERCEPT, 0);

        DicomImage image = new DicomImage(info);
        if (pixelElement.isEncapsulated()) {
            image.decodeEncapsulated(pixelElement, file.transferSyntax);
        } else if (image.isColor()) {
            image.decodeColorFrames(pixelElement.value);
        } else {
            image.decodeNative(pixelElement, ds);
        }
        return image;
    }

    private void decodeEncapsulated(DicomElement element, String transferSyntax)
            throws DicomImageException {
        if (!Dictionary.isNativelyDecodableJPEG(transferSyntax)) {
            throw new DicomImageException(
                Dictionary.transferSyntaxName(transferSyntax)
                    + " compression is not supported by this viewer.");
        }
        for (byte[] fragment : element.fragments) {
            try {
                BufferedImage bi = ImageIO.read(new ByteArrayInputStream(fragment));
                if (bi != null) renderedFrames.add(toIntRgb(bi));
            } catch (IOException e) {
                // skip undecodable fragment
            }
        }
        if (renderedFrames.isEmpty()) {
            throw new DicomImageException("compressed frames could not be decoded");
        }
        info.columns = renderedFrames.get(0).getWidth();
        info.rows = renderedFrames.get(0).getHeight();
        frameCount = renderedFrames.size();
        grayscale = false;
    }

    private void decodeNative(DicomElement element, DicomDataset ds) throws DicomImageException {
        byte[] pixels = element.value;
        boolean little = element.littleEndian;
        int pixelsPerFrame = info.rows * info.columns;

        int bytesPerSample = Math.max(1, info.bitsAllocated / 8);
        int bytesPerFrame = pixelsPerFrame * bytesPerSample;
        int mask = info.bitsStored >= 31 ? 0x7fffffff : ((1 << info.bitsStored) - 1);
        int signBit = 1 << (info.bitsStored - 1);

        int minV = Integer.MAX_VALUE;
        int maxV = Integer.MIN_VALUE;

        for (int f = 0; f < info.numberOfFrames; f++) {
            int base = f * bytesPerFrame;
            if ((long) base + bytesPerFrame > pixels.length) break;
            int[] frame = new int[pixelsPerFrame];
            for (int i = 0; i < pixelsPerFrame; i++) {
                int v;
                if (bytesPerSample == 1) {
                    v = pixels[base + i] & 0xFF;
                } else {
                    v = ByteReader.getUint16(pixels, base + i * 2, little);
                }
                v &= mask;
                if (info.pixelRepresentationSigned && (v & signBit) != 0) v -= (mask + 1);
                frame[i] = v;
                if (v < minV) minV = v;
                if (v > maxV) maxV = v;
            }
            grayFrames.add(frame);
        }

        if (grayFrames.isEmpty()) throw new DicomImageException("empty pixel data");
        if (minV > maxV) { minV = 0; maxV = 0; }

        frameCount = grayFrames.size();
        storedMin = minV;
        storedMax = maxV;
        grayscale = true;
        defaultWindow = computeDefaultWindow(ds, info, minV, maxV);

        double lo = minV * info.rescaleSlope + info.rescaleIntercept;
        double hi = maxV * info.rescaleSlope + info.rescaleIntercept;
        valueRange = new Range(Math.min(lo, hi), Math.max(lo, hi));
    }

    private void decodeColorFrames(byte[] pixels) throws DicomImageException {
        int pixelsPerFrame = info.rows * info.columns;
        int samples = info.samplesPerPixel;
        int bytesPerFrame = pixelsPerFrame * samples;

        for (int f = 0; f < info.numberOfFrames; f++) {
            int base = f * bytesPerFrame;
            if ((long) base + bytesPerFrame > pixels.length) break;
            BufferedImage img = new BufferedImage(info.columns, info.rows, BufferedImage.TYPE_INT_RGB);
            int[] rgb = new int[pixelsPerFrame];
            if (info.planarConfiguration == 0) {
                for (int i = 0; i < pixelsPerFrame; i++) {
                    int s = base + i * samples;
                    int r = pixels[s] & 0xFF;
                    int g = pixels[s + 1] & 0xFF;
                    int b = pixels[s + 2] & 0xFF;
                    rgb[i] = (r << 16) | (g << 8) | b;
                }
            } else {
                int plane = pixelsPerFrame;
                for (int i = 0; i < pixelsPerFrame; i++) {
                    int r = pixels[base + i] & 0xFF;
                    int g = pixels[base + plane + i] & 0xFF;
                    int b = pixels[base + 2 * plane + i] & 0xFF;
                    rgb[i] = (r << 16) | (g << 8) | b;
                }
            }
            img.setRGB(0, 0, info.columns, info.rows, rgb, 0, info.columns);
            renderedFrames.add(img);
        }
        if (renderedFrames.isEmpty()) throw new DicomImageException("empty color pixel data");
        frameCount = renderedFrames.size();
        grayscale = false;
    }

    // Returns a BufferedImage for the requested frame. The window is ignored for
    // colour / JPEG frames.
    public BufferedImage render(int frame, Window window) {
        if (frame < 0 || frame >= frameCount) return null;
        if (!grayscale) return renderedFrames.get(frame);
        return renderGray(frame, window);
    }

    private BufferedImage renderGray(int frame, Window window) {
        int[] pixels = grayFrames.get(frame);
        int[] lut = buildLut(window);
        BufferedImage img = new BufferedImage(info.columns, info.rows, BufferedImage.TYPE_INT_RGB);
        int base = storedMin;
        int count = pixels.length;
        int[] rgb = new int[count];
        for (int i = 0; i < count; i++) {
            int idx = pixels[i] - base;
            int g = (idx >= 0 && idx < lut.length) ? lut[idx] : 0;
            rgb[i] = (g << 16) | (g << 8) | g;
        }
        img.setRGB(0, 0, info.columns, info.rows, rgb, 0, info.columns);
        return img;
    }

    private int[] buildLut(Window window) {
        int n = storedMax - storedMin + 1;
        if (n <= 0) return new int[0];
        int[] lut = new int[n];
        double width = Math.max(1, window.width);
        double center = window.center;
        double low = center - 0.5 - (width - 1) / 2;
        double high = center - 0.5 + (width - 1) / 2;
        double slope = info.rescaleSlope;
        double intercept = info.rescaleIntercept;
        boolean invert = "MONOCHROME1".equals(info.photometric);
        for (int i = 0; i < n; i++) {
            int stored = storedMin + i;
            double m = stored * slope + intercept;
            double y;
            if (m <= low) y = 0;
            else if (m > high) y = 255;
            else y = ((m - (center - 0.5)) / (width - 1) + 0.5) * 255;
            int b = (int) Math.max(0, Math.min(255, Math.round(y)));
            if (invert) b = 255 - b;
            lut[i] = b;
        }
        return lut;
    }

    private static Window computeDefaultWindow(DicomDataset ds, Info info, int storedMin, int storedMax) {
        double[] c = ds.getDoubles(DicomTag.WINDOW_CENTER);
        double[] w = ds.getDoubles(DicomTag.WINDOW_WIDTH);
        if (c.length > 0 && w.length > 0 && w[0] > 0) {
            return new Window(c[0], w[0]);
        }
        double lo = storedMin * info.rescaleSlope + info.rescaleIntercept;
        double hi = storedMax * info.rescaleSlope + info.rescaleIntercept;
        return new Window((lo + hi) / 2, Math.max(1, hi - lo));
    }

    private static BufferedImage toIntRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        out.getGraphics().drawImage(src, 0, 0, null);
        return out;
    }
}
