package hive;

/**
 * Lógica pura de alocação de tasks (#40 — núcleo).
 * Sem dependência de CArtAgO: testável com JUnit sem simulador.
 *
 * R1: Valor = reward / max(1, distDisp + distGoal) — agente mais próximo + task
 *     mais lucrativa ganha.
 * R3: scaledBid(computeValue(...)) é o lance a passar para TaskBoard.select_task.
 */
public class TaskAllocator {

    /**
     * Valor de licitação do agente para uma task.
     *
     * @param reward      recompensa da task (inteiro MASSim)
     * @param distDisp    distância Manhattan toroidal do agente ao dispenser do bloco
     * @param distGoal    distância Manhattan toroidal do dispenser à goal-zone alvo
     * @return valor double (maior = melhor); nunca negativo, nunca infinito
     */
    public static double computeValue(int reward, int distDisp, int distGoal) {
        return reward / Math.max(1.0, distDisp + distGoal);
    }

    /**
     * Bid inteiro escalado (×1000) compatível com o leilão do TaskBoard
     * (place_bid usa double, mas select_task usa int p/ simplicidade de Jason term).
     *
     * Preserva a ordenação de computeValue: scaledBid(a) > scaledBid(b) sse a > b.
     */
    public static int scaledBid(double value) {
        return (int) (value * 1000);
    }
}
