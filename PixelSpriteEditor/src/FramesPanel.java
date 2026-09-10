import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Timer;

/**
 * Bottom strip: a thumbnail per animation frame (click to make it active),
 * +追加/複製/削除/←/→ to manage frames, a Play/Stop button that cycles
 * through frames on a timer for a quick preview, and an onion-skin toggle
 * (CanvasPanel does the actual onion-skin drawing; this just flips the flag).
 */
public class FramesPanel extends JPanel {

    private static final int THUMB_SIZE = 48;
    private static final int PLAYBACK_INTERVAL_MS = 125; // 8 fps

    private final EditorState state;
    private final JPanel thumbRow;
    private final Timer playbackTimer;
    private JButton playBtn;
    private JButton deleteBtn;
    private JButton leftBtn;
    private JButton rightBtn;

    public FramesPanel(EditorState state) {
        this.state = state;
        setLayout(new BorderLayout(0, 4));
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        playbackTimer = new Timer(PLAYBACK_INTERVAL_MS, e -> {
            int next = (state.getActiveFrameIndex() + 1) % state.getFrames().size();
            state.setActiveFrameIndex(next);
        });

        add(buildControls(), BorderLayout.NORTH);

        thumbRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JScrollPane scroll = new JScrollPane(thumbRow,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setPreferredSize(new Dimension(0, THUMB_SIZE + 24));
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        state.addChangeListener(this::refresh);
        refresh();
    }

    private JPanel buildControls() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

        JButton addBtn = new JButton("+追加");
        addBtn.addActionListener(e -> state.addFrame());
        JButton dupBtn = new JButton("複製");
        dupBtn.addActionListener(e -> state.duplicateFrame());
        deleteBtn = new JButton("削除");
        deleteBtn.addActionListener(e -> state.removeFrame());
        leftBtn = new JButton("←");
        leftBtn.addActionListener(e -> state.moveFrame(state.getActiveFrameIndex(), state.getActiveFrameIndex() - 1));
        rightBtn = new JButton("→");
        rightBtn.addActionListener(e -> state.moveFrame(state.getActiveFrameIndex(), state.getActiveFrameIndex() + 1));

        playBtn = new JButton("▶ 再生");
        playBtn.addActionListener(e -> togglePlayback());

        JCheckBox onionSkin = new JCheckBox("オニオンスキン", state.isOnionSkinEnabled());
        onionSkin.addActionListener(e -> state.setOnionSkinEnabled(onionSkin.isSelected()));

        row.add(addBtn);
        row.add(dupBtn);
        row.add(deleteBtn);
        row.add(leftBtn);
        row.add(rightBtn);
        row.add(playBtn);
        row.add(onionSkin);
        return row;
    }

    private void togglePlayback() {
        if (playbackTimer.isRunning()) {
            playbackTimer.stop();
            playBtn.setText("▶ 再生");
        } else {
            playbackTimer.start();
            playBtn.setText("■ 停止");
        }
    }

    private void refresh() {
        thumbRow.removeAll();
        List<Frame> frames = state.getFrames();
        for (int i = 0; i < frames.size(); i++) {
            thumbRow.add(buildThumbnail(i, frames.get(i)));
        }
        thumbRow.revalidate();
        thumbRow.repaint();

        deleteBtn.setEnabled(frames.size() > 1);
        leftBtn.setEnabled(state.getActiveFrameIndex() > 0);
        rightBtn.setEnabled(state.getActiveFrameIndex() < frames.size() - 1);

        if (frames.size() <= 1 && playbackTimer.isRunning()) {
            togglePlayback();
        }
    }

    private JPanel buildThumbnail(int index, Frame frame) {
        boolean active = index == state.getActiveFrameIndex();
        BufferedImage composite = frame.composite();

        JPanel thumb = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                int w = getWidth(), h = getHeight();
                g2.setColor(new Color(150, 150, 150));
                g2.fillRect(0, 0, w, h);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.drawImage(composite, 0, 0, w, h, 0, 0, composite.getWidth(), composite.getHeight(), null);
            }
        };
        thumb.setPreferredSize(new Dimension(THUMB_SIZE, THUMB_SIZE));
        thumb.setBorder(BorderFactory.createLineBorder(active ? new Color(0x4C, 0x9B, 0xEE) : Color.DARK_GRAY, active ? 2 : 1));
        thumb.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                state.setActiveFrameIndex(index);
            }
        });
        return thumb;
    }
}
