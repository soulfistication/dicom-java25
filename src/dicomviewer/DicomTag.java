//
// DicomTag.java
// Identifies a DICOM data element by (group, element), plus the well-known tag
// keys used throughout the viewer (port of dicom-tag.js).
//

package dicomviewer;

public final class DicomTag {
    public final int group;
    public final int element;

    public DicomTag(int group, int element) {
        this.group = group;
        this.element = element;
    }

    // Combined key: group in the high word, element in the low word. Held as a
    // long so groups above 0x7FFF do not overflow.
    public long key() { return key(group, element); }

    public boolean isDelimiter() { return group == 0xFFFE; }
    public boolean isPrivate() { return (group & 1) == 1; }

    public String description() {
        return String.format("(%04X,%04X)", group & 0xFFFF, element & 0xFFFF);
    }

    public static long key(int group, int element) {
        return ((long) (group & 0xFFFF) << 16) | (element & 0xFFFF);
    }

    // ------------------------------------------------------- well-known tags
    public static final long FILE_META_GROUP_LENGTH = key(0x0002, 0x0000);
    public static final long TRANSFER_SYNTAX_UID     = key(0x0002, 0x0010);

    public static final long PATIENT_NAME       = key(0x0010, 0x0010);
    public static final long PATIENT_ID         = key(0x0010, 0x0020);
    public static final long STUDY_DESCRIPTION  = key(0x0008, 0x1030);
    public static final long SERIES_DESCRIPTION = key(0x0008, 0x103E);
    public static final long MODALITY           = key(0x0008, 0x0060);

    public static final long SAMPLES_PER_PIXEL     = key(0x0028, 0x0002);
    public static final long PHOTOMETRIC_INTERP    = key(0x0028, 0x0004);
    public static final long NUMBER_OF_FRAMES      = key(0x0028, 0x0008);
    public static final long ROWS                  = key(0x0028, 0x0010);
    public static final long COLUMNS               = key(0x0028, 0x0011);
    public static final long PLANAR_CONFIGURATION  = key(0x0028, 0x0006);
    public static final long BITS_ALLOCATED        = key(0x0028, 0x0100);
    public static final long BITS_STORED           = key(0x0028, 0x0101);
    public static final long HIGH_BIT              = key(0x0028, 0x0102);
    public static final long PIXEL_REPRESENTATION  = key(0x0028, 0x0103);
    public static final long WINDOW_CENTER         = key(0x0028, 0x1050);
    public static final long WINDOW_WIDTH          = key(0x0028, 0x1051);
    public static final long RESCALE_INTERCEPT     = key(0x0028, 0x1052);
    public static final long RESCALE_SLOPE         = key(0x0028, 0x1053);

    public static final long PIXEL_DATA = key(0x7FE0, 0x0010);

    public static final long ITEM               = key(0xFFFE, 0xE000);
    public static final long ITEM_DELIMITER     = key(0xFFFE, 0xE00D);
    public static final long SEQUENCE_DELIMITER = key(0xFFFE, 0xE0DD);

    public static final long SOP_CLASS_UID      = key(0x0008, 0x0016);
    public static final long SOP_INSTANCE_UID   = key(0x0008, 0x0018);
    public static final long STUDY_INSTANCE_UID = key(0x0020, 0x000D);
}
