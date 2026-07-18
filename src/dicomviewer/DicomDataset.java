//
// DicomDataset.java
// Ordered collection of DICOM elements with convenience lookups (port of
// dataset.js).
//

package dicomviewer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DicomDataset {
    private final Map<Long, DicomElement> elements = new HashMap<>();
    private final List<Long> order = new ArrayList<>();

    public void insert(DicomElement element) {
        long key = element.tag.key();
        if (!elements.containsKey(key)) order.add(key);
        elements.put(key, element);
    }

    public DicomElement get(long tagKey) { return elements.get(tagKey); }

    public List<DicomElement> orderedElements() {
        List<DicomElement> out = new ArrayList<>(order.size());
        for (Long k : order) {
            DicomElement e = elements.get(k);
            if (e != null) out.add(e);
        }
        return out;
    }

    public String getString(long tagKey) {
        DicomElement e = get(tagKey);
        return e != null ? e.stringValue() : null;
    }

    public Integer getInt(long tagKey) {
        DicomElement e = get(tagKey);
        if (e == null) return null;
        Long v = e.intValue();
        return v != null ? Integer.valueOf(v.intValue()) : null;
    }

    public int getIntOr(long tagKey, int fallback) {
        Integer v = getInt(tagKey);
        return v != null ? v.intValue() : fallback;
    }

    public long[] getInts(long tagKey) {
        DicomElement e = get(tagKey);
        return e != null ? e.intValues() : new long[0];
    }

    public Double getDouble(long tagKey) {
        DicomElement e = get(tagKey);
        return e != null ? e.doubleValue() : null;
    }

    public double getDoubleOr(long tagKey, double fallback) {
        Double v = getDouble(tagKey);
        return v != null ? v.doubleValue() : fallback;
    }

    public double[] getDoubles(long tagKey) {
        DicomElement e = get(tagKey);
        return e != null ? e.doubleValues() : new double[0];
    }
}
