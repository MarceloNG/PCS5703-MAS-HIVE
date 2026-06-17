package hive;

import jason.asSemantics.*;
import jason.asSyntax.*;

public class AdjacentDirection extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args)
            throws Exception {
        int agX = (int) ((NumberTerm) args[0]).solve();
        int agY = (int) ((NumberTerm) args[1]).solve();
        int tX  = (int) ((NumberTerm) args[2]).solve();
        int tY  = (int) ((NumberTerm) args[3]).solve();

        String dir = direction(agX, agY, tX, tY,
                               GridConfig.width(), GridConfig.height());

        return un.unifies(args[4], new Atom(dir));
    }

    /**
     * Direção adjacente (n/s/e/w) de (agX,agY) para (tX,tY) num grid toroidal
     * de tamanho w×h; "none" se não for adjacente (inclui origem==alvo).
     * Pura e testável (U4) — toda a matemática de wrap mora aqui.
     */
    public static String direction(int agX, int agY, int tX, int tY, int w, int h) {
        int dx = wrapDelta(tX - agX, w);
        int dy = wrapDelta(tY - agY, h);

        if (dx == 0 && dy == -1) return "n";
        if (dx == 0 && dy == 1)  return "s";
        if (dx == 1 && dy == 0)  return "e";
        if (dx == -1 && dy == 0) return "w";
        return "none";
    }

    /** Menor deslocamento com sentido num eixo toroidal de tamanho size. */
    public static int wrapDelta(int d, int size) {
        if (size <= 0) return d;
        if (d > size / 2) return d - size;
        if (d < -size / 2) return d + size;
        return d;
    }
}
