package hive;

import jason.asSemantics.*;
import jason.asSyntax.*;
import jason.bb.BeliefBase;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Internal action: calcula o número mínimo de rotações CW (1-3) para alinhar os
 * blocos anexados com os requisitos posicionais de uma task.
 *
 * Chamada no corpo do plano:  hive.RotationsNeeded(TaskName, R)
 * Unifica R com 1, 2 ou 3 se alguma rotação CW alinhar os blocos.
 * Falha se os blocos já estão alinhados (R=0) ou se nenhuma rotação ajuda.
 *
 * Rotação CW em coordenadas MASSim (X=leste, Y=sul): (dx, dy) → (−dy, dx)
 */
public class RotationsNeeded extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        String taskName = args[0].toString().replaceAll("\"", "");

        BeliefBase bb = ts.getAg().getBB();

        List<int[]> reqs = new ArrayList<>();
        Literal reqPattern = ASSyntax.parseLiteral("task_req(" + taskName + ",_,_,_)");
        Iterator<Literal> reqIt = bb.getCandidateBeliefs(reqPattern, un);
        if (reqIt != null) {
            while (reqIt.hasNext()) {
                Literal l = reqIt.next();
                if (l.getFunctor().equals("task_req") && l.getArity() == 4) {
                    String functor = l.getTerm(0).toString().replaceAll("\"", "");
                    if (functor.equals(taskName)) {
                        int dx = (int) ((NumberTerm) l.getTerm(1)).solve();
                        int dy = (int) ((NumberTerm) l.getTerm(2)).solve();
                        reqs.add(new int[]{dx, dy});
                    }
                }
            }
        }

        List<int[]> attached = new ArrayList<>();
        Literal attPattern = ASSyntax.parseLiteral("attached(_,_)");
        Iterator<Literal> attIt = bb.getCandidateBeliefs(attPattern, un);
        if (attIt != null) {
            while (attIt.hasNext()) {
                Literal l = attIt.next();
                if (l.getFunctor().equals("attached") && l.getArity() == 2) {
                    int ax = (int) ((NumberTerm) l.getTerm(0)).solve();
                    int ay = (int) ((NumberTerm) l.getTerm(1)).solve();
                    attached.add(new int[]{ax, ay});
                }
            }
        }

        int r = needed(reqs, attached);
        if (r < 0) return false;
        return un.unifies(args[1], ASSyntax.createNumber(r));
    }

    /**
     * Retorna o número mínimo de rotações CW (1–3) para alinhar attached com reqs.
     * Retorna -1 se: blocos já alinhados (R=0, AllReqsSatisfied trata esse caso)
     *                 ou se nenhuma rotação produz o alinhamento.
     */
    public static int needed(List<int[]> reqs, List<int[]> attached) {
        if (AllReqsSatisfied.check(reqs, attached)) return -1;

        List<int[]> rotated = attached;
        for (int r = 1; r <= 3; r++) {
            rotated = rotateCW(rotated);
            if (AllReqsSatisfied.check(reqs, rotated)) return r;
        }
        return -1;
    }

    /** Aplica 1 rotação horária: (dx, dy) → (−dy, dx) */
    static List<int[]> rotateCW(List<int[]> positions) {
        List<int[]> result = new ArrayList<>(positions.size());
        for (int[] p : positions) {
            result.add(new int[]{-p[1], p[0]});
        }
        return result;
    }
}
