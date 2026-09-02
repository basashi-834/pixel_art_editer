import javax.swing.SwingUtilities;

/** Entry point: applies the dark theme, then builds and shows EditorWindow on the Swing event dispatch thread. */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DarkTheme.install();
            new EditorWindow().setVisible(true);
        });
    }
}
