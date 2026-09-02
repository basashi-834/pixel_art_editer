import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;

/**
 * A dark color scheme for Swing's built-in Metal look and feel, applied
 * instead of the platform look and feel so the app has a consistent dark
 * appearance close to the earlier C++/C# versions, without pulling in a
 * third-party look-and-feel library. MetalLookAndFeel is part of the JDK
 * and supports swapping its whole color scheme this way (a "MetalTheme").
 *
 * Apply with {@link #install()} before any Swing component is created.
 */
public class DarkTheme extends DefaultMetalTheme {

    private static final ColorUIResource ACCENT = new ColorUIResource(0x3D, 0x8B, 0xE0);
    private static final ColorUIResource ACCENT_DIM = new ColorUIResource(0x2E, 0x69, 0xAD);
    private static final ColorUIResource ACCENT_BRIGHT = new ColorUIResource(0x4C, 0x9B, 0xEE);

    private static final ColorUIResource BORDER_DARK = new ColorUIResource(0x1A, 0x1A, 0x1A);
    private static final ColorUIResource CONTROL_MID = new ColorUIResource(0x3A, 0x3A, 0x3A);
    private static final ColorUIResource CONTROL_LIGHT = new ColorUIResource(0x48, 0x48, 0x48);

    private static final ColorUIResource TEXT = new ColorUIResource(0xE8, 0xE8, 0xE8);
    private static final ColorUIResource DISABLED_TEXT = new ColorUIResource(0x8A, 0x8A, 0x8A);
    private static final ColorUIResource WINDOW_BG = new ColorUIResource(0x2B, 0x2B, 0x2B);
    private static final ColorUIResource FIELD_BG = new ColorUIResource(0x1E, 0x1E, 0x1E);

    public static void install() {
        MetalLookAndFeel.setCurrentTheme(new DarkTheme());
        try {
            javax.swing.UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (Exception e) {
            // Fall back to whatever default look and feel is already active.
        }
        // A few widgets read these keys directly rather than through the
        // MetalTheme color hooks below, so patch them explicitly.
        javax.swing.UIManager.put("Panel.background", WINDOW_BG);
        javax.swing.UIManager.put("OptionPane.background", WINDOW_BG);
        javax.swing.UIManager.put("OptionPane.messageForeground", TEXT);
        javax.swing.UIManager.put("TextField.background", FIELD_BG);
        javax.swing.UIManager.put("TextField.foreground", TEXT);
        javax.swing.UIManager.put("Spinner.background", FIELD_BG);
        javax.swing.UIManager.put("ToolBar.background", WINDOW_BG);
        javax.swing.UIManager.put("ToolBar.dockingBackground", WINDOW_BG);
    }

    @Override
    public String getName() {
        return "Dark";
    }

    @Override
    protected ColorUIResource getPrimary1() {
        return ACCENT_DIM;
    }

    @Override
    protected ColorUIResource getPrimary2() {
        return ACCENT;
    }

    @Override
    protected ColorUIResource getPrimary3() {
        return ACCENT_BRIGHT;
    }

    @Override
    protected ColorUIResource getSecondary1() {
        return BORDER_DARK;
    }

    @Override
    protected ColorUIResource getSecondary2() {
        return CONTROL_MID;
    }

    @Override
    protected ColorUIResource getSecondary3() {
        return CONTROL_LIGHT;
    }

    @Override
    public ColorUIResource getControlTextColor() {
        return TEXT;
    }

    @Override
    public ColorUIResource getSystemTextColor() {
        return TEXT;
    }

    @Override
    public ColorUIResource getUserTextColor() {
        return TEXT;
    }

    @Override
    public ColorUIResource getMenuForeground() {
        return TEXT;
    }

    @Override
    public ColorUIResource getMenuDisabledForeground() {
        return DISABLED_TEXT;
    }

    @Override
    public ColorUIResource getInactiveControlTextColor() {
        return DISABLED_TEXT;
    }

    @Override
    public ColorUIResource getInactiveSystemTextColor() {
        return DISABLED_TEXT;
    }

    @Override
    public ColorUIResource getWindowBackground() {
        return WINDOW_BG;
    }

    @Override
    public ColorUIResource getDesktopColor() {
        return WINDOW_BG;
    }

    @Override
    public ColorUIResource getWhite() {
        return CONTROL_LIGHT;
    }

    @Override
    public ColorUIResource getBlack() {
        return BORDER_DARK;
    }
}
