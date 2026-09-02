import javax.swing.SwingUtilities;

/** Entry point: builds and shows EditorWindow on the Swing event dispatch thread. */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new EditorWindow().setVisible(true));
    }
}
