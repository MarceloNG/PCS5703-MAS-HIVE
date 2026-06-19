package hive;

import jason.asSemantics.*;
import jason.asSyntax.*;
import jason.bb.BeliefBase;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Internal action: verifica se TODOS os task_req(TaskName, DX, DY, _) da BB têm
 * attached(DX, DY) correspondente. Usada na regra "blocos-na-mão → submit" para
 * tarefas multi-requisito (NBlocks > 1).
 *
 * Chamada no corpo do plano:  hive.AllReqsSatisfied(TaskName)
 * Retorna true se todos os requisitos posicionais da task estão attached; false caso contrário.
 */
public class AllReqsSatisfied extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        String taskName = args[0].toString().replaceAll("\"", "");

        BeliefBase bb = ts.getAg().getBB();

        // Coletar posições exigidas pela task: task_req(TaskName, DX, DY, _)
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

        // Coletar posições attached: attached(AX, AY)
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

        return check(reqs, attached);
    }

    /**
     * Verifica se todos os pares (DX,DY) em reqs estão presentes em attached.
     * Método estático puro para testabilidade em JUnit sem dependência de Jason.
     */
    public static boolean check(List<int[]> reqs, List<int[]> attached) {
        for (int[] req : reqs) {
            boolean found = false;
            for (int[] att : attached) {
                if (att[0] == req[0] && att[1] == req[1]) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }
}
