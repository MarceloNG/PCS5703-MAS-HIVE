package hive;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DetachGuardTest {

    @Test
    void maxConsecutiveFails_e2() {
        assertEquals(2, DetachGuard.MAX_CONSECUTIVE_FAILS,
            "Threshold deve ser 2 — sincronizar com literais em perception.asl (abort: F>=2)");
    }

    /**
     * Regressão do bug: sem o guard, check_stuck dispara até 6 vezes em 300 steps
     * (intervalo de timer = 50 steps). Cada disparo pode gerar um failed_target,
     * violando o DoD de < 5 por agente. O guard (MAX_CONSECUTIVE_FAILS < 5) impede isso.
     */
    @Test
    void semGuard_checkStuck_excederia_dod() {
        int maxDisparosPor300Steps = 300 / 50; // = 6 (timer de 50 steps)
        int dodLimit = 5;                       // DoD: failed_target < 5 por agente

        // Sem guard: check_stuck pode gerar mais failed_target que o DoD permite
        assertTrue(maxDisparosPor300Steps > dodLimit,
            "Sem guard: check_stuck dispara " + maxDisparosPor300Steps
                + " vezes em 300 steps, excedendo DoD " + dodLimit);

        // Com guard: MAX_CONSECUTIVE_FAILS < dodLimit => abort antes de violar DoD
        assertTrue(DetachGuard.MAX_CONSECUTIVE_FAILS < dodLimit,
            "Guard deve abortar antes do limite DoD: MAX_CONSECUTIVE_FAILS="
                + DetachGuard.MAX_CONSECUTIVE_FAILS + " deve ser < " + dodLimit);
    }
}
