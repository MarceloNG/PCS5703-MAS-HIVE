package hive;

import jason.asSemantics.*;
import jason.asSyntax.*;

public class DirectionCalculator extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args)
            throws Exception {
        int fromX = (int) ((NumberTerm) args[0]).solve();
        int fromY = (int) ((NumberTerm) args[1]).solve();
        int toX   = (int) ((NumberTerm) args[2]).solve();
        int toY   = (int) ((NumberTerm) args[3]).solve();

        int dx = toX - fromX;
        int dy = toY - fromY;

        String dir;
        if (dx == 0 && dy == 0) {
            dir = "skip";
        } else if (Math.abs(dx) >= Math.abs(dy)) {
            dir = dx > 0 ? "e" : "w";
        } else {
            dir = dy > 0 ? "s" : "n";
        }

        return un.unifies(args[4], new Atom(dir));
    }
}
