//
// Main.java
// Entry point: installs the system look and feel and shows the viewer window.
//

package dicomviewer;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import dicomviewer.ui.MainFrame;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // fall back to the default look and feel
            }
            new MainFrame().setVisible(true);
        });
    }
}
