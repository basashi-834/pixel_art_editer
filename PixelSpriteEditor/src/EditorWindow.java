import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Assembles the whole UI: menu bar, toolbar, color panel, canvas, status
 * bar. Owns the one EditorState instance and wires every control to it,
 * refreshing toolbar/menu/status-bar chrome on state.addChangeListener
 * rather than each control tracking state independently.
 */
public class EditorWindow extends JFrame {

    private final EditorState state = new EditorState(64, 64);
    private final CanvasPanel canvasPanel;

    private final Map<EditorState.ToolType, JToggleButton> toolButtons = new EnumMap<>(EditorState.ToolType.class);
    private JLabel statusLeft;
    private JLabel statusRight;
    private JLabel zoomLabel;
    private JButton undoBtn;
    private JButton redoBtn;
    private JMenuItem menuUndo;
    private JMenuItem menuRedo;
    private JCheckBoxMenuItem menuTogglePixelGrid;
    private JCheckBoxMenuItem menuToggle16Guide;
    private JCheckBoxMenuItem menuToggleChecker;

    public EditorWindow() {
        super("PixelSpriteEditor");

        canvasPanel = new CanvasPanel(state);
        ColorPanel colorPanel = new ColorPanel(state);
        canvasPanel.setHoverListener((x, y, inBounds) ->
                statusLeft.setText(inBounds ? "(" + x + ", " + y + ")" : "キャンバス外"));

        setJMenuBar(buildMenuBar());
        setLayout(new BorderLayout());
        add(buildToolbar(), BorderLayout.NORTH);
        add(colorPanel, BorderLayout.WEST);
        add(canvasPanel, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        state.addChangeListener(this::refreshChrome);
        refreshChrome();

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1150, 740);
        setMinimumSize(new Dimension(820, 560));
        setLocationRelativeTo(null);

        // The panel has no real size yet during construction; center once the
        // initial layout pass has actually happened.
        SwingUtilities.invokeLater(canvasPanel::centerCamera);
    }

    // ---- menu -------------------------------------------------------------

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("ファイル");
        file.add(menuItem("新規", KeyEvent.VK_N, 0, e -> onNew()));
        file.add(menuItem("開く...", KeyEvent.VK_O, 0, e -> onOpen()));
        file.add(menuItem("保存", KeyEvent.VK_S, 0, e -> onSave()));
        file.add(menuItem("名前を付けて保存...", KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK, e -> onSaveAs()));
        file.addSeparator();
        file.add(plainMenuItem("終了", e -> dispose()));
        bar.add(file);

        JMenu edit = new JMenu("編集");
        menuUndo = menuItem("元に戻す", KeyEvent.VK_Z, 0, e -> state.undo());
        menuRedo = menuItem("やり直し", KeyEvent.VK_Y, 0, e -> state.redo());
        edit.add(menuUndo);
        edit.add(menuRedo);
        bar.add(edit);

        JMenu view = new JMenu("表示");
        menuTogglePixelGrid = new JCheckBoxMenuItem("ピクセルグリッド", state.isShowPixelGrid());
        menuTogglePixelGrid.addActionListener(e -> state.setShowPixelGrid(menuTogglePixelGrid.isSelected()));
        menuToggle16Guide = new JCheckBoxMenuItem("16x16ガイド", state.isShow16Guide());
        menuToggle16Guide.addActionListener(e -> state.setShow16Guide(menuToggle16Guide.isSelected()));
        menuToggleChecker = new JCheckBoxMenuItem("透明チェッカーボード", state.isShowTransparencyChecker());
        menuToggleChecker.addActionListener(e -> state.setShowTransparencyChecker(menuToggleChecker.isSelected()));
        view.add(menuTogglePixelGrid);
        view.add(menuToggle16Guide);
        view.add(menuToggleChecker);
        view.addSeparator();
        view.add(plainMenuItem("ズームイン", e -> zoomAtCenter(1)));
        view.add(plainMenuItem("ズームアウト", e -> zoomAtCenter(-1)));
        bar.add(view);

        return bar;
    }

    private JMenuItem menuItem(String text, int keyCode, int extraModifiers, ActionListener action) {
        JMenuItem item = new JMenuItem(text);
        item.setAccelerator(KeyStroke.getKeyStroke(keyCode, InputEvent.CTRL_DOWN_MASK | extraModifiers));
        item.addActionListener(action);
        return item;
    }

    private JMenuItem plainMenuItem(String text, ActionListener action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(action);
        return item;
    }

    // ---- toolbar ----------------------------------------------------------

    private JToolBar buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        addToolButton(bar, EditorState.ToolType.PENCIL, "鉛筆");
        addToolButton(bar, EditorState.ToolType.ERASER, "消しゴム");
        addToolButton(bar, EditorState.ToolType.FILL, "塗りつぶし");
        addToolButton(bar, EditorState.ToolType.EYEDROPPER, "スポイト");
        addToolButton(bar, EditorState.ToolType.LINE, "直線");

        bar.addSeparator();
        undoBtn = new JButton("元に戻す");
        undoBtn.addActionListener(e -> state.undo());
        redoBtn = new JButton("やり直し");
        redoBtn.addActionListener(e -> state.redo());
        bar.add(undoBtn);
        bar.add(redoBtn);

        bar.addSeparator();
        JButton zoomOut = new JButton("-");
        zoomOut.addActionListener(e -> zoomAtCenter(-1));
        zoomLabel = new JLabel("800%", SwingConstants.CENTER);
        zoomLabel.setPreferredSize(new Dimension(50, 20));
        JButton zoomIn = new JButton("+");
        zoomIn.addActionListener(e -> zoomAtCenter(1));
        bar.add(zoomOut);
        bar.add(zoomLabel);
        bar.add(zoomIn);

        return bar;
    }

    private void addToolButton(JToolBar bar, EditorState.ToolType type, String label) {
        JToggleButton btn = new JToggleButton(label);
        btn.addActionListener(e -> state.setCurrentToolType(type));
        bar.add(btn);
        toolButtons.put(type, btn);
    }

    private void zoomAtCenter(int direction) {
        double cx = canvasPanel.getWidth() / 2.0;
        double cy = canvasPanel.getHeight() / 2.0;
        if (direction > 0) {
            state.zoomIn(cx, cy);
        } else {
            state.zoomOut(cx, cy);
        }
    }

    // ---- status bar -------------------------------------------------------

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusLeft = new JLabel(" ");
        statusRight = new JLabel(" ");
        bar.add(statusLeft, BorderLayout.WEST);
        bar.add(statusRight, BorderLayout.EAST);
        return bar;
    }

    // ---- file operations ----------------------------------------------------

    private void onNew() {
        NewCanvasDialog dialog = new NewCanvasDialog(this);
        dialog.setVisible(true);
        Dimension size = dialog.getResult();
        if (size != null) {
            state.newCanvas(size.width, size.height);
            canvasPanel.centerCamera();
        }
    }

    private void onOpen() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PNG Image (*.png)", "png"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        try {
            BufferedImage img = ImageIO.read(file);
            if (img == null) throw new IOException("Unsupported or corrupt PNG file");
            state.loadFrom(img, file.getAbsolutePath());
            canvasPanel.centerCamera();
        } catch (IOException ex) {
            showError("ファイルを開けませんでした:\n" + ex.getMessage());
        }
    }

    private void onSave() {
        String path = state.getCurrentFilePath();
        if (path == null) {
            onSaveAs();
            return;
        }
        savePngTo(new File(path));
    }

    private void onSaveAs() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PNG Image (*.png)", "png"));
        chooser.setSelectedFile(new File("sprite.png"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".png")) {
            file = new File(file.getParentFile(), file.getName() + ".png");
        }
        if (savePngTo(file)) {
            state.setSavedPath(file.getAbsolutePath());
        }
    }

    private boolean savePngTo(File file) {
        try {
            ImageIO.write(state.getCanvas().getImage(), "png", file);
            return true;
        } catch (IOException ex) {
            showError("保存に失敗しました:\n" + ex.getMessage());
            return false;
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "エラー", JOptionPane.ERROR_MESSAGE);
    }

    // ---- refresh ------------------------------------------------------------

    private void refreshChrome() {
        zoomLabel.setText((state.getZoom() * 100) + "%");

        undoBtn.setEnabled(state.getHistory().canUndo());
        redoBtn.setEnabled(state.getHistory().canRedo());
        menuUndo.setEnabled(undoBtn.isEnabled());
        menuRedo.setEnabled(redoBtn.isEnabled());

        JToggleButton active = toolButtons.get(state.getCurrentToolType());
        if (active != null) active.setSelected(true);

        menuTogglePixelGrid.setSelected(state.isShowPixelGrid());
        menuToggle16Guide.setSelected(state.isShow16Guide());
        menuToggleChecker.setSelected(state.isShowTransparencyChecker());

        String path = state.getCurrentFilePath();
        String fileName = path != null ? new File(path).getName() : "無題";
        setTitle(fileName + " - PixelSpriteEditor");

        String pathText = path != null ? path : "(未保存)";
        PixelCanvas canvas = state.getCanvas();
        statusRight.setText(canvas.getWidth() + "x" + canvas.getHeight()
                + "   " + (state.getZoom() * 100) + "%   " + pathText);
    }
}
