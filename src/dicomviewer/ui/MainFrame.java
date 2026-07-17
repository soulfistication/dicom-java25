//
// MainFrame.java
// The desktop viewer: a persistent library on the left and an interactive
// viewer on the right that scrubs the slices of a series (or the frames of a
// multi-frame file) with zoom / pan / window-level and a metadata inspector.
// Swing port of the web app's app.js controller.
//

package dicomviewer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import dicomviewer.Dictionary;
import dicomviewer.DicomImage;
import dicomviewer.DicomParser;
import dicomviewer.DicomTag;
import dicomviewer.Format;
import dicomviewer.Library;
import dicomviewer.ParsedFile;
import dicomviewer.Series;

public final class MainFrame extends JFrame implements ImageView.Listener {
    private static final long serialVersionUID = 1L;

    private static final int NAV_SLICE = 0;
    private static final int NAV_FRAME = 1;

    private final Library library = new Library();

    private final DefaultListModel<Series> studyModel = new DefaultListModel<>();
    private final JList<Series> studyList = new JList<>(studyModel);
    private final JLabel studyCount = new JLabel(" ");
    private final ImageView imageView = new ImageView();
    private final JSlider slider = new JSlider();
    private final JLabel sliceLabel = new JLabel(" ");
    private final JButton metaButton = new JButton("Metadata");

    // Viewer state.
    private Series currentSeries;
    private int navKind = NAV_SLICE;
    private int count = 1;
    private int index = 0;
    private final Map<Integer, SliceEntry> cache = new HashMap<>();
    private ParsedFile baseFile;
    private DicomImage baseImage;
    private ParsedFile currentFile;
    private DicomImage currentImage;
    private DicomImage.Window window = new DicomImage.Window(128, 256);
    private DicomImage.Range valueRange = new DicomImage.Range(0, 255);
    private boolean adjustingSlider;

    // Thumbnails.
    private final ExecutorService thumbPool = Executors.newSingleThreadExecutor();
    private final Map<String, Icon> thumbCache = new HashMap<>();
    private final Set<String> thumbInFlight = new HashSet<>();
    private final Icon placeholderIcon = makePlaceholder();

    private static final class SliceEntry {
        ParsedFile file;
        DicomImage image;
        String error;
    }

    public MainFrame() {
        super("DICOM Viewer");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        buildUi();
        imageView.setListener(this);
        installDropTarget();
        library.loadPersisted();
        refreshLibrary();
        imageView.setMessage("Open a folder of DICOM files to begin.");
        setSize(1100, 720);
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------ UI

    private void buildUi() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        JButton openBtn = new JButton("Open Folder\u2026");
        openBtn.addActionListener(e -> chooseFolder());
        toolbar.add(openBtn);
        toolbar.addSeparator();

        JToggleButton wlBtn = new JToggleButton("Window/Level", true);
        JToggleButton panBtn = new JToggleButton("Pan");
        ButtonGroup tools = new ButtonGroup();
        tools.add(wlBtn);
        tools.add(panBtn);
        wlBtn.addActionListener(e -> imageView.setTool(ImageView.TOOL_WL));
        panBtn.addActionListener(e -> imageView.setTool(ImageView.TOOL_PAN));
        toolbar.add(wlBtn);
        toolbar.add(panBtn);
        toolbar.addSeparator();

        JButton resetBtn = new JButton("Reset");
        resetBtn.addActionListener(e -> resetView());
        toolbar.add(resetBtn);

        metaButton.setEnabled(false);
        metaButton.addActionListener(e -> openMetadata());
        toolbar.add(metaButton);

        // Library (left).
        studyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        studyList.setCellRenderer(new StudyRenderer());
        studyList.setFixedCellHeight(72);
        studyList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            Series sel = studyList.getSelectedValue();
            if (sel != null) openSeries(sel);
        });
        JPanel libraryPanel = new JPanel(new BorderLayout());
        JPanel libHeader = new JPanel(new BorderLayout());
        libHeader.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        libHeader.add(new JLabel("Library"), BorderLayout.WEST);
        libHeader.add(studyCount, BorderLayout.EAST);
        libraryPanel.add(libHeader, BorderLayout.NORTH);
        libraryPanel.add(new JScrollPane(studyList), BorderLayout.CENTER);
        libraryPanel.setMinimumSize(new Dimension(200, 100));

        // Viewer (right).
        slider.setMinimum(0);
        slider.setMaximum(0);
        slider.setValue(0);
        slider.setEnabled(false);
        slider.addChangeListener(e -> {
            if (adjustingSlider) return;
            goToIndex(slider.getValue());
        });
        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 10, 6, 10));
        bottom.add(slider, BorderLayout.CENTER);
        sliceLabel.setPreferredSize(new Dimension(90, 20));
        bottom.add(sliceLabel, BorderLayout.EAST);

        imageView.setPreferredSize(new Dimension(640, 560));
        JPanel viewerPanel = new JPanel(new BorderLayout());
        viewerPanel.add(imageView, BorderLayout.CENTER);
        viewerPanel.add(bottom, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, libraryPanel, viewerPanel);
        split.setDividerLocation(280);
        split.setContinuousLayout(true);

        setLayout(new BorderLayout());
        add(toolbar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        installKeyBindings();
    }

    private void installKeyBindings() {
        JComponent root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("RIGHT"), "next");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("DOWN"), "next");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("LEFT"), "prev");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("UP"), "prev");
        root.getActionMap().put("next", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (count > 1) goToIndex(index + 1);
            }
        });
        root.getActionMap().put("prev", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (count > 1) goToIndex(index - 1);
            }
        });
    }

    private void installDropTarget() {
        new DropTarget(this, new DropTargetAdapter() {
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    Transferable t = dtde.getTransferable();
                    if (!t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        dtde.dropComplete(false);
                        return;
                    }
                    Object data = t.getTransferData(DataFlavor.javaFileListFlavor);
                    Series first = null;
                    if (data instanceof List) {
                        for (Object o : (List<?>) data) {
                            if (o instanceof File && ((File) o).isDirectory()) {
                                Series s = library.importFolder((File) o);
                                if (s != null && first == null) first = s;
                            }
                        }
                    }
                    refreshLibrary();
                    if (first != null) selectSeries(first);
                    dtde.dropComplete(true);
                } catch (Exception e) {
                    dtde.dropComplete(false);
                }
            }
        });
    }

    // ------------------------------------------------------------- library

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Import a folder of DICOM files");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Series s = library.importFolder(chooser.getSelectedFile());
        refreshLibrary();
        if (s != null) {
            selectSeries(s);
        } else {
            imageView.setMessage("No DICOM files found in that folder.");
        }
    }

    private void refreshLibrary() {
        studyModel.clear();
        List<Series> all = library.series();
        for (Series s : all) studyModel.addElement(s);
        int n = all.size();
        studyCount.setText(n == 0 ? " " : (n + (n == 1 ? " study" : " studies")));
        for (Series s : all) requestThumbnail(s);
    }

    private void selectSeries(Series s) {
        int idx = library.series().indexOf(s);
        if (idx >= 0) studyList.setSelectedIndex(idx);
    }

    // ------------------------------------------------------------- viewer

    private void openSeries(Series series) {
        currentSeries = series;
        cache.clear();
        imageView.resetTransform();
        baseFile = null;
        baseImage = null;
        currentFile = null;
        currentImage = null;

        int startIndex = series.count() / 2;
        SliceEntry first = decodeSlice(startIndex);

        if (first.image == null) {
            currentFile = first.file;
            navKind = NAV_SLICE;
            count = series.count();
            index = startIndex;
            metaButton.setEnabled(currentFile != null);
            String msg = first.error != null ? first.error : "No displayable image.";
            if (currentFile != null) msg += "\n\nMetadata is still available.";
            imageView.setMessage(msg);
            setupSlider();
            return;
        }

        if (series.count() == 1 && first.image.frameCount > 1) {
            navKind = NAV_FRAME;
            baseFile = first.file;
            baseImage = first.image;
            count = first.image.frameCount;
            index = count / 2;
        } else {
            navKind = NAV_SLICE;
            count = series.count();
            index = startIndex;
        }

        window = first.image.defaultWindow.copy();
        valueRange = first.image.valueRange;
        setupSlider();
        showCurrent();
    }

    private SliceEntry decodeSlice(int idx) {
        SliceEntry cached = cache.get(idx);
        if (cached != null) return cached;
        SliceEntry entry = new SliceEntry();
        try {
            File file = currentSeries.slice(idx);
            byte[] bytes = Library.readAllBytes(file);
            entry.file = DicomParser.parse(bytes);
            try {
                entry.image = DicomImage.decode(entry.file);
            } catch (Exception ie) {
                entry.error = ie.getMessage();
            }
        } catch (Exception e) {
            entry.error = e.getMessage();
        }
        cache.put(idx, entry);
        return entry;
    }

    private void showCurrent() {
        ParsedFile file;
        DicomImage image;
        int frame;
        if (navKind == NAV_FRAME) {
            file = baseFile;
            image = baseImage;
            frame = index;
        } else {
            SliceEntry entry = decodeSlice(index);
            file = entry.file;
            image = entry.image;
            frame = 0;
            if (image == null) {
                currentFile = file;
                currentImage = null;
                metaButton.setEnabled(file != null);
                imageView.setMessage(entry.error != null ? entry.error
                        : "No displayable image for this slice.");
                updateSliceLabel();
                return;
            }
        }

        currentFile = file;
        currentImage = image;
        metaButton.setEnabled(file != null);

        int clamped = Math.max(0, Math.min(frame, image.frameCount - 1));
        BufferedImage bi = image.render(clamped, window);
        if (bi != null) imageView.setImage(bi);
        updateOverlays();

        if (navKind == NAV_SLICE) prefetch(index + 1);
        if (navKind == NAV_SLICE) prefetch(index - 1);
    }

    private void prefetch(int idx) {
        if (idx < 0 || idx >= count) return;
        if (cache.containsKey(idx)) return;
        Series series = currentSeries;
        thumbPool.submit(() -> {
            if (series != currentSeries) return;
            decodeSlice(idx);
        });
    }

    private void rerenderCurrent() {
        if (currentImage == null) return;
        int frame = navKind == NAV_FRAME ? index : 0;
        int clamped = Math.max(0, Math.min(frame, currentImage.frameCount - 1));
        BufferedImage bi = currentImage.render(clamped, window);
        if (bi != null) imageView.setImage(bi);
        updateOverlays();
    }

    private void goToIndex(int i) {
        int clamped = Math.max(0, Math.min(i, count - 1));
        if (clamped == index && currentImage != null) return;
        index = clamped;
        adjustingSlider = true;
        slider.setValue(index);
        adjustingSlider = false;
        showCurrent();
    }

    private void setupSlider() {
        adjustingSlider = true;
        if (count > 1) {
            slider.setEnabled(true);
            slider.setMinimum(0);
            slider.setMaximum(count - 1);
            slider.setValue(index);
        } else {
            slider.setEnabled(false);
            slider.setMinimum(0);
            slider.setMaximum(0);
            slider.setValue(0);
        }
        adjustingSlider = false;
        updateSliceLabel();
    }

    private void resetView() {
        imageView.resetTransform();
        if (currentImage != null) window = currentImage.defaultWindow.copy();
        rerenderCurrent();
    }

    private void adjustWindow(double deltaCenter, double deltaWidth) {
        if (currentImage == null || !currentImage.supportsWindowing()) return;
        double span = Math.max(1, valueRange.max - valueRange.min);
        double center = window.center + deltaCenter * span / 400;
        double width = Math.max(1, window.width + deltaWidth * span / 400);
        window = new DicomImage.Window(center, width);
        rerenderCurrent();
    }

    private void openMetadata() {
        if (currentFile == null) return;
        MetadataDialog dialog = new MetadataDialog(this, currentFile);
        dialog.setVisible(true);
    }

    // --------------------------------------------------------- ImageView.Listener

    public void onScrub(int delta) { if (count > 1) goToIndex(index + delta); }
    public void onWindowAdjust(double deltaCenter, double deltaWidth) {
        adjustWindow(deltaCenter, deltaWidth);
    }
    public void onReset() { resetView(); }
    public void onViewChanged() { updateOverlays(); }

    // ------------------------------------------------------------- overlays

    private void updateOverlays() {
        if (currentFile == null) { updateSliceLabel(); return; }
        DicomImage img = currentImage;

        String patient = currentFile.dataset.getString(DicomTag.PATIENT_NAME);
        patient = patient == null ? "" : patient.replace('^', ' ').trim();
        String tl = joinLines(
                patient.length() > 0 ? "Patient: " + patient : null,
                currentFile.dataset.getString(DicomTag.STUDY_DESCRIPTION));

        String dims = img != null ? (img.info.columns + " \u00d7 " + img.info.rows) : null;
        String tr = joinLines(currentFile.dataset.getString(DicomTag.MODALITY), dims);

        String wl = (img != null && img.supportsWindowing())
                ? "W: " + Math.round(window.width) + "  L: " + Math.round(window.center) : null;
        double scale = imageView.getUserScale();
        String zoom = Math.abs(scale - 1.0) > 0.01
                ? "Zoom: " + Format.significant(scale, 2) + "\u00d7" : null;
        String bl = joinLines(wl, zoom);

        String noun = navKind == NAV_FRAME ? "Frame" : "Slice";
        String nav = count > 1 ? (noun + " " + (index + 1) + "/" + count) : null;
        String br = joinLines(nav, Dictionary.transferSyntaxName(currentFile.transferSyntax));

        imageView.setOverlays(tl, tr, bl, br);
        updateSliceLabel();
    }

    private void updateSliceLabel() {
        sliceLabel.setText(count > 1 ? ((index + 1) + " / " + count) : " ");
    }

    private static String joinLines(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.length() == 0) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(p);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------- thumbnails

    private void requestThumbnail(Series series) {
        String key = series.sourcePath;
        if (thumbCache.containsKey(key) || thumbInFlight.contains(key)) return;
        File file = series.thumbnailSource();
        if (file == null) return;
        thumbInFlight.add(key);
        thumbPool.submit(() -> {
            Icon icon = null;
            try {
                byte[] bytes = Library.readAllBytes(file);
                ParsedFile pf = DicomParser.parse(bytes);
                DicomImage img = DicomImage.decode(pf);
                BufferedImage bi = img.render(img.frameCount / 2, img.defaultWindow);
                if (bi != null) icon = new ImageIcon(scaleToThumb(bi, 56));
            } catch (Exception e) {
                // keep placeholder
            }
            Icon result = icon;
            SwingUtilities.invokeLater(() -> {
                thumbInFlight.remove(key);
                if (result != null) {
                    thumbCache.put(key, result);
                    studyList.repaint();
                }
            });
        });
    }

    private static BufferedImage scaleToThumb(BufferedImage src, int size) {
        int w = src.getWidth(), h = src.getHeight();
        double s = Math.min((double) size / w, (double) size / h);
        int dw = Math.max(1, (int) Math.round(w * s));
        int dh = Math.max(1, (int) Math.round(h * s));
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics g = out.getGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, size, size);
        g.drawImage(src, (size - dw) / 2, (size - dh) / 2, dw, dh, null);
        g.dispose();
        return out;
    }

    private static Icon makePlaceholder() {
        int size = 56;
        BufferedImage bi = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics g = bi.getGraphics();
        g.setColor(new Color(0x22, 0x27, 0x30));
        g.fillRect(0, 0, size, size);
        g.setColor(new Color(0x55, 0x5d, 0x6b));
        g.drawRect(6, 10, size - 12, size - 20);
        g.dispose();
        return new ImageIcon(bi);
    }

    private final class StudyRenderer extends JPanel implements ListCellRenderer<Series> {
        private final JLabel thumb = new JLabel();
        private final JLabel title = new JLabel();
        private final JLabel sub = new JLabel();

        StudyRenderer() {
            setLayout(new BorderLayout(8, 0));
            setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            thumb.setPreferredSize(new Dimension(56, 56));
            JPanel text = new JPanel(new BorderLayout());
            text.setOpaque(false);
            sub.setForeground(new Color(0x77, 0x7f, 0x8c));
            sub.setFont(sub.getFont().deriveFont(11f));
            text.add(title, BorderLayout.NORTH);
            text.add(sub, BorderLayout.CENTER);
            add(thumb, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Series> list, Series s, int idx,
                boolean isSelected, boolean cellHasFocus) {
            Icon icon = thumbCache.get(s.sourcePath);
            thumb.setIcon(icon != null ? icon : placeholderIcon);
            title.setText(s.name);
            int c = s.count();
            sub.setText(c + (c == 1 ? " slice \u00b7 " : " slices \u00b7 ") + Format.bytes(s.totalSize()));
            setOpaque(true);
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                title.setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                title.setForeground(list.getForeground());
            }
            return this;
        }
    }
}
