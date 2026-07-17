//
// ImageView.java
// The interactive viewer canvas: fits the current frame to the component, then
// applies a user zoom, pan and window/level gestures, and paints the classic
// corner overlays (Swing equivalent of the web app's <canvas> + stage).
//

package dicomviewer.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;

public final class ImageView extends JComponent {
    private static final long serialVersionUID = 1L;

    public static final int TOOL_WL = 0;
    public static final int TOOL_PAN = 1;

    private static final double MIN_ZOOM = 0.4;
    private static final double MAX_ZOOM = 14.0;

    public interface Listener {
        void onScrub(int delta);
        void onWindowAdjust(double deltaCenter, double deltaWidth);
        void onReset();
        void onViewChanged();
    }

    private BufferedImage image;
    private String message;
    private int tool = TOOL_WL;

    private double userScale = 1.0;
    private int offsetX = 0;
    private int offsetY = 0;

    private String overlayTL = "", overlayTR = "", overlayBL = "", overlayBR = "";

    private Listener listener;
    private int lastX, lastY;
    private boolean dragging;

    public ImageView() {
        setOpaque(true);
        setBackground(Color.BLACK);
        setFocusable(true);
        MouseHandler h = new MouseHandler();
        addMouseListener(h);
        addMouseMotionListener(h);
        addMouseWheelListener(h);
        updateCursor();
    }

    public void setListener(Listener l) { this.listener = l; }

    public void setImage(BufferedImage img) {
        this.image = img;
        this.message = null;
        repaint();
    }

    public void setMessage(String msg) {
        this.image = null;
        this.message = msg;
        repaint();
    }

    public void setOverlays(String tl, String tr, String bl, String br) {
        overlayTL = tl == null ? "" : tl;
        overlayTR = tr == null ? "" : tr;
        overlayBL = bl == null ? "" : bl;
        overlayBR = br == null ? "" : br;
        repaint();
    }

    public void setTool(int tool) {
        this.tool = tool;
        updateCursor();
    }

    public double getUserScale() { return userScale; }

    public void resetTransform() {
        userScale = 1.0;
        offsetX = 0;
        offsetY = 0;
        repaint();
    }

    // ------------------------------------------------------------- painting

    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        int w = getWidth();
        int h = getHeight();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);

        if (image != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int iw = image.getWidth();
            int ih = image.getHeight();
            double fit = Math.min((double) w / iw, (double) h / ih);
            if (fit <= 0 || Double.isInfinite(fit)) fit = 1;
            double eff = fit * userScale;
            int dw = (int) Math.round(iw * eff);
            int dh = (int) Math.round(ih * eff);
            int x = (w - dw) / 2 + offsetX;
            int y = (h - dh) / 2 + offsetY;
            g.drawImage(image, x, y, dw, dh, null);
            drawOverlays(g, w, h);
        } else if (message != null) {
            drawCenteredMessage(g, w, h, message);
        }
        g.dispose();
    }

    private void drawOverlays(Graphics2D g, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font font = getFont().deriveFont(Font.PLAIN, 12f);
        g.setFont(font);
        int pad = 10;
        int lh = g.getFontMetrics().getHeight();

        drawBlock(g, splitLines(overlayTL), pad, pad + g.getFontMetrics().getAscent(), false, lh);
        drawBlock(g, splitLines(overlayTR), w - pad, pad + g.getFontMetrics().getAscent(), true, lh);

        String[] bl = splitLines(overlayBL);
        drawBlock(g, bl, pad, h - pad - (bl.length - 1) * lh, false, lh);
        String[] br = splitLines(overlayBR);
        drawBlock(g, br, w - pad, h - pad - (br.length - 1) * lh, true, lh);
    }

    private void drawBlock(Graphics2D g, String[] lines, int x, int y, boolean rightAlign, int lh) {
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.length() == 0) continue;
            int tx = x;
            if (rightAlign) tx = x - g.getFontMetrics().stringWidth(line);
            int ty = y + i * lh;
            g.setColor(new Color(0, 0, 0, 170));
            g.drawString(line, tx + 1, ty + 1);
            g.setColor(new Color(0xE6, 0xF0, 0xFF));
            g.drawString(line, tx, ty);
        }
    }

    private void drawCenteredMessage(Graphics2D g, int w, int h, String msg) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x9A, 0xA6, 0xB8));
        g.setFont(getFont().deriveFont(Font.PLAIN, 13f));
        String[] lines = splitLines(msg);
        int lh = g.getFontMetrics().getHeight();
        int total = lines.length * lh;
        int y = (h - total) / 2 + g.getFontMetrics().getAscent();
        for (String line : lines) {
            int tw = g.getFontMetrics().stringWidth(line);
            g.drawString(line, (w - tw) / 2, y);
            y += lh;
        }
    }

    private static String[] splitLines(String s) {
        if (s == null || s.length() == 0) return new String[0];
        return s.split("\n", -1);
    }

    private void updateCursor() {
        setCursor(java.awt.Cursor.getPredefinedCursor(
                tool == TOOL_PAN ? java.awt.Cursor.MOVE_CURSOR : java.awt.Cursor.CROSSHAIR_CURSOR));
    }

    // ------------------------------------------------------------- gestures

    private final class MouseHandler extends MouseAdapter implements MouseWheelListener {
        public void mousePressed(MouseEvent e) {
            requestFocusInWindow();
            if (e.getClickCount() == 2) {
                if (listener != null) listener.onReset();
                return;
            }
            lastX = e.getX();
            lastY = e.getY();
            dragging = true;
        }

        public void mouseReleased(MouseEvent e) { dragging = false; }

        public void mouseDragged(MouseEvent e) {
            if (!dragging) return;
            int dx = e.getX() - lastX;
            int dy = e.getY() - lastY;
            lastX = e.getX();
            lastY = e.getY();
            if (tool == TOOL_PAN) {
                offsetX += dx;
                offsetY += dy;
                repaint();
                if (listener != null) listener.onViewChanged();
            } else {
                if (listener != null) listener.onWindowAdjust(-dy, dx);
            }
        }

        public void mouseWheelMoved(MouseWheelEvent e) {
            int rotation = e.getWheelRotation();
            if (rotation == 0) return;
            boolean zoom = e.isControlDown() || e.isMetaDown() || e.isAltDown();
            if (zoom) {
                double factor = Math.exp(-rotation * 0.12);
                userScale = clamp(userScale * factor, MIN_ZOOM, MAX_ZOOM);
                repaint();
                if (listener != null) listener.onViewChanged();
            } else if (listener != null) {
                listener.onScrub(rotation > 0 ? 1 : -1);
            }
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
