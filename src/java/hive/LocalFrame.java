package hive;

/**
 * Fase D / U1 — álgebra de dead-reckoning no frame local do agente, pura e
 * testável (R1, R7, R9) sem rodar a simulação. É a fonte canônica testada da
 * matemática de deslocamento por direção e de tradução por offset; o
 * dead-reckoning em {@code perception.asl} (!dead_reckon_move) espelha
 * {@link #integrate}. Mesmo padrão estático-puro de {@link AdjacentDirection}.
 *
 * <p>Convenção de eixos (igual ao MAPC / SharedMap): n = y-1, s = y+1,
 * e = x+1, w = x-1 (origem no canto superior-esquerdo).
 */
public class LocalFrame {

    /**
     * Integra um move bem-sucedido na direção {@code dir}, retornando o novo
     * offset {x,y}. O chamador só invoca em move bem-sucedido — um move falho é
     * no-op por construção (não chama). Direção desconhecida também é no-op.
     */
    public static int[] integrate(int[] offset, String dir) {
        int x = offset[0], y = offset[1];
        switch (dir) {
            case "n": return new int[]{x, y - 1};
            case "s": return new int[]{x, y + 1};
            case "e": return new int[]{x + 1, y};
            case "w": return new int[]{x - 1, y};
            default:  return new int[]{x, y};
        }
    }

    /**
     * Mapeia um percept relativo {@code (relX,relY)} para o frame local, dada a
     * posição corrente {@code (px,py)} do agente.
     */
    public static int[] toLocal(int px, int py, int relX, int relY) {
        return new int[]{px + relX, py + relY};
    }

    /**
     * Re-keia uma célula {@code (x,y)} por um offset {@code (dX,dY)} — a costura
     * da fusão (R7), sem wrap (offset não-limitado até as dimensões serem
     * conhecidas). {@code SharedMap.translateCells} aplica isto célula-a-célula
     * com wrap toroidal por cima.
     */
    public static int[] translate(int x, int y, int dX, int dY) {
        return new int[]{x + dX, y + dY};
    }
}
