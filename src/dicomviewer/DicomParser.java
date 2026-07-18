//
// DicomParser.java
// A from-scratch DICOM Part 10 parser supporting Explicit/Implicit VR in
// little- and big-endian, sequences and encapsulated pixel data (port of parser.js).
//

package dicomviewer;

import java.util.ArrayList;
import java.util.List;

public final class DicomParser {
    private DicomParser() {}

    private static final long UNDEFINED_LENGTH = 0xFFFFFFFFL;

    public static final String IMPLICIT_VR_LITTLE_ENDIAN = "1.2.840.10008.1.2";
    public static final String EXPLICIT_VR_LITTLE_ENDIAN = "1.2.840.10008.1.2.1";
    public static final String EXPLICIT_VR_BIG_ENDIAN    = "1.2.840.10008.1.2.2";

    public static ParsedFile parse(byte[] bytes) throws DicomParseException {
        if (bytes.length < 8) {
            throw new DicomParseException("The file is too small to be a DICOM object.");
        }

        ByteReader reader = new ByteReader(bytes, true);
        DicomDataset meta = new DicomDataset();

        boolean hasPreamble = bytes.length >= 132 && "DICM".equals(magic(bytes, 128));
        if (hasPreamble) {
            reader.seek(132);
            meta = parseFileMeta(reader);
        }

        DicomElement tsElement = meta.get(DicomTag.TRANSFER_SYNTAX_UID);
        String transferSyntax = tsElement != null
            ? ByteReader.decodeString(tsElement.value)
            : detectSyntax(bytes, reader.offset());

        boolean explicit = !IMPLICIT_VR_LITTLE_ENDIAN.equals(transferSyntax);
        boolean little = !EXPLICIT_VR_BIG_ENDIAN.equals(transferSyntax);

        reader.littleEndian = little;
        DicomDataset dataset = parseDataset(reader, explicit, bytes.length);

        return new ParsedFile(meta, dataset, transferSyntax, little, explicit);
    }

    // File meta group is always Explicit VR Little Endian.
    private static DicomDataset parseFileMeta(ByteReader reader) throws DicomParseException {
        reader.littleEndian = true;
        DicomDataset meta = new DicomDataset();

        int groupLengthStart = reader.offset();
        DicomElement first = readElement(reader, true, reader.length());
        if (first == null) throw new DicomParseException("missing file meta group");
        meta.insert(first);

        int metaEnd = reader.length();
        Long groupLen = first.intValue();
        if (first.tag.key() == DicomTag.FILE_META_GROUP_LENGTH && groupLen != null) {
            metaEnd = (int) Math.min((long) reader.offset() + groupLen.longValue(), reader.length());
        } else {
            reader.seek(groupLengthStart);
        }

        while (reader.offset() < metaEnd) {
            DicomElement element = readElement(reader, true, metaEnd);
            if (element == null || element.tag.group != 0x0002) break;
            meta.insert(element);
        }
        return meta;
    }

    private static DicomDataset parseDataset(ByteReader reader, boolean explicitVR, int endOffset) {
        DicomDataset dataset = new DicomDataset();
        while (reader.offset() < endOffset) {
            int start = reader.offset();
            DicomElement element = readElement(reader, explicitVR, endOffset);
            if (element == null) break;
            long key = element.tag.key();
            if (key == DicomTag.ITEM_DELIMITER || key == DicomTag.SEQUENCE_DELIMITER) break;
            dataset.insert(element);
            if (reader.offset() <= start) break; // never stall
        }
        return dataset;
    }

    private static DicomElement readElement(ByteReader reader, boolean explicitVR, int endOffset) {
        DicomTag tag = reader.readTag();
        if (tag == null) return null;
        boolean little = reader.littleEndian;

        // Group FFFE (item / delimiters): 4-byte length, no VR.
        if (tag.isDelimiter()) {
            long length = reader.readUint32();
            if (length < 0) return null;
            if (length != UNDEFINED_LENGTH && length > 0) reader.skip((int) length);
            return new DicomElement(tag, "UN", new byte[0], little);
        }

        String vr;
        int length; // -1 means undefined length

        if (explicitVR) {
            String code = reader.readString(2);
            if (code == null) return null;
            if (Vr.isValid(code)) {
                vr = code;
            } else {
                String dictVr = Dictionary.vr(tag);
                vr = dictVr != null ? dictVr : "UN";
            }
            if (Vr.usesExtendedLength(vr)) {
                reader.skip(2); // reserved
                long l = reader.readUint32();
                if (l < 0) return null;
                length = l == UNDEFINED_LENGTH ? -1 : (int) l;
            } else {
                int l = reader.readUint16();
                if (l < 0) return null;
                length = l;
            }
        } else {
            String dictVr = Dictionary.vr(tag);
            vr = dictVr != null ? dictVr : (tag.key() == DicomTag.PIXEL_DATA ? "OW" : "UN");
            long l = reader.readUint32();
            if (l < 0) return null;
            length = l == UNDEFINED_LENGTH ? -1 : (int) l;
        }

        // Undefined length: sequence or encapsulated pixel data.
        if (length < 0) {
            if (tag.key() == DicomTag.PIXEL_DATA) {
                List<byte[]> fragments = readEncapsulatedFragments(reader);
                return new DicomElement(tag, "OB", new byte[0], little,
                                        new ArrayList<>(), fragments);
            }
            List<DicomDataset> items = readSequenceItems(reader, explicitVR, endOffset);
            return new DicomElement(tag, "SQ", new byte[0], little, items,
                                    new ArrayList<>());
        }

        // Defined-length sequence.
        if ("SQ".equals(vr)) {
            int seqEnd = (int) Math.min((long) reader.offset() + length, endOffset);
            List<DicomDataset> items = readSequenceItems(reader, explicitVR, seqEnd);
            return new DicomElement(tag, "SQ", new byte[0], little, items,
                                    new ArrayList<>());
        }

        if (length < 0 || !reader.canRead(length)) return null;
        byte[] value = reader.readBytes(length);
        if (value == null) value = new byte[0];
        return new DicomElement(tag, vr, value, little);
    }

    private static List<DicomDataset> readSequenceItems(ByteReader reader, boolean explicitVR,
                                                        int endOffset) {
        List<DicomDataset> items = new ArrayList<>();
        while (reader.offset() < endOffset) {
            DicomTag tag = reader.readTag();
            long rawLength = reader.readUint32();
            if (tag == null || rawLength < 0) break;
            if (tag.key() == DicomTag.SEQUENCE_DELIMITER) break;
            if (tag.key() != DicomTag.ITEM) break;

            if (rawLength == UNDEFINED_LENGTH) {
                items.add(parseDataset(reader, explicitVR, endOffset));
            } else {
                int itemEnd = (int) Math.min((long) reader.offset() + rawLength, reader.length());
                items.add(parseDataset(reader, explicitVR, itemEnd));
                reader.seek(itemEnd);
            }
        }
        return items;
    }

    private static List<byte[]> readEncapsulatedFragments(ByteReader reader) {
        List<byte[]> fragments = new ArrayList<>();
        boolean isFirst = true;
        while (!reader.isAtEnd()) {
            DicomTag tag = reader.readTag();
            long length = reader.readUint32();
            if (tag == null || length < 0) break;
            if (tag.key() == DicomTag.SEQUENCE_DELIMITER) break;
            if (tag.key() != DicomTag.ITEM) break;
            byte[] bytes = reader.readBytes((int) length);
            if (bytes == null) bytes = new byte[0];
            if (isFirst) { isFirst = false; continue; } // Basic Offset Table
            fragments.add(bytes);
        }
        return fragments;
    }

    private static String magic(byte[] bytes, int offset) {
        if (bytes.length < offset + 4) return null;
        return ByteReader.decodeString(bytes, offset, 4);
    }

    private static String detectSyntax(byte[] bytes, int offset) {
        if (bytes.length < offset + 6) return IMPLICIT_VR_LITTLE_ENDIAN;
        String code = ByteReader.decodeString(bytes, offset + 4, 2);
        return Vr.isValid(code) ? EXPLICIT_VR_LITTLE_ENDIAN : IMPLICIT_VR_LITTLE_ENDIAN;
    }
}
