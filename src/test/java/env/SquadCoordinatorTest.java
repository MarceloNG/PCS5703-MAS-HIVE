package env;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cartago.OpFeedbackParam;
import org.junit.jupiter.api.Test;

/**
 * Lógica de coordenação do SquadCoordinator (backfill Track 1, P2): composição
 * de squads, distância toroidal e escolha do soloist livre mais próximo.
 * Instancia o artefato e testa em-lugar (visibilidade afrouxada).
 */
class SquadCoordinatorTest {

    private SquadCoordinator coordinator() {
        SquadCoordinator sc = new SquadCoordinator();
        sc.init();
        return sc;
    }

    @Test
    void composicaoDeSquadsPadrao() {
        SquadCoordinator sc = coordinator();
        assertEquals("squad1", sc.agentSquad.get("connectionA4"));
        assertEquals("leader", sc.squadRole.get("connectionA1"));
        assertEquals("collector", sc.squadRole.get("connectionA4"));
        assertEquals("assembler", sc.squadRole.get("connectionA10"));
        assertEquals("sentinel", sc.squadRole.get("connectionA13"));
        assertEquals(3, sc.squadMembers.size());
        assertTrue(sc.squadMembers.get("squad1").contains("connectionA1"));
    }

    @Test
    void wrapDistToroidal() {
        SquadCoordinator sc = coordinator();
        assertEquals(1, sc.wrapDist(0, 39, 40)); // dá a volta
        assertEquals(5, sc.wrapDist(0, 5, 40));
        assertEquals(1, sc.wrapDist(0, 69, 70));
        assertEquals(35, sc.wrapDist(0, 35, 70));
    }

    @Test
    void soloistLivreMaisProximo() {
        SquadCoordinator sc = coordinator();
        sc.agentPositions.put("connectionA4", new int[]{10, 10});
        sc.agentPositions.put("connectionA5", new int[]{2, 2});  // livre, perto de (0,0)
        sc.agentPositions.put("connectionA6", new int[]{1, 1});  // mais perto, mas ocupado
        sc.soloistBusy.put("connectionA6", true);

        OpFeedbackParam<String> winner = new OpFeedbackParam<>();
        sc.find_free_soloist(0, 0, winner);

        assertEquals("connectionA5", winner.get()); // A6 mais perto, mas ocupado
    }
}
