package env;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class SharedMapTangentTest {

    private SharedMap mapWith(int w, int h) {
        SharedMap sm = new SharedMap();
        sm.obstacles = new ConcurrentHashMap<>();
        sm.occupancy = new ConcurrentHashMap<>();
        sm.gridWidth = w;
        sm.gridHeight = h;
        sm.occupancyStep = 0;
        return sm;
    }

    // ===== scanForward =====

    @Test
    void scanForward_caminhoLivre_retornaRange() {
        assertEquals(SharedMap.SCAN_RANGE, mapWith(70, 70).scanForward(5, 5, "n"));
    }

    @Test
    void scanForward_obstaculoA1_retorna0() {
        SharedMap sm = mapWith(70, 70);
        sm.obstacles.put("5,4", 1);   // 1 célula ao norte de (5,5)
        assertEquals(0, sm.scanForward(5, 5, "n"));
    }

    @Test
    void scanForward_obstaculoA3_retorna2() {
        SharedMap sm = mapWith(70, 70);
        sm.obstacles.put("5,2", 1);   // 3 células ao norte (1 e 2 livres, 3 bloqueada)
        assertEquals(2, sm.scanForward(5, 5, "n"));
    }

    @Test
    void scanForward_obstaculoA5_retorna4() {
        SharedMap sm = mapWith(70, 70);
        sm.obstacles.put("5,0", 1);   // 5 células ao norte (1..4 livres, 5 bloqueada)
        assertEquals(4, sm.scanForward(5, 5, "n"));
    }

    @Test
    void scanForward_dirLeste_detectaObstaculo() {
        SharedMap sm = mapWith(70, 70);
        sm.obstacles.put("8,5", 1);   // 3 células a leste
        assertEquals(2, sm.scanForward(5, 5, "e"));
    }

    @Test
    void scanForward_dirSul_detectaObstaculo() {
        SharedMap sm = mapWith(70, 70);
        sm.obstacles.put("5,7", 1);   // 2 células ao sul
        assertEquals(1, sm.scanForward(5, 5, "s"));
    }

    @Test
    void scanForward_dirOeste_detectaObstaculo() {
        SharedMap sm = mapWith(70, 70);
        sm.obstacles.put("3,5", 1);   // 2 células a oeste
        assertEquals(1, sm.scanForward(5, 5, "w"));
    }

    @Test
    void scanForward_wrapToroidal_detecta() {
        SharedMap sm = mapWith(10, 10);
        sm.obstacles.put("5,8", 1);   // 2 células ao sul de (5,6) com wrap
        assertEquals(1, sm.scanForward(5, 6, "s"));
    }

    // ===== nearestTangent =====

    @Test
    void nearestTangent_paredeNorteUmaColuna_retornaCWAdjacente() {
        SharedMap sm = mapWith(70, 70);
        sm.obstacles.put("5,4", 1);   // parede de 1 célula ao norte
        // CW de norte = leste; a partir de (6,5) a célula norte (6,4) é livre
        int[] t = sm.nearestTangent(5, 5, "n");
        assertEquals(6, t[0]);        // deve mover para leste
        assertEquals(5, t[1]);
    }

    @Test
    void nearestTangent_paredeNorteLarga_retornaFimParede() {
        SharedMap sm = mapWith(70, 70);
        // parede de 3 células: (5,4), (6,4), (7,4) — bloqueiam norte de x=5,6,7
        sm.obstacles.put("5,4", 1);
        sm.obstacles.put("6,4", 1);
        sm.obstacles.put("7,4", 1);
        // CW=leste; de (8,5) o norte (8,4) está livre
        int[] t = sm.nearestTangent(5, 5, "n");
        assertEquals(8, t[0]);
        assertEquals(5, t[1]);
    }

    @Test
    void nearestTangent_paredeLesteUmaColuna_retornaCW() {
        SharedMap sm = mapWith(70, 70);
        sm.obstacles.put("6,5", 1);   // 1 célula a leste
        // CW de leste = sul; de (5,6) a célula leste (6,6) é livre
        int[] t = sm.nearestTangent(5, 5, "e");
        assertEquals(5, t[0]);
        assertEquals(6, t[1]);
    }

    @Test
    void nearestTangent_paredeSulLarga_retornaFimParede() {
        SharedMap sm = mapWith(70, 70);
        sm.obstacles.put("5,6", 1);
        sm.obstacles.put("4,6", 1);
        sm.obstacles.put("3,6", 1);
        // CW de sul = oeste; de (2,5) o sul (2,6) é livre
        int[] t = sm.nearestTangent(5, 5, "s");
        assertEquals(2, t[0]);
        assertEquals(5, t[1]);
    }

    @Test
    void nearestTangent_agenteSobreAlvo_retornaPropriaPos() {
        // Se a célula primária já está livre imediatamente, o tangente é a própria posição
        SharedMap sm = mapWith(70, 70);
        int[] t = sm.nearestTangent(5, 5, "n");  // sem obstáculos
        assertEquals(5, t[0]);
        assertEquals(5, t[1]);
    }

    @Test
    void nearestTangent_obstaculoNaoAdjacente_naoRetornaPropriaPos() {
        // Regressão: obstáculo 3 células à frente (FR=2, não adjacente).
        // O bug antigo retornava a própria posição do agente porque i=0 satisfazia
        // a condição errada. A correção deve retornar o primeiro ponto CW livre.
        SharedMap sm = mapWith(70, 70);
        sm.obstacles.put("5,2", 1);   // 3 células ao norte; (5,4) livre
        // CW de norte = leste; de (6,5) o norte está totalmente livre → retorna {6,5}
        int[] t = sm.nearestTangent(5, 5, "n");
        assertNotEquals(5, t[0], "não deve retornar a própria posição (bug antigo)");
        assertEquals(6, t[0]);
        assertEquals(5, t[1]);
    }
}
