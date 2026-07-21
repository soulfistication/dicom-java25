//
// Library.java
// Session library of imported folders (series). Because the desktop app reads
// slices straight from disk, "persistence" just remembers the folder paths (via
// java.util.prefs) and re-scans them on launch.
//

package dicomviewer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.prefs.Preferences;

public final class Library {
    private static final Preferences PREFS = Preferences.userNodeForPackage(Library.class);
    private static final String KEY_COUNT = "folderCount";
    private static final String KEY_PATH = "folderPath.";

    private final List<Series> series = new ArrayList<>();

    public List<Series> series() { return series; }

    // Imports a folder as a single series and remembers it. Returns null when no
    // DICOM files are found.
    public Series importFolder(File dir) {
        Series s = scanFolder(dir);
        if (s == null) return null;
        // De-duplicate by source path (most recent import wins, moved to front).
        for (int i = series.size() - 1; i >= 0; i--) {
            if (series.get(i).sourcePath.equals(s.sourcePath)) series.remove(i);
        }
        series.add(0, s);
        persist();
        return s;
    }

    public void remove(Series s) {
        series.remove(s);
        persist();
    }

    // Re-scans previously imported folders on startup.
    public void loadPersisted() {
        int count = PREFS.getInt(KEY_COUNT, 0);
        for (int i = 0; i < count; i++) {
            String path = PREFS.get(KEY_PATH + i, null);
            if (path == null) continue;
            File dir = new File(path);
            if (!dir.isDirectory()) continue;
            Series s = scanFolder(dir);
            if (s != null) series.add(s);
        }
    }

    private void persist() {
        try {
            PREFS.clear();
            PREFS.putInt(KEY_COUNT, series.size());
            for (int i = 0; i < series.size(); i++) {
                PREFS.put(KEY_PATH + i, series.get(i).sourcePath);
            }
            PREFS.flush();
        } catch (Exception e) {
            // Persistence is best-effort; ignore failures.
        }
    }

    // Recursively collects DICOM files inside dir and builds a series.
    private Series scanFolder(File dir) {
        if (dir == null || !dir.isDirectory()) return null;
        List<File> found = new ArrayList<>();
        collect(dir, found);
        if (found.isEmpty()) return null;
        found.sort(Comparator.comparing(File::getAbsolutePath, NaturalOrder.INSTANCE));
        return new Series(dir.getName(), dir.getAbsolutePath(), found);
    }

    private void collect(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        // Deterministic traversal order.
        java.util.Arrays.sort(children, Comparator.comparing(File::getName, NaturalOrder.INSTANCE));
        for (File f : children) {
            if (f.isDirectory()) {
                collect(f, out);
            } else if (f.isFile() && isDicom(f)) {
                out.add(f);
            }
        }
    }

    // ---------------------------------------------------------- DICOM detection

    public static boolean isDicom(File f) {
        String name = f.getName();
        if (name.equalsIgnoreCase("DICOMDIR")) return false;
        if (name.toLowerCase().endsWith(".dcm")) return true;
        try {
            byte[] bytes = readAllBytes(f);
            return isDicom(bytes, name);
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isDicom(byte[] buffer, String name) {
        if (name != null && name.equalsIgnoreCase("DICOMDIR")) return false;
        if (name != null && name.toLowerCase().endsWith(".dcm")) return true;
        if (hasDicmMagic(buffer)) return true;
        if (buffer.length < 8) return false;
        try {
            ParsedFile file = DicomParser.parse(buffer);
            if (file.meta.getString(DicomTag.TRANSFER_SYNTAX_UID) != null) return true;
            DicomDataset ds = file.dataset;
            if (ds.get(DicomTag.PIXEL_DATA) != null) return true;
            if (ds.getInt(DicomTag.ROWS) != null && ds.getInt(DicomTag.COLUMNS) != null) return true;
            long[] probes = {
                DicomTag.MODALITY, DicomTag.PATIENT_NAME,
                DicomTag.SOP_CLASS_UID, DicomTag.SOP_INSTANCE_UID, DicomTag.STUDY_INSTANCE_UID,
            };
            for (long k : probes) {
                if (ds.get(k) != null) return true;
            }
        } catch (Exception e) {
            // not parseable -> not DICOM
        }
        return false;
    }

    private static boolean hasDicmMagic(byte[] b) {
        if (b.length < 132) return false;
        return b[128] == 0x44 && b[129] == 0x49 && b[130] == 0x43 && b[131] == 0x4D; // DICM
    }

    public static byte[] readAllBytes(File f) throws IOException {
        return Files.readAllBytes(f.toPath());
    }
}
