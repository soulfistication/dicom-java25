//
// DicomElement.java
// A single parsed DICOM data element and typed accessors for its value (port of
// element.js).
//

package dicomviewer;

import java.util.ArrayList;
import java.util.List;

public final class DicomElement {
    public final DicomTag tag;
    public final String vr;
    public final byte[] value;              // raw bytes
    public final boolean littleEndian;
    public final List<DicomDataset> items;  // for SQ
    public final List<byte[]> fragments;    // for encapsulated pixel data

    public DicomElement(DicomTag tag, String vr, byte[] value, boolean littleEndian) {
        this(tag, vr, value, littleEndian,
             new ArrayList<>(), new ArrayList<>());
    }

    public DicomElement(DicomTag tag, String vr, byte[] value, boolean littleEndian,
                        List<DicomDataset> items, List<byte[]> fragments) {
        this.tag = tag;
        this.vr = vr;
        this.value = value;
        this.littleEndian = littleEndian;
        this.items = items;
        this.fragments = fragments;
    }

    public boolean isEncapsulated() { return !fragments.isEmpty(); }

    public String[] stringValues() {
        String text = ByteReader.decodeString(value);
        if (text.length() == 0) return new String[0];
        String[] parts = text.split("\\\\", -1);
        String[] out = new String[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = parts[i].trim();
        return out;
    }

    public String stringValue() {
        return Vr.isString(vr) ? ByteReader.decodeString(value) : null;
    }

    public long[] intValues() {
        int n = value.length;
        boolean le = littleEndian;
        List<Long> out = new ArrayList<>();
        if ("US".equals(vr)) {
            for (int i = 0; i + 2 <= n; i += 2) out.add((long) (ByteReader.getUint16(value, i, le)));
        } else if ("SS".equals(vr)) {
            for (int i = 0; i + 2 <= n; i += 2) out.add((long) (ByteReader.getInt16(value, i, le)));
        } else if ("UL".equals(vr)) {
            for (int i = 0; i + 4 <= n; i += 4) out.add(ByteReader.getUint32(value, i, le));
        } else if ("SL".equals(vr)) {
            for (int i = 0; i + 4 <= n; i += 4) out.add((long) (ByteReader.getInt32(value, i, le)));
        } else {
            for (String s : stringValues()) {
                try { out.add((long) Integer.parseInt(s.trim())); } catch (NumberFormatException e) { /* skip */ }
            }
        }
        long[] arr = new long[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i).longValue();
        return arr;
    }

    public Long intValue() {
        long[] v = intValues();
        return v.length > 0 ? Long.valueOf(v[0]) : null;
    }

    public double[] doubleValues() {
        int n = value.length;
        boolean le = littleEndian;
        List<Double> out = new ArrayList<>();
        if ("FL".equals(vr)) {
            for (int i = 0; i + 4 <= n; i += 4) out.add((double) ByteReader.getFloat32(value, i, le));
        } else if ("FD".equals(vr)) {
            for (int i = 0; i + 8 <= n; i += 8) out.add(ByteReader.getFloat64(value, i, le));
        } else {
            for (String s : stringValues()) {
                try { out.add(Double.parseDouble(s.trim())); } catch (NumberFormatException e) { /* skip */ }
            }
        }
        double[] arr = new double[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i).doubleValue();
        return arr;
    }

    public Double doubleValue() {
        double[] v = doubleValues();
        return v.length > 0 ? Double.valueOf(v[0]) : null;
    }

    public String displayString() {
        if (tag.key() == DicomTag.PIXEL_DATA) {
            return "<" + value.length + " bytes of pixel data>";
        }
        if ("SQ".equals(vr)) {
            int n = items.size();
            return "<sequence \u00b7 " + n + " item" + (n == 1 ? "" : "s") + ">";
        }
        if (Vr.isString(vr)) {
            String s = ByteReader.decodeString(value);
            return s.length() > 0 ? s : "\u2014";
        }
        if ("US".equals(vr) || "SS".equals(vr) || "UL".equals(vr) || "SL".equals(vr)) {
            return joinLongs(intValues());
        }
        if ("FL".equals(vr) || "FD".equals(vr)) {
            return joinDoubles(doubleValues());
        }
        if ("AT".equals(vr)) {
            return "<attribute tag>";
        }
        return "<" + value.length + " bytes>";
    }

    private static String joinLongs(long[] v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(v[i]);
        }
        return sb.toString();
    }

    private static String joinDoubles(double[] v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(Format.significant(v[i], 6));
        }
        return sb.toString();
    }
}
