//
// Vr.java
// DICOM Value Representations and their on-disk encoding rules (port of vr.js).
//

package dicomviewer;

import java.util.Set;

public final class Vr {
    private Vr() {}

    // Explicit-VR encodings that use a 2-byte reserved field + 4-byte length.
    private static final Set<String> EXTENDED_LENGTH = Set.of(
        "OB", "OD", "OF", "OL", "OV", "OW", "SQ", "UC", "UR", "UT", "UN", "SV", "UV");

    private static final Set<String> STRING = Set.of(
        "AE", "AS", "CS", "DA", "DS", "DT", "IS", "LO", "LT", "PN", "SH", "ST", "TM",
        "UC", "UI", "UR", "UT");

    private static final Set<String> ALL = Set.of(
        "AE", "AS", "AT", "CS", "DA", "DS", "DT", "FL", "FD", "IS", "LO", "LT",
        "OB", "OD", "OF", "OL", "OV", "OW", "PN", "SH", "SL", "SQ", "SS", "ST",
        "SV", "TM", "UC", "UI", "UL", "UN", "UR", "US", "UT", "UV");

    public static boolean isValid(String code) { return ALL.contains(code); }
    public static boolean usesExtendedLength(String code) { return EXTENDED_LENGTH.contains(code); }
    public static boolean isString(String code) { return STRING.contains(code); }
}
