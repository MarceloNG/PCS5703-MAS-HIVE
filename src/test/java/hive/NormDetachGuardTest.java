package hive;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * Pina o limiar de norm_detach_blocked (FF>=2 em perception.asl) à constante Java
 * DetachGuard.MAX_CONSECUTIVE_FAILS. Evita divergência silenciosa entre o ASL e o Java.
 */
class NormDetachGuardTest {

    @Test
    void normDetachLimiar_coincideComDetachGuard() {
        // perception.asl: "if (norm_detach_fails(FF) & FF >= 2) { +norm_detach_blocked }"
        int limiarNoAsl = 2;
        assertEquals(limiarNoAsl, DetachGuard.MAX_CONSECUTIVE_FAILS,
            "Limiar de norm_detach_blocked (FF>=2 em perception.asl) deve coincidir com " +
            "DetachGuard.MAX_CONSECUTIVE_FAILS — altere os dois juntos");
    }
}
