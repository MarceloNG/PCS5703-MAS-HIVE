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
}
