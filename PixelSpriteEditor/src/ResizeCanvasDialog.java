import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;

/**
 * Modal dialog for resizing the canvas: new width/height plus a 3x3 anchor
 * picker (same idea as GIMP's Canvas Size dialog) choosing where existing
 * content stays put as the canvas grows or shrinks.
 */
public class ResizeCanvasDialog extends JDialog {

    /** 0.0/0.5/1.0 on each axis: left-or-top / center / right-or-bottom. */
    public static final class Result {
        public final int width;
        public final int height;
        public final double anchorX;
        public final double anchorY;

        Result(int width, int height, double anchorX, double anchorY) {
            this.width = width;
            this.height = height;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
        }
    }

    private static final String[] ANCHOR_LABELS = {"↖", "↑", "↗", "←", "・", "→", "↙", "↓", "↘"};
    private static final double[] ANCHOR_X = {0.0, 0.5, 1.0, 0.0, 0.5, 1.0, 0.0, 0.5, 1.0};
    private static final double[] ANCHOR_Y = {0.0, 0.0, 0.0, 0.5, 0.5, 0.5, 1.0, 1.0, 1.0};

    private Result result;
    private final JSpinner widthSpinner;
    private final JSpinner heightSpinner;
    private int anchorIndex = 0; // top-left: existing content stays exactly where it is

    public ResizeCanvasDialog(Frame owner, int currentWidth, int currentHeight) {
        super(owner, "キャンバスをリサイズ", true);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JLabel hint = new JLabel("既存の内容は基準位置を保ったまま残ります（はみ出た部分は切り取られます）。");
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
        content.add(hint);
        content.add(Box.createVerticalStrut(10));

        JPanel sizeGrid = new JPanel(new GridLayout(2, 2, 8, 8));
        widthSpinner = new JSpinner(new SpinnerNumberModel(currentWidth, 1, 2048, 1));
        heightSpinner = new JSpinner(new SpinnerNumberModel(currentHeight, 1, 2048, 1));
        sizeGrid.add(new JLabel("幅 (px)"));
        sizeGrid.add(widthSpinner);
        sizeGrid.add(new JLabel("高さ (px)"));
        sizeGrid.add(heightSpinner);
        content.add(sizeGrid);
        content.add(Box.createVerticalStrut(12));

        JLabel anchorLabel = new JLabel("基準位置");
        anchorLabel.setFont(anchorLabel.getFont().deriveFont(Font.BOLD));
        content.add(anchorLabel);
        content.add(Box.createVerticalStrut(4));
        content.add(buildAnchorPicker());
        content.add(Box.createVerticalStrut(16));

        JPanel buttons = new JPanel();
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("キャンセル");
        ok.addActionListener(e -> {
            result = new Result((Integer) widthSpinner.getValue(), (Integer) heightSpinner.getValue(),
                    ANCHOR_X[anchorIndex], ANCHOR_Y[anchorIndex]);
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

    private JPanel buildAnchorPicker() {
        JPanel grid = new JPanel(new GridLayout(3, 3, 2, 2));
        grid.setMaximumSize(new Dimension(90, 90));
        grid.setPreferredSize(new Dimension(90, 90));
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < 9; i++) {
            int index = i;
            JToggleButton btn = new JToggleButton(ANCHOR_LABELS[i]);
            btn.setMargin(new java.awt.Insets(0, 0, 0, 0));
            btn.setSelected(i == anchorIndex);
            btn.addActionListener(e -> anchorIndex = index);
            group.add(btn);
            grid.add(btn);
        }
        JPanel wrapper = new JPanel();
        wrapper.add(grid);
        return wrapper;
    }

    /** Returns the chosen size/anchor, or null if the dialog was cancelled/closed. */
    public Result getResult() {
        return result;
    }
}
