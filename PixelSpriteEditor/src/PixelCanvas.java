import java.awt.image.BufferedImage;

/**
 * The real, low-resolution pixel data for the sprite being edited. Backed
 * directly by a TYPE_INT_ARGB BufferedImage so it can be handed straight to
 * javax.imageio.ImageIO for PNG load/save and to Graphics2D for on-screen
 * drawing, with no separate pixel format to convert between.
 */
public class PixelCanvas {
    private BufferedImage image;

    public PixelCanvas(int width, int height) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    public int getWidth() {
        return image.getWidth();
    }

    public int getHeight() {
        return image.getHeight();
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < getWidth() && y < getHeight();
    }

    /** Returns 0 (fully transparent) for out-of-bounds coordinates. */
    public int getPixel(int x, int y) {
        if (!inBounds(x, y)) return 0;
        return image.getRGB(x, y);
    }

    /** Returns true if the pixel actually changed. Does nothing if out of bounds. */
    public boolean setPixel(int x, int y, int argb) {
        if (!inBounds(x, y)) return false;
        if (image.getRGB(x, y) == argb) return false;
        image.setRGB(x, y, argb);
        return true;
    }

    /** Replaces the canvas with a blank width x height image. */
    public void reset(int width, int height) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    /**
     * Replaces the canvas contents with a copy of the given image (used after
     * loading a PNG). Copies raw ARGB values via getRGB/setRGB rather than
     * Graphics.drawImage(), which composites (even onto a blank destination)
     * and can shift a semi-transparent pixel's color by +-1 per channel due
     * to premultiplied-alpha rounding -- getRGB/setRGB is a plain value copy
     * with no compositing math, so it's exact for every alpha value.
     */
    public void loadFrom(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);
        BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        copy.setRGB(0, 0, width, height, pixels, 0, width);
        image = copy;
    }

    /** The live backing image -- read-only use expected (rendering, saving); mutate via setPixel. */
    public BufferedImage getImage() {
        return image;
    }
}
