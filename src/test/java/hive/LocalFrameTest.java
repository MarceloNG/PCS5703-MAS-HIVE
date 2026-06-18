package hive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/**
 * Fase D / U1 — cobre a álgebra de dead-reckoning (R1/AE1) e a tradução por
 * offset (R7), em ms sem rodar a simulação (R9). A versão .asl
 * (!dead_reckon_move) espelha {@link LocalFrame#integrate}.
 */
class LocalFrameTest {

    // --- integrate: deltas por direção ---

    @Test
    void integrate_cadaDirecao() {
        assertArrayEquals(new int[]{0, -1}, LocalFrame.integrate(new int[]{0, 0}, "n"));
        assertArrayEquals(new int[]{0, 1},  LocalFrame.integrate(new int[]{0, 0}, "s"));
        assertArrayEquals(new int[]{1, 0},  LocalFrame.integrate(new int[]{0, 0}, "e"));
        assertArrayEquals(new int[]{-1, 0}, LocalFrame.integrate(new int[]{0, 0}, "w"));
    }

    /** AE1: sequência n/e/s/w (só sucessos) parte de (0,0) e volta a (0,0). */
    @Test
    void integrate_sequenciaNESW_voltaOrigem() {
        int[] p = {0, 0};
        p = LocalFrame.integrate(p, "n"); // (0,-1)
        p = LocalFrame.integrate(p, "e"); // (1,-1)
        p = LocalFrame.integrate(p, "s"); // (1,0)
        p = LocalFrame.integrate(p, "w"); // (0,0)
        assertArrayEquals(new int[]{0, 0}, p);
    }

    /**
     * AE1 (a parte que o keystone errava antes do review): um move FALHO no meio
     * é no-op. Modelado pelo chamador não invocando integrate no move falho — o
     * offset reflete só os moves bem-sucedidos.
     */
    @Test
    void integrate_moveFalho_eNoOp() {
        int[] p = {0, 0};
        p = LocalFrame.integrate(p, "e");      // sucesso -> (1,0)
        // move 'n' FALHA: o chamador NÃO chama integrate -> offset inalterado
        p = LocalFrame.integrate(p, "e");      // sucesso -> (2,0)
        assertArrayEquals(new int[]{2, 0}, p);
    }

    /** Direção desconhecida (resultado não-move) é no-op defensivo. */
    @Test
    void integrate_direcaoDesconhecida_eNoOp() {
        assertArrayEquals(new int[]{3, 4}, LocalFrame.integrate(new int[]{3, 4}, "skip"));
    }

    // --- toLocal: percept relativo -> frame local ---

    @Test
    void toLocal_mapeiaPercept() {
        assertArrayEquals(new int[]{7, 1}, LocalFrame.toLocal(5, 3, 2, -2));
    }

    // --- translate: re-key por offset (round-trip) ---

    @Test
    void translate_offsetZero_naoMuda() {
        assertArrayEquals(new int[]{5, 3}, LocalFrame.translate(5, 3, 0, 0));
    }

    @Test
    void translate_inversaVolta() {
        int[] t = LocalFrame.translate(5, 3, 4, -2);   // (9,1)
        assertArrayEquals(new int[]{9, 1}, t);
        int[] back = LocalFrame.translate(t[0], t[1], -4, 2);
        assertArrayEquals(new int[]{5, 3}, back);
    }
}
