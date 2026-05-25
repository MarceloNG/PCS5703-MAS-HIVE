package hive;

import jason.asSemantics.*;
import jason.asSyntax.*;

public class ConnectCalculator extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args)
            throws Exception {
        int myX = (int) ((NumberTerm) args[0]).solve();
        int myY = (int) ((NumberTerm) args[1]).solve();
        int partnerX = (int) ((NumberTerm) args[2]).solve();
        int partnerY = (int) ((NumberTerm) args[3]).solve();

        int relX = partnerX - myX;
        int relY = partnerY - myY;

        return un.unifies(args[4], ASSyntax.createNumber(relX)) &&
               un.unifies(args[5], ASSyntax.createNumber(relY));
    }
}
