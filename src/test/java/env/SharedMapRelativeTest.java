package env;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Fase D / R7: prova que o mapa por-agente e parametrizado por frame —
 * translateCells re-keia todo o estado por-celula por um offset (a algebra que
 * a fusao cross-agente (U9) usara). Sem rodar a simulacao.
 */
class SharedMapRelativeTest {

    private SharedMap freshMap(int w, int h) {
        SharedMap sm = new SharedMap();
        sm.init();
        sm.gridWidth = w;
        sm.gridHeight = h;
        return sm;
    }

    @Test
    void translacaoBasicaSemWrap() {
        SharedMap sm = freshMap(0, 0); // sem wrap (dims desconhecidas)
        sm.cells.put("5,3", "obstacle:");
        sm.obstacles.put("5,3", 10);
        sm.visitedCells.add("5,3");
        sm.knownGoalZones.add("5,3");
        sm.translateCells(2, 1);
        assertTrue(sm.cells.containsKey("7,4"));
        assertFalse(sm.cells.containsKey("5,3"));
        assertTrue(sm.obstacles.containsKey("7,4"));
        assertTrue(sm.visitedCells.contains("7,4"));
        assertTrue(sm.knownGoalZones.contains("7,4"));
    }

    @Test
    void offsetZeroNaoMuda() {
        SharedMap sm = freshMap(0, 0);
        sm.visitedCells.add("4,9");
        sm.translateCells(0, 0);
        assertTrue(sm.visitedCells.contains("4,9"));
    }

    @Test
    void translacaoInversaVolta() {
        SharedMap sm = freshMap(0, 0);
        sm.knownGoalZones.add("3,7");
        sm.translateCells(5, -2);
        sm.translateCells(-5, 2);
        assertTrue(sm.knownGoalZones.contains("3,7"));
    }

    @Test
    void translacaoToroidalAplicaWrap() {
        SharedMap sm = freshMap(70, 70);
        sm.visitedCells.add("68,0");
        sm.translateCells(5, 0); // 68+5=73 -> 3 (mod 70)
        assertTrue(sm.visitedCells.contains("3,0"));
        assertFalse(sm.visitedCells.contains("73,0"));
    }

    @Test
    void dispenserPreservaDetalhe() {
        SharedMap sm = freshMap(0, 0);
        sm.knownDispensers.add("5,3:b1");
        sm.translateCells(2, 1);
        assertTrue(sm.knownDispensers.contains("7,4:b1"));
    }

    @Test
    void ocupacaoTraduzida() {
        SharedMap sm = freshMap(0, 0);
        sm.occupancy.put("colega", new int[]{5, 3, 9});
        sm.translateCells(2, 1);
        int[] p = sm.occupancy.get("colega");
        assertEquals(7, p[0]);
        assertEquals(4, p[1]);
        assertEquals(9, p[2]); // step preservado
    }

    @Test
    void roleZoneTraduzida() {
        SharedMap sm = freshMap(0, 0);
        sm.knownRoleZones.add("2,8");
        sm.translateCells(1, 1);
        assertTrue(sm.knownRoleZones.contains("3,9"));
        assertFalse(sm.knownRoleZones.contains("2,8"));
    }

    @Test
    void valorDaCelulaPreservado() {
        SharedMap sm = freshMap(0, 0);
        sm.cells.put("5,3", "obstacle:");
        sm.obstacles.put("5,3", 10);
        sm.translateCells(2, 1);
        assertEquals("obstacle:", sm.cells.get("7,4"));   // tipo/detalhe intacto
        assertEquals(10, sm.obstacles.get("7,4").intValue());
    }

    // Fase D (#7): decay poda 'seen_' obsoletas sem tocar chaves de posicao de agente.
    @Test
    void decayPodaOccupancySeenObsoleta() {
        SharedMap sm = freshMap(0, 0);
        sm.occupancyStep = 20;
        sm.occupancy.put("seen_5_3", new int[]{5, 3, 20}); // fresca (== watermark)
        sm.occupancy.put("seen_9_9", new int[]{9, 9, 10}); // obsoleta (< 19)
        sm.occupancy.put("agentA1",  new int[]{1, 1, 10}); // chave de agente: nunca poda aqui
        sm.decay_obstacles(20);                            // step % 5 == 0
        assertTrue(sm.occupancy.containsKey("seen_5_3"));
        assertFalse(sm.occupancy.containsKey("seen_9_9"));
        assertTrue(sm.occupancy.containsKey("agentA1"));
    }

    // Fase D (#7): em step nao-multiplo-de-5 o decay e no-op — nao poda nada (review Fase D).
    @Test
    void decayNaoPodaForaDoMultiploDe5() {
        SharedMap sm = freshMap(0, 0);
        sm.occupancyStep = 20;
        sm.occupancy.put("seen_9_9", new int[]{9, 9, 10}); // seria obsoleta se o decay rodasse
        sm.decay_obstacles(21);                            // step % 5 != 0 -> early return
        assertTrue(sm.occupancy.containsKey("seen_9_9"));
    }

    // R3 / U3: instancias por-agente sao frames PRIVADOS — escrever numa nao vaza p/ outra.
    // (Invariante central do split instancia-por-agente; prometida no plano, antes sem teste.)
    @Test
    void instanciasPorAgenteNaoCompartilhamCelulas() {
        SharedMap a = freshMap(0, 0);
        SharedMap b = freshMap(0, 0);
        a.cells.put("5,3", "obstacle:");
        a.obstacles.put("5,3", 10);
        a.visitedCells.add("5,3");
        a.knownGoalZones.add("5,3");
        assertFalse(b.cells.containsKey("5,3"));
        assertFalse(b.obstacles.containsKey("5,3"));
        assertFalse(b.visitedCells.contains("5,3"));
        assertFalse(b.knownGoalZones.contains("5,3"));
    }

    // translateCells com offset NEGATIVO sob wrap toroidal exercita o ramo modular de norm()
    // (a < 0): 2 + (-5) = -3 -> 67 (mod 70). O round-trip existente usa freshMap(0,0), sem wrap.
    @Test
    void translacaoToroidalOffsetNegativo() {
        SharedMap sm = freshMap(70, 70);
        sm.visitedCells.add("2,0");
        sm.translateCells(-5, 0); // 2-5 = -3 -> 67 (mod 70)
        assertTrue(sm.visitedCells.contains("67,0"));
        assertFalse(sm.visitedCells.contains("-3,0"));
    }
}
