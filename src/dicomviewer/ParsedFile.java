//
// ParsedFile.java
// The result of parsing a DICOM object: file-meta group, main dataset and the
// resolved transfer-syntax properties.
//

package dicomviewer;

public final class ParsedFile {
    public final DicomDataset meta;
    public final DicomDataset dataset;
    public final String transferSyntax;
    public final boolean littleEndian;
    public final boolean explicitVR;

    public ParsedFile(DicomDataset meta, DicomDataset dataset, String transferSyntax,
                      boolean littleEndian, boolean explicitVR) {
        this.meta = meta;
        this.dataset = dataset;
        this.transferSyntax = transferSyntax;
        this.littleEndian = littleEndian;
        this.explicitVR = explicitVR;
    }

    // Looks up a tag in the main dataset first, then the file-meta group.
    public DicomElement get(long tagKey) {
        DicomElement e = dataset.get(tagKey);
        return e != null ? e : meta.get(tagKey);
    }
}
