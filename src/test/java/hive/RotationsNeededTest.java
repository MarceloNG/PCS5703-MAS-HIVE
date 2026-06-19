package hive;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Testa RotationsNeeded.needed() e rotateCW() sem dependência de Jason.
 * Rotação CW: (dx, dy) → (−dy, dx)  [coordenadas MASSim: X=leste, Y=sul]
 */
class RotationsNeededTest {

    private static List<int[]> pos(int[]... pairs) { return Arrays.asList(pairs); }
    private static int[] p(int x, int y) { return new int[]{x, y}; }

    // --- Happy paths: retorna número de rotações correto ---

    @Test
    void cadeiaNoRte_vsTaskLeste_retorna1() {
        // (0,-1),(0,-2) após 1 CW → (1,0),(2,0)
        assertEquals(1, RotationsNeeded.needed(
            pos(p(1, 0), p(2, 0)),
            pos(p(0, -1), p(0, -2))
        ));
    }

    @Test
    void cadeiaOeste_vsTaskLeste_retorna2() {
        // (-1,0),(-2,0) após 2 CW → (1,0),(2,0)
        assertEquals(2, RotationsNeeded.needed(
            pos(p(1, 0), p(2, 0)),
            pos(p(-1, 0), p(-2, 0))
        ));
    }

    @Test
    void cadeiaSul_vsTaskLeste_retorna3() {
        // (0,1),(0,2) após 3 CW → (1,0),(2,0)
        assertEquals(3, RotationsNeeded.needed(
            pos(p(1, 0), p(2, 0)),
            pos(p(0, 1), p(0, 2))
        ));
    }

    @Test
    void umBloco_norte_vsTaskLeste_retorna1() {
        assertEquals(1, RotationsNeeded.needed(
            pos(p(1, 0)),
            pos(p(0, -1))
        ));
    }

    // --- Falha: retorna -1 ---

    @Test
    void cadeiaLeste_jaAlinhada_retornaMenosUm() {
        // Blocos já na posição correta → R=0 → fail
        assertEquals(-1, RotationsNeeded.needed(
            pos(p(1, 0), p(2, 0)),
            pos(p(1, 0), p(2, 0))
        ));
    }

    @Test
    void lShape_incompativel_retornaMenosUm() {
        // (1,0)+(0,1) — nenhuma das 4 rotações produz (1,0)+(2,0)
        assertEquals(-1, RotationsNeeded.needed(
            pos(p(1, 0), p(2, 0)),
            pos(p(1, 0), p(0, 1))
        ));
    }

    @Test
    void taskVazia_retornaMenosUm() {
        // Sem requisitos: AllReqsSatisfied retorna true → R=0 → fail
        assertEquals(-1, RotationsNeeded.needed(
            Collections.emptyList(),
            pos(p(0, -1), p(0, -2))
        ));
    }

    @Test
    void semBlocos_semReqs_retornaMenosUm() {
        // Nenhum bloco nem requisito: trivialmente alinhado → fail
        assertEquals(-1, RotationsNeeded.needed(
            Collections.emptyList(),
            Collections.emptyList()
        ));
    }

    @Test
    void semBlocos_comReqs_retornaMenosUm() {
        // Sem blocos attached: impossível satisfazer
        assertEquals(-1, RotationsNeeded.needed(
            pos(p(1, 0), p(2, 0)),
            Collections.emptyList()
        ));
    }

    // --- rotateCW ---

    @Test
    void rotateCW_norte_para_leste() {
        List<int[]> r = RotationsNeeded.rotateCW(pos(p(0, -1)));
        assertEquals(1,  r.get(0)[0]);
        assertEquals(0,  r.get(0)[1]);
    }

    @Test
    void rotateCW_leste_para_sul() {
        List<int[]> r = RotationsNeeded.rotateCW(pos(p(1, 0)));
        assertEquals(0,  r.get(0)[0]);
        assertEquals(1,  r.get(0)[1]);
    }

    @Test
    void rotateCW_sul_para_oeste() {
        List<int[]> r = RotationsNeeded.rotateCW(pos(p(0, 1)));
        assertEquals(-1, r.get(0)[0]);
        assertEquals(0,  r.get(0)[1]);
    }

    @Test
    void rotateCW_oeste_para_norte() {
        List<int[]> r = RotationsNeeded.rotateCW(pos(p(-1, 0)));
        assertEquals(0,  r.get(0)[0]);
        assertEquals(-1, r.get(0)[1]);
    }

    @Test
    void rotateCW_4_vezes_e_identidade() {
        List<int[]> original = pos(p(1, 0), p(2, 0), p(0, -1), p(-1, 1));
        List<int[]> rotated = original;
        for (int i = 0; i < 4; i++) rotated = RotationsNeeded.rotateCW(rotated);
        for (int i = 0; i < original.size(); i++) {
            assertArrayEquals(original.get(i), rotated.get(i),
                "Posição " + i + " deve ser identidade após 4 rotações");
        }
    }
}
