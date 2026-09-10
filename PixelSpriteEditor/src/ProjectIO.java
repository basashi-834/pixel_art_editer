import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/**
 * Saves/loads a whole project (every frame's every layer, plus any
 * user-created color palettes) as a single .pxproj file -- a plain zip
 * (java.util.zip is JDK standard library, so this needs no third-party
 * dependency) containing one PNG per layer plus a manifest.properties
 * describing frame/layer structure and palettes. Reuses the same
 * ImageIO-based PNG encoding as the regular single-image export, so a
 * project file's images are exactly as inspectable as any other PNG.
 */
public final class ProjectIO {

    private ProjectIO() {
    }

    public static void save(EditorState state, File file) throws IOException {
        List<Frame> frames = state.getFrames();
        StringBuilder manifest = new StringBuilder();
        manifest.append("frameCount=").append(frames.size()).append('\n');

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            for (int fi = 0; fi < frames.size(); fi++) {
                Frame frame = frames.get(fi);
                List<Layer> layers = frame.getLayers();
                manifest.append("frame").append(fi).append(".layerCount=").append(layers.size()).append('\n');
                manifest.append("frame").append(fi).append(".activeLayer=").append(frame.getActiveLayerIndex()).append('\n');

                for (int li = 0; li < layers.size(); li++) {
                    Layer layer = layers.get(li);
                    String key = "frame" + fi + ".layer" + li;
                    manifest.append(key).append(".name=").append(escape(layer.getName())).append('\n');
                    manifest.append(key).append(".visible=").append(layer.isVisible()).append('\n');

                    zip.putNextEntry(new ZipEntry(key + ".png"));
                    ImageIO.write(layer.getCanvas().getImage(), "png", zip);
                    zip.closeEntry();
                }
            }

            List<ColorPalette> customPalettes = new ArrayList<>();
            for (ColorPalette p : state.getPalettes()) {
                if (!p.isBuiltIn()) customPalettes.add(p);
            }
            manifest.append("paletteCount=").append(customPalettes.size()).append('\n');
            for (int pi = 0; pi < customPalettes.size(); pi++) {
                ColorPalette palette = customPalettes.get(pi);
                manifest.append("palette").append(pi).append(".name=").append(escape(palette.getName())).append('\n');
                StringBuilder colors = new StringBuilder();
                for (int c : palette.getColors()) {
                    if (colors.length() > 0) colors.append(',');
                    colors.append(Integer.toHexString(c));
                }
                manifest.append("palette").append(pi).append(".colors=").append(colors).append('\n');
            }

            zip.putNextEntry(new ZipEntry("manifest.properties"));
            zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    public static void load(EditorState state, File file) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            Properties manifest = new Properties();
            ZipEntry manifestEntry = zip.getEntry("manifest.properties");
            if (manifestEntry == null) throw new IOException("Not a PixelSpriteEditor project file (manifest.properties missing)");
            try (InputStream in = zip.getInputStream(manifestEntry)) {
                manifest.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }

            int frameCount = Integer.parseInt(manifest.getProperty("frameCount", "0"));
            if (frameCount <= 0) throw new IOException("Project file has no frames");

            List<Frame> frames = new ArrayList<>();
            for (int fi = 0; fi < frameCount; fi++) {
                int layerCount = Integer.parseInt(manifest.getProperty("frame" + fi + ".layerCount", "0"));
                Frame frame = new Frame();
                for (int li = 0; li < layerCount; li++) {
                    String key = "frame" + fi + ".layer" + li;
                    String name = manifest.getProperty(key + ".name", "レイヤー" + (li + 1));
                    boolean visible = Boolean.parseBoolean(manifest.getProperty(key + ".visible", "true"));

                    ZipEntry pngEntry = zip.getEntry(key + ".png");
                    if (pngEntry == null) throw new IOException("Missing layer image: " + key + ".png");
                    BufferedImage img;
                    try (InputStream in = zip.getInputStream(pngEntry)) {
                        img = ImageIO.read(in);
                    }
                    PixelCanvas canvas = new PixelCanvas(img.getWidth(), img.getHeight());
                    canvas.loadFrom(img);
                    frame.addLoadedLayer(new Layer(name, visible, canvas));
                }
                if (frame.getLayers().isEmpty()) throw new IOException("Frame " + fi + " has no layers");
                frame.setActiveLayerIndex(Integer.parseInt(manifest.getProperty("frame" + fi + ".activeLayer", "0")));
                frames.add(frame);
            }

            List<ColorPalette> customPalettes = new ArrayList<>();
            int paletteCount = Integer.parseInt(manifest.getProperty("paletteCount", "0"));
            for (int pi = 0; pi < paletteCount; pi++) {
                String name = manifest.getProperty("palette" + pi + ".name", "パレット" + (pi + 1));
                String colorsCsv = manifest.getProperty("palette" + pi + ".colors", "");
                List<Integer> colors = new ArrayList<>();
                for (String hex : colorsCsv.split(",")) {
                    if (!hex.isEmpty()) colors.add((int) Long.parseLong(hex, 16));
                }
                customPalettes.add(new ColorPalette(unescape(name), false, colors));
            }

            state.replaceProject(frames, customPalettes, file.getAbsolutePath());
        }
    }

    // Properties values can't contain raw newlines; palette/layer names realistically never will,
    // but escape defensively so a stray one doesn't corrupt the manifest instead of just looking odd.
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\n", "\\n");
    }

    private static String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\\\", "\\");
    }
}
