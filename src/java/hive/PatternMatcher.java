package hive;

import jason.asSemantics.*;
import jason.asSyntax.*;
import jason.bb.BeliefBase;

public class PatternMatcher extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args)
            throws Exception {
        ListTerm reqs = (ListTerm) args[0];
        BeliefBase bb = ts.getAg().getBB();

        for (Term req : reqs) {
            Structure r = (Structure) req;
            int rx = (int) ((NumberTerm) r.getTerm(0)).solve();
            int ry = (int) ((NumberTerm) r.getTerm(1)).solve();

            Literal check = ASSyntax.createLiteral("my_attached",
                ASSyntax.createNumber(rx), ASSyntax.createNumber(ry));
            if (bb.contains(check) == null) {
                return un.unifies(args[1], Literal.parseLiteral("false"));
            }
        }
        return un.unifies(args[1], Literal.parseLiteral("true"));
    }
}
