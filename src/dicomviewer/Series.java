//
// Series.java
// A folder of DICOM slice files, sorted naturally by name. Mirrors the "series"
// concept from the web app's library.
//

package dicomviewer;

import java.io.File;
import java.util.List;

public final class Series {
    public final String name;
    public final String sourcePath;   // absolute path of the imported folder
    public final List<File> slices;   // naturally sorted

    public Series(String name, String sourcePath, List<File> slices) {
        this.name = name;
        this.sourcePath = sourcePath;
        this.slices = slices;
    }

    public int count() { return slices.size(); }

    public File slice(int index) { return slices.get(index); }

    // Representative slice used for the library thumbnail (middle of the stack).
    public File thumbnailSource() {
        if (slices.isEmpty()) return null;
        return slices.get(slices.size() / 2);
    }

    public long totalSize() {
        long total = 0;
        for (File f : slices) total += f.length();
        return total;
    }
}
