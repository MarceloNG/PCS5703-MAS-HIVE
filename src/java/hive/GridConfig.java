package hive;

/**
 * Fonte única das dimensões do grid toroidal (U4 — Track 3, Fase B).
 *
 * <p>Default 40x40 (config de dev). Override por propriedade de sistema no launch,
 * para a config oficial 70x70:
 * <pre>  -Dhive.grid.width=70 -Dhive.grid.height=70</pre>
 *
 * <p>Substitui os literais "40" antes espalhados em {@code AdjacentDirection},
 * {@code SquadCoordinator} e {@code perception.asl}. Acessível de internal
 * actions e artefatos.
 */
public final class GridConfig {

    private static volatile int width  = readProp("hive.grid.width", 40);
    private static volatile int height = readProp("hive.grid.height", 40);

    private GridConfig() {
    }

    public static int width() {
        return width;
    }

    public static int height() {
        return height;
    }

    /** Override em runtime; valores não-positivos são ignorados. */
    public static void set(int w, int h) {
        if (w > 0) {
            width = w;
        }
        if (h > 0) {
            height = h;
        }
    }

    private static int readProp(String key, int fallback) {
        try {
            String v = System.getProperty(key);
            if (v != null) {
                int parsed = Integer.parseInt(v.trim());
                if (parsed > 0) {
                    return parsed;
                }
            }
        } catch (NumberFormatException ignored) {
            // mantém o fallback
        }
        return fallback;
    }
}
