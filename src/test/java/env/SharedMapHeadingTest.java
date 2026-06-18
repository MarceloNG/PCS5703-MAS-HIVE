package env;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/**
 * Cobre heading-balanceado: extractAgentIndex, inPreferredDirection e
 * nearestFrontierBiased. Não requer infra CArtAgO — testa os métodos
 * package-private com cachedFrontiers pré-populado diretamente.
 */
class SharedMapHeadingTest {

    private SharedMap mapBase() {
        SharedMap sm = new SharedMap();
        sm.obstacles = new ConcurrentHashMap<>();
        sm.occupancy = new ConcurrentHashMap<>();
        sm.gridWidth = 0;   // frame dead-reckoned: sem wrap
        sm.gridHeight = 0;
        sm.occupancyStep = 0;
        sm.cachedFrontiers = new java.util.ArrayList<>();
        return sm;
    }

    // --- extractAgentIndex ---

    @Test
    void extractIndex_connectionA1() {
        assertEquals(1, mapBase().extractAgentIndex("connectionA1"));
    }

    @Test
    void extractIndex_connectionA15() {
        assertEquals(15, mapBase().extractAgentIndex("connectionA15"));
    }

    @Test
    void extractIndex_semNumero_retornaMenosUm() {
        assertEquals(-1, mapBase().extractAgentIndex("agentSemNumero"));
    }

    // --- nearestFrontierBiased: seleção direcional ---

    @Test
    void headingLeste_prefereEste() {
        // connectionA1 → idx=1 → E; frontier (7,5) está a leste, (5,3) ao norte
        SharedMap sm = mapBase();
        sm.cachedFrontiers = Arrays.asList(new int[]{7, 5}, new int[]{5, 3});
        int[] r = sm.nearestFrontierBiased(5, 5, "connectionA1");
        assertArrayEquals(new int[]{7, 5}, r, "deve retornar frontier a leste");
    }

    @Test
    void headingNorte_fallback_semFrontierAoNorte() {
        // connectionA4 → idx=4 → N (mod 4 = 0); única frontier (7,5) está a leste
        SharedMap sm = mapBase();
        sm.cachedFrontiers = Arrays.asList(new int[]{7, 5});
        int[] r = sm.nearestFrontierBiased(5, 5, "connectionA4");
        assertArrayEquals(new int[]{7, 5}, r, "fallback deve retornar frontier global unica");
    }

    @Test
    void headingSul_maisperto_entredoisCandidatos() {
        // connectionA2 → idx=2 → S; frontiers (5,7) a sul e (8,2) ao norte
        SharedMap sm = mapBase();
        sm.cachedFrontiers = Arrays.asList(new int[]{5, 7}, new int[]{8, 2});
        int[] r = sm.nearestFrontierBiased(5, 5, "connectionA2");
        assertArrayEquals(new int[]{5, 7}, r, "deve retornar frontier ao sul");
    }

    @Test
    void headingOeste_maisProxima() {
        // connectionA3 → idx=3 → W; (2,5) e (3,5) ambas a oeste, (3,5) mais próxima
        SharedMap sm = mapBase();
        sm.cachedFrontiers = Arrays.asList(new int[]{2, 5}, new int[]{3, 5});
        int[] r = sm.nearestFrontierBiased(5, 5, "connectionA3");
        assertArrayEquals(new int[]{3, 5}, r, "deve retornar frontier mais proxima a oeste");
    }

    @Test
    void semFrontiers_retornaPosicaoAgente() {
        SharedMap sm = mapBase();
        // cachedFrontiers vazio — comportamento idêntico ao get_nearest_frontier
        int[] r = sm.nearestFrontierBiased(3, 3, "connectionA5");
        assertArrayEquals(new int[]{3, 3}, r, "sem frontiers, retorna posicao do agente");
    }

    @Test
    void nomeInvalido_fallbackGlobal() {
        // índice -1 → sem heading → global nearest
        SharedMap sm = mapBase();
        sm.cachedFrontiers = Arrays.asList(new int[]{7, 5}, new int[]{5, 3});
        // ambas igualmente distantes de (5,5); global nearest deve retornar uma delas
        int[] r = sm.nearestFrontierBiased(5, 5, "agenteSemNumero");
        assertTrue(r[0] == 7 || r[0] == 5, "fallback global com nome invalido");
    }
}
