package hive;

import jason.asSemantics.*;
import jason.asSyntax.*;

public class AdjacentDirection extends DefaultInternalAction {

    private static final int GRID_WIDTH = 40;
    private static final int GRID_HEIGHT = 40;

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args)
            throws Exception {
        int agX = (int) ((NumberTerm) args[0]).solve();
        int agY = (int) ((NumberTerm) args[1]).solve();
        int tX  = (int) ((NumberTerm) args[2]).solve();
        int tY  = (int) ((NumberTerm) args[3]).solve();

        int dx = wrapDelta(tX - agX, GRID_WIDTH);
        int dy = wrapDelta(tY - agY, GRID_HEIGHT);

        String dir;
        if (dx == 0 && dy == -1)      dir = "n";
        else if (dx == 0 && dy == 1)  dir = "s";
        else if (dx == 1 && dy == 0)  dir = "e";
        else if (dx == -1 && dy == 0) dir = "w";
        else                          dir = "none";

        return un.unifies(args[4], new Atom(dir));
    }

    private int wrapDelta(int d, int size) {
        if (size <= 0) return d;
        if (d > size / 2) return d - size;
        if (d < -size / 2) return d + size;
        return d;
    }
}
