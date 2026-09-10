import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Right-side panel: the active frame's layers, topmost-drawn-last order
 * shown topmost-first (matching how most layer panels read), each with a
 * visibility checkbox. Clicking a row makes it the active layer for
 * drawing/undo. +追加/複製/削除/↑/↓ mutate the active frame's layer stack.
 */
public class LayersPanel extends JPanel {

    private final EditorState state;
    private final JPanel listContainer;
    private JButton deleteBtn;
    private JButton upBtn;
    private JButton downBtn;

    public LayersPanel(EditorState state) {
        this.state = state;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setPreferredSize(new Dimension(170, 0));

        JLabel title = new JLabel("レイヤー");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(title);
        add(Box.createVerticalStrut(6));

        listContainer = new JPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(listContainer);
        add(Box.createVerticalStrut(6));

        JPanel buttons = new JPanel(new GridLayout(3, 2, 3, 3));
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
    }

    private JPanel buildRow(int index, Layer layer) {
        boolean active = index == state.getActiveLayerIndex();

        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        row.setOpaque(active);
        if (active) row.setBackground(new Color(0x3D, 0x6B, 0xA5));

        JCheckBox visible = new JCheckBox();
        visible.setSelected(layer.isVisible());
        visible.setOpaque(false);
        visible.addActionListener(e -> state.setLayerVisible(index, visible.isSelected()));
        row.add(visible, BorderLayout.WEST);

        JLabel name = new JLabel(layer.getName());
        row.add(name, BorderLayout.CENTER);

        MouseAdapter selectListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                state.setActiveLayerIndex(index);
            }
        };
        row.addMouseListener(selectListener);
        name.addMouseListener(selectListener);

        return row;
    }
}
