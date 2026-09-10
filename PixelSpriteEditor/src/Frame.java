import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * One animation frame: an ordered stack of Layers sharing the same pixel
 * dimensions, plus which one is currently active for drawing. Frames don't
 * know about the rest of the project (EditorState owns the frame list and
 * playback/onion-skin state) -- a Frame only knows how to manage its own
 * layers and flatten them into one displayable image.
 */
public class Frame {
    private final List<Layer> layers = new ArrayList<>();
    private int activeLayerIndex;

    public Frame(int width, int height) {
        layers.add(new Layer("レイヤー1", width, height));
    }

    /** For ProjectIO rebuilding a frame loaded from disk, one addLoadedLayer() call per layer. */
    Frame() {
    }

    void addLoadedLayer(Layer layer) {
        layers.add(layer);
    }

    public List<Layer> getLayers() {
        return layers;
    }

    public int getActiveLayerIndex() {
        return activeLayerIndex;
    }

    public void setActiveLayerIndex(int index) {
        activeLayerIndex = Math.max(0, Math.min(layers.size() - 1, index));
    }

    public Layer getActiveLayer() {
        return layers.get(activeLayerIndex);
    }

    public int getWidth() {
        return layers.get(0).getCanvas().getWidth();
    }

    public int getHeight() {
        return layers.get(0).getCanvas().getHeight();
    }

    public void addLayer() {
        layers.add(new Layer("レイヤー" + (layers.size() + 1), getWidth(), getHeight()));
        activeLayerIndex = layers.size() - 1;
    }

    public void duplicateLayer(int index) {
        Layer copy = layers.get(index).duplicate();
        layers.add(index + 1, copy);
        activeLayerIndex = index + 1;
    }

    /** No-op if this is the only layer -- a frame always needs at least one. */
    public void removeLayer(int index) {
        if (layers.size() <= 1) return;
        layers.remove(index);
        activeLayerIndex = Math.max(0, Math.min(layers.size() - 1, activeLayerIndex));
    }

    public void moveLayer(int from, int to) {
        if (to < 0 || to >= layers.size()) return;
        Layer layer = layers.remove(from);
        layers.add(to, layer);
        activeLayerIndex = to;
    }

    /** A deep copy: new PixelCanvas/History per layer, so editing the copy never touches this frame. */
    public Frame duplicate() {
        Frame copy = new Frame();
        for (Layer layer : layers) copy.layers.add(layer.duplicate());
        copy.activeLayerIndex = activeLayerIndex;
        return copy;
    }

    /** Flattens visible layers bottom-to-top into a single new image; never mutates the layers. */
    public BufferedImage composite() {
        BufferedImage result = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = result.createGraphics();
        for (Layer layer : layers) {
            if (layer.isVisible()) {
                g2.drawImage(layer.getCanvas().getImage(), 0, 0, null);
            }
        }
        g2.dispose();
        return result;
    }
}
