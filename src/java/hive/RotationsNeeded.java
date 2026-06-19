package hive;

import jason.asSemantics.*;
import jason.asSyntax.*;
import jason.bb.BeliefBase;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Internal action: calcula a rotação ótima para alinhar os blocos anexados com
 * os requisitos posicionais de uma task antes do submit.
 *
 * Chamada no corpo do plano:  hive.RotationsNeeded(TaskName, R, Dir)
 * Unifica R com o número de passos e Dir com "cw" ou "ccw".
 * 3 CW equivalem a 1 CCW — o dir ótimo é retornado diretamente.
 * Falha se os blocos já estão alinhados (R=0) ou se nenhuma rotação ajuda.
 *
 * Rotação CW:  (dx, dy) → (−dy, dx)   [coordenadas MASSim: X=leste, Y=sul]
 * Rotação CCW: (dx, dy) → ( dy, −dx)  (inverso de CW)
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
        int optCount = optimalCount(r);
        String dir = optimalDir(r);
        return un.unifies(args[1], ASSyntax.createNumber(optCount)) &&
               un.unifies(args[2], ASSyntax.createAtom(dir));
    }

    /**
     * Retorna o número mínimo de rotações CW (1–3) para alinhar attached com reqs.
     * Retorna -1 se blocos já alinhados ou se nenhuma rotação produz alinhamento.
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

    /** Número ótimo de passos: 3 CW → 1 CCW, demais ficam iguais. */
    static int optimalCount(int cwRotations) {
        return cwRotations == 3 ? 1 : cwRotations;
    }

    /** Direção ótima: 3 CW → "ccw"; 1 ou 2 CW → "cw". */
    static String optimalDir(int cwRotations) {
        return cwRotations == 3 ? "ccw" : "cw";
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
