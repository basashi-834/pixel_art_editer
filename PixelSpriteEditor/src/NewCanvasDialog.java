import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/** Modal dialog asking for a new canvas's pixel width/height. */
public class NewCanvasDialog extends JDialog {

    private Dimension result;
    private final JSpinner widthSpinner;
    private final JSpinner heightSpinner;

    public NewCanvasDialog(Frame owner) {
        super(owner, "新規キャンバス", true);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JLabel hint = new JLabel("16の倍数（例: 64x64）が格ゲー用スプライトに適しています。");
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
        content.add(hint);
        content.add(Box.createVerticalStrut(10));

        JPanel grid = new JPanel(new GridLayout(2, 2, 8, 8));
        widthSpinner = new JSpinner(new SpinnerNumberModel(64, 1, 1024, 1));
        heightSpinner = new JSpinner(new SpinnerNumberModel(64, 1, 1024, 1));
        grid.add(new JLabel("幅 (px)"));
        grid.add(widthSpinner);
        grid.add(new JLabel("高さ (px)"));
        grid.add(heightSpinner);
        content.add(grid);
        content.add(Box.createVerticalStrut(16));

        JPanel buttons = new JPanel();
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("キャンセル");
        ok.addActionListener(e -> {
            result = new Dimension((Integer) widthSpinner.getValue(), (Integer) heightSpinner.getValue());
            dispose();
        });
        cancel.addActionListener(e -> {
            result = null;
            dispose();
        });
        buttons.add(cancel);
        buttons.add(ok);
        content.add(buttons);

        getRootPane().setDefaultButton(ok);
        setContentPane(content);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Returns the chosen size, or null if the dialog was cancelled/closed. */
    public Dimension getResult() {
        return result;
    }
}
