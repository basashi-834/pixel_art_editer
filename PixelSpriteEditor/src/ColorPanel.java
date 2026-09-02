import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeListener;

/**
 * Left-side panel: an RGBA color picker (four 0-255 sliders, since the new
 * Java rewrite doesn't need the RGB565 quantization the earlier C++/C#
 * versions had), a fixed 16-color starter palette (15 colors + transparent),
 * and a row of recently-used colors.
 */
public class ColorPanel extends JPanel {

    private static final int SWATCH_SIZE = 18;
    private static final String[] CHANNEL_NAMES = {"R", "G", "B", "A"};

    /** 15 colors + transparent = 16, a fixed starter set (not user-editable, unlike Recent). */
    private static final int[] PALETTE = {
        0x00000000, // transparent
        0xFF000000, // black
        0xFF555555, // dark gray
        0xFFAAAAAA, // light gray
        0xFFFFFFFF, // white
        0xFFE63946, // red
        0xFFF4A261, // orange
        0xFFFFD166, // yellow
        0xFF2A9D8F, // teal green
        0xFF1B4332, // dark green
        0xFF4CC9F0, // cyan
        0xFF1D3557, // navy blue
        0xFF7B2CBF, // purple
        0xFFF72585, // pink
        0xFF6F4518, // brown
        0xFFE0AC69, // skin tone
    };

    private final EditorState state;
    private final JPanel swatch;
    private final JSlider[] sliders = new JSlider[4];
    private final JLabel[] valueLabels = new JLabel[4];
    private final JPanel recentRow;
    private boolean updating;

    public ColorPanel(EditorState state) {
        this.state = state;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel title = new JLabel("カラー");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(title);
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
        add(buildPaletteGrid());

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

    private JPanel buildChannelRow(int channelIndex) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        JLabel name = new JLabel(CHANNEL_NAMES[channelIndex]);
        name.setPreferredSize(new Dimension(14, 20));
        row.add(name, BorderLayout.WEST);

        JSlider slider = new JSlider(0, 255, 0);
        ChangeListener onChange = e -> {
            if (updating) return;
            state.setColorChannel(channelIndex, slider.getValue());
        };
        slider.addChangeListener(onChange);
        slider.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                state.pushCurrentColorToRecent();
            }
        });
        row.add(slider, BorderLayout.CENTER);
        sliders[channelIndex] = slider;

        JLabel value = new JLabel("0");
        value.setPreferredSize(new Dimension(30, 20));
        row.add(value, BorderLayout.EAST);
        valueLabels[channelIndex] = value;

        return row;
    }

    private JPanel buildPaletteGrid() {
        JPanel grid = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 3));
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (int argb : PALETTE) {
            grid.add(buildSwatchButton(argb));
        }
        return grid;
    }

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

    private JPanel buildSwatchButton(int argb) {
        JPanel sw = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintSwatchCell((Graphics2D) g, getWidth(), getHeight(), argb, 6);
            }
        };
        sw.setPreferredSize(new Dimension(SWATCH_SIZE, SWATCH_SIZE));
        sw.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
        sw.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                state.setCurrentColorArgb(argb);
            }
        });
        return sw;
    }

    private void refresh() {
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
            valueLabels[i].setText(String.valueOf(channels[i]));
        }
        updating = false;
        swatch.repaint();

        recentRow.removeAll();
        for (int c : state.getRecentColors()) {
            recentRow.add(buildSwatchButton(c));
        }
        recentRow.revalidate();
        recentRow.repaint();
    }
}
