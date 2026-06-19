package hive;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Testa a lógica pura de AllReqsSatisfied.check() sem dependência de Jason.
 * Verifica que TODOS os task_req posicionais estão em attached para tasks multi-bloco.
 */
class AllReqsSatisfiedTest {

    private static List<int[]> reqs(int[]... pairs) { return Arrays.asList(pairs); }
    private static List<int[]> att(int[]... pairs) { return Arrays.asList(pairs); }
    private static int[] pos(int x, int y) { return new int[]{x, y}; }

    // --- Happy paths ---

    @Test
    void doisBlocosAlinhados_ambosAttached_retornaTrue() {
        assertTrue(AllReqsSatisfied.check(
            reqs(pos(1, 0), pos(2, 0)),
            att(pos(1, 0), pos(2, 0))
        ));
    }

    @Test
    void umBlocoSatisfeito_retornaTrue() {
        assertTrue(AllReqsSatisfied.check(
            reqs(pos(1, 0)),
            att(pos(1, 0))
        ));
    }

    @Test
    void ordemDiferente_retornaTrue() {
        assertTrue(AllReqsSatisfied.check(
            reqs(pos(2, 0), pos(1, 0)),
            att(pos(1, 0), pos(2, 0))
        ));
    }

    @Test
    void extraAttachedNaoImporta_retornaTrue() {
        assertTrue(AllReqsSatisfied.check(
            reqs(pos(1, 0)),
            att(pos(1, 0), pos(0, 1), pos(-1, 0))
        ));
    }

    // --- Falha parcial ---

    @Test
    void faltaUmDeDois_retornaFalse() {
        assertFalse(AllReqsSatisfied.check(
            reqs(pos(1, 0), pos(2, 0)),
            att(pos(1, 0))
        ));
    }

    @Test
    void posicaoErrada_retornaFalse() {
        assertFalse(AllReqsSatisfied.check(
            reqs(pos(1, 0)),
            att(pos(0, 1))
        ));
    }

    @Test
    void nenhumAttached_retornaFalse() {
        assertFalse(AllReqsSatisfied.check(
            reqs(pos(1, 0), pos(2, 0)),
            Collections.emptyList()
        ));
    }

    // --- Edge cases ---

    @Test
    void reqsVazio_semRequisitos_retornaFalse() {
        // reqs vazia indica corrida perceptual (task_req ainda não chegou) — false defensivo
        assertFalse(AllReqsSatisfied.check(
            Collections.emptyList(),
            att(pos(1, 0))
        ));
    }

    @Test
    void reqsVazioAttachedVazio_retornaFalse() {
        assertFalse(AllReqsSatisfied.check(
            Collections.emptyList(),
            Collections.emptyList()
        ));
    }

    @Test
    void tresBlocos_todosPresentes_retornaTrue() {
        assertTrue(AllReqsSatisfied.check(
            reqs(pos(1, 0), pos(2, 0), pos(0, 1)),
            att(pos(0, 1), pos(1, 0), pos(2, 0))
        ));
    }

    @Test
    void tresBlocos_faltaUm_retornaFalse() {
        assertFalse(AllReqsSatisfied.check(
            reqs(pos(1, 0), pos(2, 0), pos(0, 1)),
            att(pos(1, 0), pos(2, 0))
        ));
    }
}
