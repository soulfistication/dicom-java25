//
// MetadataDialog.java
// Searchable list of every parsed data element (including nested sequences) of
// the current slice (Swing equivalent of the web app's metadata drawer).
//

package dicomviewer.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

import dicomviewer.Dictionary;
import dicomviewer.DicomDataset;
import dicomviewer.DicomElement;
import dicomviewer.ParsedFile;

public final class MetadataDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    private static final class Row {
        final String tag, name, vr, value;
        final int depth;
        Row(String tag, String name, String vr, String value, int depth) {
            this.tag = tag; this.name = name; this.vr = vr; this.value = value; this.depth = depth;
        }
    }

    private final List<Row> allRows = new ArrayList<>();
    private final DefaultTableModel model;
    private final JTextField search;

    public MetadataDialog(Frame owner, ParsedFile file) {
        super(owner, "Metadata", false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel summary = new JPanel();
        summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
        summary.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
        summary.add(new JLabel("Transfer Syntax:  " + Dictionary.transferSyntaxName(file.transferSyntax)));
        summary.add(new JLabel("Encoding:  " + (file.explicitVR ? "Explicit VR" : "Implicit VR")));
        summary.add(new JLabel("Byte Order:  " + (file.littleEndian ? "Little Endian" : "Big Endian")));

        search = new JTextField();
        JPanel searchRow = new JPanel(new BorderLayout(6, 0));
        searchRow.setBorder(BorderFactory.createEmptyBorder(0, 12, 8, 12));
        searchRow.add(new JLabel("Search:"), BorderLayout.WEST);
        searchRow.add(search, BorderLayout.CENTER);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(summary);
        top.add(searchRow);

        model = new DefaultTableModel(new Object[] {"Tag", "Name", "VR", "Value"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        table.setRowHeight(20);
        table.getColumnModel().getColumn(0).setPreferredWidth(110);
        table.getColumnModel().getColumn(1).setPreferredWidth(230);
        table.getColumnModel().getColumn(2).setPreferredWidth(40);
        table.getColumnModel().getColumn(3).setPreferredWidth(320);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(new JScrollPane(table), BorderLayout.CENTER);
        getContentPane().add(Box.createVerticalStrut(4), BorderLayout.SOUTH);

        collectRows(file.meta, 0);
        collectRows(file.dataset, 0);
        rebuild("");

        DocumentListener filter = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { rebuild(search.getText()); }
            @Override public void removeUpdate(DocumentEvent e) { rebuild(search.getText()); }
            @Override public void changedUpdate(DocumentEvent e) { rebuild(search.getText()); }
        };
        search.getDocument().addDocumentListener(filter);

        setPreferredSize(new Dimension(760, 560));
        pack();
        setLocationRelativeTo(owner);
    }

    private void collectRows(DicomDataset dataset, int depth) {
        for (DicomElement element : dataset.orderedElements()) {
            String name = Dictionary.name(element.tag);
            if (name == null) name = "Unknown";
            allRows.add(new Row(element.tag.description(), name, element.vr,
                    element.displayString(), depth));
            if ("SQ".equals(element.vr)) {
                int i = 0;
                for (DicomDataset item : element.items) {
                    i++;
                    allRows.add(new Row("(FFFE,E000)", "Item " + i, "\u2014", "", depth + 1));
                    collectRows(item, depth + 2);
                }
            }
        }
    }

    private void rebuild(String query) {
        String q = query == null ? "" : query.toLowerCase();
        model.setRowCount(0);
        for (Row r : allRows) {
            if (!q.isEmpty()) {
                boolean match = r.name.toLowerCase().contains(q)
                        || r.tag.toLowerCase().contains(q)
                        || (r.value != null && r.value.toLowerCase().contains(q));
                if (!match) continue;
            }
            String indented = "    ".repeat(r.depth) + r.name;
            model.addRow(new Object[] {r.tag, indented, r.vr, r.value});
        }
    }
}
