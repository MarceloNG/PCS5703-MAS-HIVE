package hive;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RotationGuardTest {

    @Test
    void maxConsecutiveFails_e3() {
        assertEquals(3, RotationGuard.MAX_CONSECUTIVE_FAILS,
            "Threshold deve ser 3 — sincronizar com literais em connect_protocol.asl (P0: F>=3, P2: F<3)");
    }
}
