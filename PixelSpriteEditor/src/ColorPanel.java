import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;

/**
 * Left-side panel: an RGBA color picker (four 0-255 sliders each paired
 * with an editable numeric spinner, since the new Java rewrite doesn't need
 * the RGB565 quantization the earlier C++/C# versions had), a switchable
 * set of named color palettes (one fixed built-in 16-color set, plus any
 * the user creates), and a row of recently-used colors. Any swatch in a
 * user-created palette or in Recent can be right-clicked to remove it.
 */
public class ColorPanel extends JPanel {

    private static final int SWATCH_SIZE = 18;
    private static final String[] CHANNEL_NAMES = {"R", "G", "B", "A"};

    private final EditorState state;
    private final JPanel swatch;
    private final JSlider[] sliders = new JSlider[4];
    private final JSpinner[] spinners = new JSpinner[4];
    private final JPanel recentRow;

    // Palettes themselves live in EditorState (so ProjectIO can save/load them);
    // this panel only owns the combo box/grid widgets that display them.
    private JComboBox<ColorPalette> paletteCombo;
    private JPanel paletteGrid;
    private JButton deletePaletteBtn;
    private JButton addColorToPaletteBtn;

    private boolean updating;

    public ColorPanel(EditorState state) {
        this.state = state;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(sectionTitle("カラー"));
        add(Box.createVerticalStrut(6));

        swatch = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintSwatchCell((Graphics2D) g, getWidth(), getHeight(), state.getCurrentColorArgb(), 8);
            }
        };
        swatch.setPreferredSize(new Dimension(200, 48));
        swatch.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        swatch.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        swatch.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(swatch);
        add(Box.createVerticalStrut(10));

        for (int i = 0; i < 4; i++) {
            add(buildChannelRow(i));
            add(Box.createVerticalStrut(2));
        }

        add(Box.createVerticalStrut(10));
        add(sectionTitle("パレット"));
        add(buildPaletteSection());

        add(Box.createVerticalStrut(10));
        add(sectionTitle("最近使った色"));
        recentRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 3));
        recentRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(recentRow);

        add(Box.createVerticalGlue());

        state.addChangeListener(this::refresh);
        refresh();
    }

    private JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    // ---- RGBA sliders + numeric fields --------------------------------------

    private JPanel buildChannelRow(int channelIndex) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        JLabel name = new JLabel(CHANNEL_NAMES[channelIndex]);
        name.setPreferredSize(new Dimension(14, 20));
        row.add(name, BorderLayout.WEST);

        JSlider slider = new JSlider(0, 255, 0);
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(0, 0, 255, 1));
        spinner.setPreferredSize(new Dimension(55, 22));

        ChangeListener sliderListener = e -> {
            if (updating) return;
            updating = true;
            spinner.setValue(slider.getValue());
            updating = false;
            state.setColorChannel(channelIndex, slider.getValue());
        };
        slider.addChangeListener(sliderListener);
        slider.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                state.pushCurrentColorToRecent();
            }
        });

        ChangeListener spinnerListener = e -> {
            if (updating) return;
            int value = (Integer) spinner.getValue();
            updating = true;
            slider.setValue(value);
            updating = false;
            state.setColorChannel(channelIndex, value);
        };
        spinner.addChangeListener(spinnerListener);
        // Slider drags commit to Recent on mouse-release; typing/clicking the
        // spinner commits when focus leaves it, so holding the spinner's
        // repeat-arrow doesn't spam Recent with every intermediate value.
        // Keyboard focus actually lands on the spinner's internal text field,
        // not the JSpinner component itself, so the listener goes there.
        JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) spinner.getEditor();
        editor.getTextField().addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                // JFormattedTextField's own commit-on-focus-lost isn't guaranteed to have
                // run yet by the time other FocusListeners (this one included) are notified,
                // so commit explicitly first to make sure state reflects the typed value.
                try {
                    spinner.commitEdit();
                } catch (java.text.ParseException ignored) {
                    // Invalid text reverts to the last good value; nothing to commit.
                }
                state.pushCurrentColorToRecent();
            }
        });

        JPanel center = new JPanel(new BorderLayout(4, 0));
        center.add(slider, BorderLayout.CENTER);
        center.add(spinner, BorderLayout.EAST);
        row.add(center, BorderLayout.CENTER);

        sliders[channelIndex] = slider;
        spinners[channelIndex] = spinner;
        return row;
    }

    // ---- palette presets ----------------------------------------------------

    private JPanel buildPaletteSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        paletteCombo = new JComboBox<>();
        paletteCombo.setPreferredSize(new Dimension(100, 22));
        for (ColorPalette p : state.getPalettes()) paletteCombo.addItem(p);
        paletteCombo.addActionListener(e -> refreshPaletteGrid());
        header.add(paletteCombo);

        JButton newBtn = new JButton("+新規");
        newBtn.addActionListener(e -> onNewPalette());
        header.add(newBtn);

        deletePaletteBtn = new JButton("削除");
        deletePaletteBtn.addActionListener(e -> onDeletePalette());
        header.add(deletePaletteBtn);

        section.add(header);

        paletteGrid = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 3));
        paletteGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(paletteGrid);

        addColorToPaletteBtn = new JButton("現在の色をパレットに追加");
        addColorToPaletteBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        addColorToPaletteBtn.addActionListener(e -> onAddColorToPalette());
        section.add(addColorToPaletteBtn);

        refreshPaletteGrid();
        return section;
    }

    private void onNewPalette() {
        String name = JOptionPane.showInputDialog(this, "パレット名を入力してください", "新規パレット", JOptionPane.PLAIN_MESSAGE);
        if (name == null) return;
        name = name.trim();
        if (name.isEmpty()) return;

        ColorPalette palette = new ColorPalette(name, false, new ArrayList<>());
        state.addPalette(palette);
        paletteCombo.addItem(palette);
        paletteCombo.setSelectedItem(palette);
    }

    private void onDeletePalette() {
        ColorPalette selected = (ColorPalette) paletteCombo.getSelectedItem();
        if (selected == null || selected.isBuiltIn()) return;
        int confirm = JOptionPane.showConfirmDialog(this,
                "パレット「" + selected.getName() + "」を削除しますか？", "確認", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        state.removePalette(selected);
        paletteCombo.removeItem(selected);
        paletteCombo.setSelectedIndex(0);
    }

    private void onAddColorToPalette() {
        ColorPalette selected = (ColorPalette) paletteCombo.getSelectedItem();
        if (selected == null || selected.isBuiltIn()) return;
        selected.addColor(state.getCurrentColorArgb());
        refreshPaletteGrid();
    }

    private void refreshPaletteGrid() {
        ColorPalette selected = (ColorPalette) paletteCombo.getSelectedItem();
        paletteGrid.removeAll();
        boolean editable = selected != null && !selected.isBuiltIn();
        if (selected != null) {
            for (int argb : new ArrayList<>(selected.getColors())) {
                Runnable onRemove = editable ? () -> {
                    selected.removeColor(argb);
                    refreshPaletteGrid();
                } : null;
                paletteGrid.add(buildSwatchButton(argb, onRemove));
            }
        }
        deletePaletteBtn.setEnabled(editable);
        addColorToPaletteBtn.setEnabled(editable);
        paletteGrid.revalidate();
        paletteGrid.repaint();
    }

    // ---- swatches -------------------------------------------------------

    /** Fills w x h with a checkerboard first so alpha (including fully transparent) is visible, then the color on top. */
    private static void paintSwatchCell(Graphics2D g2, int w, int h, int argb, int cell) {
        for (int y = 0; y < h; y += cell) {
            for (int x = 0; x < w; x += cell) {
                boolean light = ((x / cell) + (y / cell)) % 2 == 0;
                g2.setColor(light ? new Color(230, 230, 230) : new Color(200, 200, 200));
                g2.fillRect(x, y, cell, cell);
            }
        }
        g2.setColor(new Color(argb, true));
        g2.fillRect(0, 0, w, h);
    }

    /** Left-click selects the color; right-click removes it, if onRemove is non-null. */
    private JPanel buildSwatchButton(int argb, Runnable onRemove) {
        JPanel sw = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintSwatchCell((Graphics2D) g, getWidth(), getHeight(), argb, 6);
            }
        };
        sw.setPreferredSize(new Dimension(SWATCH_SIZE, SWATCH_SIZE));
        sw.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
        if (onRemove != null) {
            sw.setToolTipText("右クリックで削除");
        }
        sw.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    if (onRemove != null) onRemove.run();
                } else {
                    state.setCurrentColorArgb(argb);
                }
            }
        });
        return sw;
    }

    /** Rebuilds the combo box only if EditorState's palette list actually changed underneath us (e.g. a project load) -- otherwise every unrelated state change (picking a color, drawing a pixel) would reset the dropdown's selection. */
    private void syncPaletteCombo() {
        List<ColorPalette> current = state.getPalettes();
        boolean same = paletteCombo.getItemCount() == current.size();
        for (int i = 0; same && i < current.size(); i++) {
            same = paletteCombo.getItemAt(i) == current.get(i);
        }
        if (same) return;

        paletteCombo.removeAllItems();
        for (ColorPalette p : current) paletteCombo.addItem(p);
        if (paletteCombo.getItemCount() > 0) paletteCombo.setSelectedIndex(0);
    }

    private void refresh() {
        syncPaletteCombo();
        updating = true;
        int argb = state.getCurrentColorArgb();
        int[] channels = {
            (argb >>> 16) & 0xFF, // R
            (argb >>> 8) & 0xFF,  // G
            argb & 0xFF,          // B
            (argb >>> 24) & 0xFF, // A
        };
        for (int i = 0; i < 4; i++) {
            sliders[i].setValue(channels[i]);
            spinners[i].setValue(channels[i]);
        }
        updating = false;
        swatch.repaint();

        recentRow.removeAll();
        for (int c : state.getRecentColors()) {
            recentRow.add(buildSwatchButton(c, () -> state.removeRecentColor(c)));
        }
        recentRow.revalidate();
        recentRow.repaint();

        refreshPaletteGrid();
    }
}
