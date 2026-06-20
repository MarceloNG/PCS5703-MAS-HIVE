package hive;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * Testes da lógica pura de alocação de tasks (#40 — R1-R5).
 * Sem CArtAgO: apenas Java pura, roda em ms sem simulador.
 */
class TaskAllocatorTest {

    // -------------------------------------------------------
    // R1 — Rank por valor: reward / max(1, distDisp + distGoal)
    // -------------------------------------------------------

    @Test
    void testValorMaisAltoComMenorDistancia() {
        // Mesma reward, agente próximo vale mais
        double vProximo = TaskAllocator.computeValue(10, 2, 3);   // dist total = 5
        double vDistante = TaskAllocator.computeValue(10, 5, 5);  // dist total = 10
        assertTrue(vProximo > vDistante,
            "Agente mais próximo deve ter valor maior (mesma reward, menor dist)");
    }

    @Test
    void testRewardMaisAltaGanhaComMesmaDist() {
        double vAlta  = TaskAllocator.computeValue(20, 4, 1);
        double vBaixa = TaskAllocator.computeValue(10, 4, 1);
        assertTrue(vAlta > vBaixa,
            "Task com maior reward deve ter valor maior (mesma distância)");
    }

    @Test
    void testProporçãoIgualMesmoValor() {
        // reward=10/dist=5 == reward=20/dist=10 (razão idêntica)
        double v1 = TaskAllocator.computeValue(10, 5, 0);
        double v2 = TaskAllocator.computeValue(20, 10, 0);
        assertEquals(v1, v2, 1e-9,
            "Proporções iguais devem gerar o mesmo valor");
    }

    // -------------------------------------------------------
    // R2 — Dist zero não explode (denominador mínimo = 1)
    // -------------------------------------------------------

    @Test
    void testDistanciaZeroNaoExplode() {
        double v = TaskAllocator.computeValue(10, 0, 0);
        assertEquals(10.0, v, 1e-9,
            "Dist zero: valor = reward / 1 (sem divisão por zero)");
    }

    @Test
    void testDistanciaUmRetornaReward() {
        double v = TaskAllocator.computeValue(5, 1, 0);
        assertEquals(5.0, v, 1e-9);
    }

    // -------------------------------------------------------
    // R3 — Agente mais próximo tem bid mais alto
    // -------------------------------------------------------

    @Test
    void testAgenteProximoTemBidMaisAlto() {
        // Dois agentes para a mesma task (mesma reward), distâncias diferentes
        double bidA = TaskAllocator.computeValue(10, 1, 2);  // total dist = 3
        double bidB = TaskAllocator.computeValue(10, 5, 5);  // total dist = 10
        assertTrue(bidA > bidB,
            "Agente próximo (dist=3) deve superar agente distante (dist=10)");
    }

    @Test
    void testScaledBidPositivo() {
        double v = TaskAllocator.computeValue(10, 2, 3);
        int scaled = TaskAllocator.scaledBid(v);
        assertTrue(scaled > 0, "scaledBid deve ser positivo para valor > 0");
    }

    @Test
    void testScaledBidMantémOrdem() {
        // Escalar por ×1000 deve preservar a ordenação dos bids
        double v1 = TaskAllocator.computeValue(10, 2, 3);
        double v2 = TaskAllocator.computeValue(10, 5, 5);
        assertTrue(TaskAllocator.scaledBid(v1) > TaskAllocator.scaledBid(v2),
            "scaledBid deve manter a ordem relativa dos valores");
    }

    // -------------------------------------------------------
    // R5 — Single-block processado normalmente (coalização tamanho 1)
    // -------------------------------------------------------

    @Test
    void testTaskSingleBlockTemValorPositivo() {
        // Single-block task: reward normal, distâncias plausíveis
        double v = TaskAllocator.computeValue(10, 3, 4);
        assertTrue(v > 0, "Task de 1 bloco deve gerar valor positivo");
        assertTrue(Double.isFinite(v), "Valor deve ser finito");
    }

    @Test
    void testRewardZeroGeraValorZero() {
        // Task sem reward (edge case) → valor 0
        double v = TaskAllocator.computeValue(0, 3, 4);
        assertEquals(0.0, v, 1e-9,
            "Reward=0 → valor=0 independente da distância");
    }
}
