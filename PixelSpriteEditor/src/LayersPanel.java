import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;

/**
 * Right-side panel: the active frame's layers, topmost-drawn-last order
 * shown topmost-first (matching how most layer panels read), each with a
 * visibility checkbox and a small preview thumbnail so it's clear at a
 * glance which layer is selected/what it contains. Clicking a row makes it
 * the active layer for drawing/undo; dragging a row up/down (or the ↑/↓
 * buttons) reorders the layer stack. The opacity slider at the bottom edits
 * the active layer's opacity.
 */
public class LayersPanel extends JPanel {

    private static final int THUMB_SIZE = 28;

    private final EditorState state;
    private final JPanel listContainer;
    private JButton deleteBtn;
    private JButton upBtn;
    private JButton downBtn;
    private JSlider opacitySlider;
    private boolean updatingOpacitySlider;

    // Manual drag-to-reorder: AWT keeps delivering mouseDragged/mouseReleased
    // to whichever row received the mousePressed, even once the cursor
    // leaves that row's bounds, so a plain MouseMotionListener per row is
    // enough -- no need for Swing's heavier TransferHandler/DnD API.
    private int dragSourceIndex = -1;
    private JPanel dragHighlightRow;

    public LayersPanel(EditorState state) {
        this.state = state;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setPreferredSize(new Dimension(190, 0));

        JLabel title = new JLabel("レイヤー");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(title);
        add(Box.createVerticalStrut(6));

        listContainer = new JPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(listContainer);
        add(Box.createVerticalStrut(6));

        add(buildOpacityRow());
        add(Box.createVerticalStrut(6));

        JPanel buttons = new JPanel(new java.awt.GridLayout(3, 2, 3, 3));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));

        JButton addBtn = new JButton("+追加");
        addBtn.addActionListener(e -> state.addLayer());
        JButton dupBtn = new JButton("複製");
        dupBtn.addActionListener(e -> state.duplicateLayer(state.getActiveLayerIndex()));
        upBtn = new JButton("↑");
        upBtn.addActionListener(e -> state.moveLayer(state.getActiveLayerIndex(), state.getActiveLayerIndex() - 1));
        downBtn = new JButton("↓");
        downBtn.addActionListener(e -> state.moveLayer(state.getActiveLayerIndex(), state.getActiveLayerIndex() + 1));
        deleteBtn = new JButton("削除");
        deleteBtn.addActionListener(e -> state.removeLayer(state.getActiveLayerIndex()));

        buttons.add(addBtn);
        buttons.add(dupBtn);
        buttons.add(upBtn);
        buttons.add(downBtn);
        buttons.add(deleteBtn);
        add(buttons);

        add(Box.createVerticalGlue());

        state.addChangeListener(this::refresh);
        refresh();
    }

    private JPanel buildOpacityRow() {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        row.add(new JLabel("不透明度"), BorderLayout.WEST);
        opacitySlider = new JSlider(0, 100, 100);
        opacitySlider.addChangeListener(e -> {
            if (updatingOpacitySlider) return;
            state.setLayerOpacity(state.getActiveLayerIndex(), opacitySlider.getValue() / 100f);
        });
        row.add(opacitySlider, BorderLayout.CENTER);
        return row;
    }

    private void refresh() {
        listContainer.removeAll();
        List<Layer> layers = state.getLayers();
        // composite() draws layers[0] first (bottom) ... layers[last] last (top);
        // show topmost layer at the top of the list, as most layer panels do.
        for (int i = layers.size() - 1; i >= 0; i--) {
            listContainer.add(buildRow(i, layers.get(i)));
        }
        listContainer.revalidate();
        listContainer.repaint();

        int active = state.getActiveLayerIndex();
        deleteBtn.setEnabled(layers.size() > 1);
        upBtn.setEnabled(active < layers.size() - 1);
        downBtn.setEnabled(active > 0);

        updatingOpacitySlider = true;
        opacitySlider.setValue(Math.round(layers.get(active).getOpacity() * 100));
        updatingOpacitySlider = false;
    }

    private JPanel buildRow(int index, Layer layer) {
        boolean active = index == state.getActiveLayerIndex();

        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.putClientProperty("layerIndex", index);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, THUMB_SIZE + 6));
        row.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        row.setOpaque(active);
        if (active) row.setBackground(new Color(0x3D, 0x6B, 0xA5));

        JCheckBox visible = new JCheckBox();
        visible.setSelected(layer.isVisible());
        visible.setOpaque(false);
        visible.addActionListener(e -> state.setLayerVisible(index, visible.isSelected()));
        row.add(visible, BorderLayout.WEST);

        JPanel thumb = buildThumbnail(layer);
        JLabel name = new JLabel(layer.getName());
        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        center.setOpaque(false);
        center.add(thumb);
        center.add(name);
        row.add(center, BorderLayout.CENTER);

        MouseAdapter dragSelectListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                state.setActiveLayerIndex(index);
                dragSourceIndex = index;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragSourceIndex < 0) return;
                int target = rowIndexAtScreenPoint(e.getLocationOnScreen());
                int from = dragSourceIndex;
                dragSourceIndex = -1;
                clearDragHighlight();
                if (target >= 0 && target != from) {
                    state.moveLayer(from, target);
                }
            }
        };
        MouseMotionAdapter dragMoveListener = new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragSourceIndex < 0) return;
                highlightDragTarget(rowIndexAtScreenPoint(e.getLocationOnScreen()));
            }
        };
        row.addMouseListener(dragSelectListener);
        row.addMouseMotionListener(dragMoveListener);
        name.addMouseListener(dragSelectListener);
        name.addMouseMotionListener(dragMoveListener);

        return row;
    }

    private JPanel buildThumbnail(Layer layer) {
        BufferedImage image = layer.getCanvas().getImage();
        float opacity = layer.getOpacity();
        JPanel thumb = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(new Color(150, 150, 150));
                g2.fillRect(0, 0, THUMB_SIZE, THUMB_SIZE);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, opacity));
                g2.drawImage(image, 0, 0, THUMB_SIZE, THUMB_SIZE, 0, 0, image.getWidth(), image.getHeight(), null);
            }
        };
        thumb.setPreferredSize(new Dimension(THUMB_SIZE, THUMB_SIZE));
        thumb.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));
        return thumb;
    }

    /** Which row (by layer index, via the "layerIndex" client property) a screen point lands on, or -1. */
    private int rowIndexAtScreenPoint(Point screenPt) {
        for (Component c : listContainer.getComponents()) {
            if (!(c instanceof JPanel)) continue;
            Point local = new Point(screenPt);
            SwingUtilities.convertPointFromScreen(local, c);
            if (c.contains(local)) {
                Object idx = ((JPanel) c).getClientProperty("layerIndex");
                if (idx instanceof Integer) return (Integer) idx;
            }
        }
        return -1;
    }

    private void highlightDragTarget(int layerIndex) {
        JPanel target = null;
        if (layerIndex >= 0) {
            for (Component c : listContainer.getComponents()) {
                if (c instanceof JPanel && Integer.valueOf(layerIndex).equals(((JPanel) c).getClientProperty("layerIndex"))) {
                    target = (JPanel) c;
                    break;
                }
            }
        }
        if (target == dragHighlightRow) return;
        clearDragHighlight();
        if (target != null) {
            target.setBorder(BorderFactory.createLineBorder(new Color(0xE2, 0xA5, 0x3C), 2));
            dragHighlightRow = target;
        }
    }

    private void clearDragHighlight() {
        if (dragHighlightRow != null) {
            dragHighlightRow.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            dragHighlightRow = null;
        }
    }
}
