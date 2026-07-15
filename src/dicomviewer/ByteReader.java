//
// ByteReader.java
// Endian-aware cursor over a byte[], used by the parser (port of the web app's
// byte-reader.js).
//

package dicomviewer;

import java.nio.charset.Charset;

public final class ByteReader {
    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");

    private final byte[] bytes;
    private int offset;
    public boolean littleEndian;

    public ByteReader(byte[] bytes, boolean littleEndian) {
        this.bytes = bytes;
        this.littleEndian = littleEndian;
        this.offset = 0;
    }

    public int length() { return bytes.length; }
    public int offset() { return offset; }
    public boolean isAtEnd() { return offset >= bytes.length; }
    public int remaining() { return Math.max(0, bytes.length - offset); }

    public void seek(int o) { offset = o; }
    public void skip(int n) { offset += n; }
    public boolean canRead(int n) { return n >= 0 && (long) offset + n <= bytes.length; }

    // Returns -1 when there are not enough bytes to read.
    public int readUint8() {
        if (!canRead(1)) return -1;
        int v = bytes[offset] & 0xFF;
        offset += 1;
        return v;
    }

    // Returns -1 when there are not enough bytes to read.
    public int readUint16() {
        if (!canRead(2)) return -1;
        int v = getUint16(bytes, offset, littleEndian);
        offset += 2;
        return v;
    }

    // Returns -1L when there are not enough bytes to read. Valid values are
    // 0 .. 0xFFFFFFFF and never collide with the -1 sentinel.
    public long readUint32() {
        if (!canRead(4)) return -1L;
        long v = getUint32(bytes, offset, littleEndian);
        offset += 4;
        return v;
    }

    // Returns a copy of n bytes advancing the cursor, or null at end.
    public byte[] readBytes(int n) {
        if (!canRead(n)) return null;
        byte[] out = new byte[n];
        System.arraycopy(bytes, offset, out, 0, n);
        offset += n;
        return out;
    }

    public DicomTag readTag() {
        int group = readUint16();
        int element = readUint16();
        if (group < 0 || element < 0) return null;
        return new DicomTag(group, element);
    }

    public String readString(int n) {
        byte[] b = readBytes(n);
        if (b == null) return null;
        return decodeString(b, 0, b.length);
    }

    // ---------------------------------------------------------------- statics

    public static String decodeString(byte[] b) {
        return decodeString(b, 0, b.length);
    }

    // Decodes ISO-8859-1 text and trims trailing NUL/space padding plus any
    // leading NULs, matching the JavaScript decoder.
    public static String decodeString(byte[] b, int off, int len) {
        String s = new String(b, off, len, LATIN1);
        int end = s.length();
        while (end > 0) {
            char c = s.charAt(end - 1);
            if (c == '\0' || c == ' ') end--; else break;
        }
        int start = 0;
        while (start < end && s.charAt(start) == '\0') start++;
        return s.substring(start, end);
    }

    public static int getUint8(byte[] b, int o) { return b[o] & 0xFF; }

    public static int getUint16(byte[] b, int o, boolean le) {
        int b0 = b[o] & 0xFF, b1 = b[o + 1] & 0xFF;
        return le ? (b0 | (b1 << 8)) : ((b0 << 8) | b1);
    }

    public static short getInt16(byte[] b, int o, boolean le) {
        return (short) getUint16(b, o, le);
    }

    public static long getUint32(byte[] b, int o, boolean le) {
        long b0 = b[o] & 0xFF, b1 = b[o + 1] & 0xFF, b2 = b[o + 2] & 0xFF, b3 = b[o + 3] & 0xFF;
        return le ? (b0 | (b1 << 8) | (b2 << 16) | (b3 << 24))
                  : ((b0 << 24) | (b1 << 16) | (b2 << 8) | b3);
    }

    public static int getInt32(byte[] b, int o, boolean le) {
        return (int) getUint32(b, o, le);
    }

    public static float getFloat32(byte[] b, int o, boolean le) {
        return Float.intBitsToFloat(getInt32(b, o, le));
    }

    public static double getFloat64(byte[] b, int o, boolean le) {
        long v = 0;
        if (le) {
            for (int i = 7; i >= 0; i--) v = (v << 8) | (b[o + i] & 0xFF);
        } else {
            for (int i = 0; i < 8; i++) v = (v << 8) | (b[o + i] & 0xFF);
        }
        return Double.longBitsToDouble(v);
    }
}
