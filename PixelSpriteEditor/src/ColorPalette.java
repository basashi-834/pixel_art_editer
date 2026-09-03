import java.util.List;

/**
 * A named list of colors the user can pick from. The single built-in
 * palette (see ColorPanel) is fixed -- its colors can't be added to or
 * removed, and it can't be deleted -- while user-created palettes are
 * fully editable. Not persisted to disk; lives for the app session only.
 */
public class ColorPalette {
    private final String name;
    private final boolean builtIn;
    private final List<Integer> colors;

    public ColorPalette(String name, boolean builtIn, List<Integer> colors) {
        this.name = name;
        this.builtIn = builtIn;
        this.colors = colors;
    }

    public String getName() {
        return name;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    public List<Integer> getColors() {
        return colors;
    }

    public void addColor(int argb) {
        if (!colors.contains(argb)) {
            colors.add(argb);
        }
    }

    public void removeColor(int argb) {
        colors.remove((Integer) argb);
    }

    /** JComboBox renders items via toString() by default. */
    @Override
    public String toString() {
        return name;
    }
}
