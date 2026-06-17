package hive;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Regressão do wrap toroidal e da direção adjacente (U4 — Track 3, Fase B).
 * Lógica pura, validada em ms sem rodar a simulação — cobre o grid em 40 e 70.
 */
class AdjacentDirectionTest {

    // --- wrapDelta: menor deslocamento com sentido num eixo toroidal ---

    @Test
    void wrapDelta_semWrap() {
        assertEquals(1, AdjacentDirection.wrapDelta(1, 40));
        assertEquals(-1, AdjacentDirection.wrapDelta(-1, 40));
        assertEquals(0, AdjacentDirection.wrapDelta(0, 40));
        assertEquals(20, AdjacentDirection.wrapDelta(20, 40));
    }

    @Test
    void wrapDelta_comWrap40() {
        assertEquals(-1, AdjacentDirection.wrapDelta(39, 40)); // dá a volta
        assertEquals(1, AdjacentDirection.wrapDelta(-39, 40));
        assertEquals(-19, AdjacentDirection.wrapDelta(21, 40));
    }

    @Test
    void wrapDelta_comWrap70() {
        assertEquals(-1, AdjacentDirection.wrapDelta(69, 70));
        assertEquals(1, AdjacentDirection.wrapDelta(-69, 70));
        assertEquals(35, AdjacentDirection.wrapDelta(35, 70));
        assertEquals(-34, AdjacentDirection.wrapDelta(36, 70));
    }

    @Test
    void wrapDelta_sizeInvalido_semWrap() {
        assertEquals(5, AdjacentDirection.wrapDelta(5, 0));
    }

    // --- direction: n/s/e/w para alvo adjacente; none caso contrário ---

    @Test
    void direction_adjacenteSemWrap() {
        assertEquals("n", AdjacentDirection.direction(0, 0, 0, -1, 40, 40));
        assertEquals("s", AdjacentDirection.direction(0, 0, 0, 1, 40, 40));
        assertEquals("e", AdjacentDirection.direction(0, 0, 1, 0, 40, 40));
        assertEquals("w", AdjacentDirection.direction(0, 0, -1, 0, 40, 40));
    }

    @Test
    void direction_origemIgualAlvo_none() {
        assertEquals("none", AdjacentDirection.direction(5, 5, 5, 5, 40, 40));
    }

    @Test
    void direction_naoAdjacente_none() {
        assertEquals("none", AdjacentDirection.direction(0, 0, 2, 0, 40, 40)); // dx=2
        assertEquals("none", AdjacentDirection.direction(0, 0, 1, 1, 40, 40)); // diagonal
    }

    @Test
    void direction_wrapToroidal40() {
        // 39 -> 0 é adjacente "e" (dá a volta); 0 -> 39 é "w"
        assertEquals("e", AdjacentDirection.direction(39, 0, 0, 0, 40, 40));
        assertEquals("w", AdjacentDirection.direction(0, 0, 39, 0, 40, 40));
    }

    @Test
    void direction_wrapToroidal70() {
        assertEquals("e", AdjacentDirection.direction(69, 0, 0, 0, 70, 70));
        assertEquals("w", AdjacentDirection.direction(0, 0, 69, 0, 70, 70));
        assertEquals("s", AdjacentDirection.direction(0, 69, 0, 0, 70, 70));
        assertEquals("n", AdjacentDirection.direction(0, 0, 0, 69, 70, 70));
    }
}
