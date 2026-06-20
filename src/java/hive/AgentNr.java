package hive;

import jason.asSemantics.*;
import jason.asSyntax.*;

/**
 * IA: retorna o índice numérico do agente extraído do nome (ex: "connectionA3" → 3).
 * Usada para escalonar retries periódicos por agente e evitar tempestades síncronas
 * (ex: (N mod 10) == (AgNr mod 10) em vez de (N mod 10) == 0 para todos).
 *
 * Chamada: hive.AgentNr(Nr)
 */
public class AgentNr extends DefaultInternalAction {

    public static int extractNr(String agentName) {
        return Integer.parseInt(agentName.replaceAll("[^0-9]", ""));
    }

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        int nr = extractNr(ts.getAgArch().getAgName());
        return un.unifies(args[0], ASSyntax.createNumber(nr));
    }
}
