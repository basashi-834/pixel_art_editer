import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

/**
 * Renders the active frame's composited layers at the current zoom with
 * nearest-neighbor scaling, an optional checkerboard behind transparent
 * pixels, an optional 1px pixel grid, an optional 16x16 block guide, an
 * optional onion-skin of the previous frame, the line tool's live preview,
 * a selection-rectangle outline, a floating paste preview, and a
 * hover-pixel highlight -- none of which are ever part of the saved PNG,
 * only this view. Also owns pointer input: painting (delegated to the
 * active tool), panning (middle-drag or Space+drag), wheel-zoom anchored at
 * the cursor, and paste-mode clicks (which bypass the active tool).
 */
public class CanvasPanel extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {

    private static final int CHECKER_CELL = 8;

    private final EditorState state;

    private boolean panning;
    private int panLastX, panLastY;
    private boolean painting;
    private boolean spaceHeld;
    private int hoverX, hoverY;
    private boolean hoverVisible;

    /** Lets EditorWindow show the hovered pixel coordinates in the status bar. */
    public interface HoverListener {
        void onPixelHovered(int x, int y, boolean inBounds);
    }

    private HoverListener hoverListener;

    public CanvasPanel(EditorState state) {
        this.state = state;
        setBackground(new Color(0x20, 0x20, 0x20));
        setFocusable(true);
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
        addKeyListener(this);
        state.addChangeListener(this::repaint);
    }

    public void setHoverListener(HoverListener listener) {
        this.hoverListener = listener;
    }

    /** Centers the sprite in the current viewport. Call after the panel has a real size (componentShown) and after New/Open. */
    public void centerCamera() {
        state.centerCamera(getWidth(), getHeight());
        repaint();
    }

    // ---- rendering ------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        PixelCanvas canvas = state.getCanvas();
        int zoom = state.getZoom();
        Rectangle rect = new Rectangle(
                (int) Math.round(state.getCamOffsetX()),
                (int) Math.round(state.getCamOffsetY()),
                canvas.getWidth() * zoom,
                canvas.getHeight() * zoom);

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            if (state.isShowTransparencyChecker()) {
                drawCheckerboard(g2, rect);
            }

            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            if (state.isOnionSkinEnabled() && state.getPreviousFrame() != null) {
                drawImageScaled(g2, state.getPreviousFrame().composite(), rect, 0.35f);
            }
            drawImageScaled(g2, state.getCompositeImage(), rect, 1.0f);

            if (state.isShowPixelGrid() && zoom >= 4) {
                g2.setColor(new Color(128, 128, 128, 90));
                drawGrid(g2, rect, zoom, 1);
            }
            if (state.isShow16Guide()) {
                g2.setColor(new Color(255, 70, 70, 210));
                drawGrid(g2, rect, zoom, 16);
            }
            if (state.hasPreview()) {
                drawPreviewLine(g2, rect, zoom);
            }
            if (state.hasSelection()) {
                drawSelection(g2, rect, zoom);
            }
            if (state.isPasteModeActive()) {
                drawPastePreview(g2, rect, zoom);
            }
            if (hoverVisible) {
                drawHoverHighlight(g2, rect, zoom);
            }
        } finally {
            g2.dispose();
        }
    }

    private static void drawImageScaled(Graphics2D g2, BufferedImage image, Rectangle rect, float alpha) {
        Graphics2D gi = (Graphics2D) g2.create();
        if (alpha < 1.0f) {
            gi.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        }
        gi.drawImage(image, rect.x, rect.y, rect.x + rect.width, rect.y + rect.height,
                0, 0, image.getWidth(), image.getHeight(), null);
        gi.dispose();
    }

    private void drawCheckerboard(Graphics2D g2, Rectangle rect) {
        Graphics2D gc = (Graphics2D) g2.create();
        gc.clip(rect);
        Color light = new Color(200, 200, 200);
        Color dark = new Color(150, 150, 150);
        for (int y = rect.y; y < rect.y + rect.height; y += CHECKER_CELL) {
            for (int x = rect.x; x < rect.x + rect.width; x += CHECKER_CELL) {
                int col = Math.floorDiv(x - rect.x, CHECKER_CELL);
                int row = Math.floorDiv(y - rect.y, CHECKER_CELL);
                gc.setColor(((col + row) % 2 == 0) ? light : dark);
                gc.fillRect(x, y, CHECKER_CELL, CHECKER_CELL);
            }
        }
        gc.dispose();
    }

    private void drawGrid(Graphics2D g2, Rectangle rect, int zoom, int step) {
        PixelCanvas canvas = state.getCanvas();
        for (int x = 0; x <= canvas.getWidth(); x += step) {
            int sx = rect.x + x * zoom;
            g2.drawLine(sx, rect.y, sx, rect.y + rect.height);
        }
        for (int y = 0; y <= canvas.getHeight(); y += step) {
            int sy = rect.y + y * zoom;
            g2.drawLine(rect.x, sy, rect.x + rect.width, sy);
        }
    }

    private void drawPreviewLine(Graphics2D g2, Rectangle rect, int zoom) {
        int argb = state.getCurrentColorArgb();
        g2.setColor(new Color(argb, true));
        LineUtil.forEachLinePixel(
                state.getPreviewX0(), state.getPreviewY0(),
                state.getPreviewX1(), state.getPreviewY1(),
                (px, py) -> g2.fillRect(rect.x + px * zoom, rect.y + py * zoom, zoom, zoom));
    }

    private void drawSelection(Graphics2D g2, Rectangle rect, int zoom) {
        Rectangle sel = state.getSelection();
        int sx = rect.x + sel.x * zoom;
        int sy = rect.y + sel.y * zoom;
        int sw = sel.width * zoom;
        int sh = sel.height * zoom;
        Graphics2D gs = (Graphics2D) g2.create();
        gs.setStroke(new java.awt.BasicStroke(1, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_MITER,
                1, new float[] {4, 4}, 0));
        gs.setColor(Color.WHITE);
        gs.drawRect(sx, sy, sw, sh);
        gs.setColor(Color.BLACK);
        gs.setStroke(new java.awt.BasicStroke(1, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_MITER,
                1, new float[] {4, 4}, 4));
        gs.drawRect(sx, sy, sw, sh);
        gs.dispose();
    }

    private void drawPastePreview(Graphics2D g2, Rectangle rect, int zoom) {
        BufferedImage clip = state.getClipboardImage();
        if (clip == null) return;
        Rectangle pasteRect = new Rectangle(
                rect.x + state.getPasteX() * zoom,
                rect.y + state.getPasteY() * zoom,
                clip.getWidth() * zoom,
                clip.getHeight() * zoom);
        drawImageScaled(g2, clip, pasteRect, 0.75f);
        g2.setColor(Color.WHITE);
        g2.drawRect(pasteRect.x, pasteRect.y, pasteRect.width, pasteRect.height);
    }

    private void drawHoverHighlight(Graphics2D g2, Rectangle rect, int zoom) {
        int x0 = rect.x + hoverX * zoom;
        int y0 = rect.y + hoverY * zoom;
        g2.setColor(Color.BLACK);
        g2.drawRect(x0, y0, Math.max(0, zoom - 1), Math.max(0, zoom - 1));
        if (zoom >= 6) {
            g2.setColor(Color.WHITE);
            g2.drawRect(x0 + 1, y0 + 1, Math.max(0, zoom - 3), Math.max(0, zoom - 3));
        }
    }

    // ---- coordinate mapping -----------------------------------------------

    private int pixelXAt(int screenX) {
        return (int) Math.floor((screenX - state.getCamOffsetX()) / state.getZoom());
    }

    private int pixelYAt(int screenY) {
        return (int) Math.floor((screenY - state.getCamOffsetY()) / state.getZoom());
    }

    public void setSpaceHeld(boolean held) {
        spaceHeld = held;
    }

    // ---- mouse: painting / panning / pasting -------------------------------

    @Override
    public void mousePressed(MouseEvent e) {
        requestFocusInWindow();

        if (state.isPasteModeActive()) {
            if (e.getButton() == MouseEvent.BUTTON1) {
                state.updatePastePosition(pixelXAt(e.getX()), pixelYAt(e.getY()));
                state.commitPaste();
            }
            return;
        }

        boolean middle = e.getButton() == MouseEvent.BUTTON2;
        boolean left = e.getButton() == MouseEvent.BUTTON1;

        if (middle || (spaceHeld && left)) {
            panning = true;
            panLastX = e.getX();
            panLastY = e.getY();
            return;
        }
        if (left) {
            painting = true;
            state.getCurrentTool().onPress(state, pixelXAt(e.getX()), pixelYAt(e.getY()));
            repaint();
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (state.isPasteModeActive()) {
            state.updatePastePosition(pixelXAt(e.getX()), pixelYAt(e.getY()));
            repaint();
            return;
        }
        if (panning) {
            state.panBy(e.getX() - panLastX, e.getY() - panLastY);
            panLastX = e.getX();
            panLastY = e.getY();
            repaint();
            return;
        }
        updateHover(e.getX(), e.getY());
        if (painting) {
            state.getCurrentTool().onDrag(state, pixelXAt(e.getX()), pixelYAt(e.getY()));
            repaint();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (panning) {
            panning = false;
            return;
        }
        if (painting) {
            state.getCurrentTool().onRelease(state, pixelXAt(e.getX()), pixelYAt(e.getY()));
            painting = false;
            repaint();
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (state.isPasteModeActive()) {
            state.updatePastePosition(pixelXAt(e.getX()), pixelYAt(e.getY()));
            repaint();
            return;
        }
        updateHover(e.getX(), e.getY());
    }

    @Override
    public void mouseExited(MouseEvent e) {
        if (hoverVisible) {
            hoverVisible = false;
            repaint();
        }
    }

    private void updateHover(int screenX, int screenY) {
        int px = pixelXAt(screenX);
        int py = pixelYAt(screenY);
        boolean inBounds = state.getCanvas().inBounds(px, py);
        if (hoverListener != null) hoverListener.onPixelHovered(px, py, inBounds);
        if (px != hoverX || py != hoverY || hoverVisible != inBounds) {
            hoverX = px;
            hoverY = py;
            hoverVisible = inBounds;
            repaint();
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        // Only Ctrl+wheel zooms; a plain wheel scroll does nothing (there's no
        // scrollable content here, and the user asked wheel-alone not to zoom).
        if (!e.isControlDown()) return;
        if (e.getWheelRotation() < 0) {
            state.zoomIn(e.getX(), e.getY());
        } else if (e.getWheelRotation() > 0) {
            state.zoomOut(e.getX(), e.getY());
        }
    }

    // ---- space-to-pan, escape-to-cancel-paste ------------------------------

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
            spaceHeld = true;
        } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            state.cancelPaste();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
            spaceHeld = false;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }
}
