package env;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Registro leve pós-#53: o regime squad-era e seus testes (composição de squads,
 * distância toroidal, soloist livre mais próximo) foram removidos junto com os métodos.
 * Restam os hooks ainda chamados pelo regime único (posição + busy/free do allocator #40).
 */
class SquadCoordinatorTest {

    private SquadCoordinator coordinator() {
        SquadCoordinator sc = new SquadCoordinator();
        sc.init();
        return sc;
    }

    @Test
    void updateAgentPosArmazenaPosicao() {
        SquadCoordinator sc = coordinator();
        sc.update_agent_pos("connectionA4", 10, 20);
        int[] pos = sc.agentPositions.get("connectionA4");
        assertEquals(10, pos[0]);
        assertEquals(20, pos[1]);
    }

    @Test
    void markBusyEFreeAlternamEstado() {
        SquadCoordinator sc = coordinator();
        assertNull(sc.soloistBusy.get("connectionA4")); // mapa começa vazio; ausência ≠ false
        sc.mark_busy("connectionA4");
        assertTrue(sc.soloistBusy.get("connectionA4"));
        sc.mark_free("connectionA4");
        assertFalse(sc.soloistBusy.get("connectionA4"));
    }
}
