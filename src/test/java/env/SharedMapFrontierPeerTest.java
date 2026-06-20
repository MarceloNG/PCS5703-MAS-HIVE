package env;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/**
 * Cobre a peer-awareness na seleção de fronteira (#28):
 *  - peerPositions: filtragem de occupancy (fresco, não-self)
 *  - peerAwareScore: penalidade quando colega está mais perto da fronteira
 *  - nearestFrontierBiased: divergência de agentes com mesmo heading
 */
class SharedMapFrontierPeerTest {

    private SharedMap mapBase() {
        SharedMap sm = new SharedMap();
        sm.obstacles = new ConcurrentHashMap<>();
        sm.occupancy = new ConcurrentHashMap<>();
        sm.gridWidth = 0;
        sm.gridHeight = 0;
        sm.occupancyStep = 0;
        sm.cachedFrontiers = new java.util.ArrayList<>();
        return sm;
    }

    // ===== U1: peerPositions =====

    @Test
    void peerPositions_occupancyVazio_retornaListaVazia() {
        SharedMap sm = mapBase();
        List<int[]> peers = sm.peerPositions("agentA1");
        assertTrue(peers.isEmpty());
    }

    @Test
    void peerPositions_peerFresco_retornaUmaEntrada() {
        SharedMap sm = mapBase();
        sm.occupancyStep = 5;
        sm.occupancy.put("agentA2", new int[]{3, 4, 5});
        List<int[]> peers = sm.peerPositions("agentA1");
        assertEquals(1, peers.size());
        assertArrayEquals(new int[]{3, 4}, peers.get(0));
    }

    @Test
    void peerPositions_selfExcluido() {
        SharedMap sm = mapBase();
        sm.occupancyStep = 5;
        sm.occupancy.put("agentA1", new int[]{5, 5, 5});
        List<int[]> peers = sm.peerPositions("agentA1");
        assertTrue(peers.isEmpty());
    }

    @Test
    void peerPositions_peerStale_excluido() {
        // step=3, occupancyStep=5 → 3 < 5-1=4 → stale
        SharedMap sm = mapBase();
        sm.occupancyStep = 5;
        sm.occupancy.put("agentA2", new int[]{3, 4, 3});
        List<int[]> peers = sm.peerPositions("agentA1");
        assertTrue(peers.isEmpty());
    }

    @Test
    void peerPositions_misturaEntradas_soFrescaNaoSelf() {
        // self + 1 fresca + 1 stale → deve retornar só a fresca
        SharedMap sm = mapBase();
        sm.occupancyStep = 5;
        sm.occupancy.put("agentA1", new int[]{1, 1, 5});   // self
        sm.occupancy.put("agentA2", new int[]{3, 4, 5});   // fresca
        sm.occupancy.put("agentA3", new int[]{7, 7, 2});   // stale (step=2 < 4)
        List<int[]> peers = sm.peerPositions("agentA1");
        assertEquals(1, peers.size());
        assertArrayEquals(new int[]{3, 4}, peers.get(0));
    }

    // ===== U2: nearestFrontierBiased peer-aware (cenário núcleo) =====

    @Test
    void cenarioNucleo_agentA0_escolheFronteiraY() {
        // agentA0 (idx=0, heading N) em (5,10) e agentA4 (idx=4, heading N) em (6,10)
        // Fronteiras: X=(6,5) e Y=(4,5)
        // dist(A0→X)=|5-6|+|10-5|=6, dist(A4→X)=|6-6|+|10-5|=5 → A4 mais perto de X
        // dist(A0→Y)=|5-4|+|10-5|=6, dist(A4→Y)=|6-4|+|10-5|=7 → A0 mais perto de Y
        // A0 deve escolher Y (X penalizado pois A4 mais perto)
        SharedMap sm = mapBase();
        sm.occupancyStep = 0;
        sm.occupancy.put("agentA0", new int[]{5, 10, 0});
        sm.occupancy.put("agentA4", new int[]{6, 10, 0});
        sm.cachedFrontiers = java.util.Arrays.asList(new int[]{6, 5}, new int[]{4, 5});
        int[] r = sm.nearestFrontierBiased(5, 10, "agentA0");
        assertArrayEquals(new int[]{4, 5}, r, "A0 deve escolher Y=(4,5), não X=(6,5)");
    }

    @Test
    void cenarioNucleo_agentA4_escolheFronteiraX() {
        // agentA4 (heading N) em (6,10) — A0 está mais longe de X → A4 escolhe X sem penalidade
        SharedMap sm = mapBase();
        sm.occupancyStep = 0;
        sm.occupancy.put("agentA0", new int[]{5, 10, 0});
        sm.occupancy.put("agentA4", new int[]{6, 10, 0});
        sm.cachedFrontiers = java.util.Arrays.asList(new int[]{6, 5}, new int[]{4, 5});
        int[] r = sm.nearestFrontierBiased(6, 10, "agentA4");
        assertArrayEquals(new int[]{6, 5}, r, "A4 deve escolher X=(6,5)");
    }

    @Test
    void peerEquidistante_semPenalidade() {
        // peer exatamente equidistante de F que agent → sem penalidade (critério < estrito)
        SharedMap sm = mapBase();
        sm.occupancyStep = 0;
        sm.occupancy.put("agentA0", new int[]{5, 10, 0});
        sm.occupancy.put("agentA4", new int[]{5, 10, 0}); // mesmo local → mesma dist
        sm.cachedFrontiers = java.util.Arrays.asList(new int[]{5, 5}); // só uma fronteira, heading N
        // dist(A0→F)=5, dist(A4→F)=5 → empate → não penaliza → A0 escolhe F normalmente
        int[] r = sm.nearestFrontierBiased(5, 10, "agentA0");
        assertArrayEquals(new int[]{5, 5}, r, "empate não penaliza — deve escolher F");
    }

    @Test
    void semPeers_comportamentoOriginalPreservado() {
        // sem peers em occupancy → heading-bias preservado (R2)
        SharedMap sm = mapBase();
        // agentA4 → idx=4 → heading N (4%4=0)
        sm.cachedFrontiers = java.util.Arrays.asList(new int[]{5, 3}, new int[]{5, 7});
        // (5,3) está ao norte de (5,5), (5,7) ao sul
        int[] r = sm.nearestFrontierBiased(5, 5, "agentA4");
        assertArrayEquals(new int[]{5, 3}, r, "sem peers, heading N deve preferir fronteira ao norte");
    }

    @Test
    void headingBiasPreservadoComPeer_escolheFronteiraNoSetorSemPeer() {
        // agent heading N, 2 fronteiras ao norte: uma com peer mais perto, outra sem
        // Deve preferir a sem peer mesmo sendo mesma distância base
        SharedMap sm = mapBase();
        sm.occupancyStep = 0;
        // peer em (6,5) — mais perto de X=(6,3) que agent em (5,10)
        sm.occupancy.put("agentA4", new int[]{6, 5, 0}); // peer
        sm.occupancy.put("agentA0", new int[]{5, 10, 0}); // self
        // agentA0 → heading N; fronteiras X=(6,3) e Y=(4,3), ambas ao norte de (5,10)
        // dist(A0→X)=|5-6|+|10-3|=8, dist(peer→X)=|6-6|+|5-3|=2 < 8 → X penalizado
        // dist(A0→Y)=|5-4|+|10-3|=8, dist(peer→Y)=|6-4|+|5-3|=4 < 8 → Y também penalizado
        // ambas penalizadas → ainda deve escolher uma (não starvar) — a mais próxima
        sm.cachedFrontiers = java.util.Arrays.asList(new int[]{6, 3}, new int[]{4, 7});
        // (4,7) está ao sul de (5,10) — não está no setor N → fallback global
        // A0 heading N: (6,3) está ao norte (3<10) → candidata → penalizada → score=8+3=11
        // (4,7) ao sul (7>10 falso, 7<10 sim) → ao norte também — fy=7 < agY=10 → candidata
        // dist(A0→(4,7)) = |5-4|+|10-7|=4, dist(peer→(4,7)) = |6-4|+|5-7|=4 → empate → sem penalidade → score=4
        // Deve escolher (4,7) com score=4 < (6,3) com score=11
        int[] r = sm.nearestFrontierBiased(5, 10, "agentA0");
        assertArrayEquals(new int[]{4, 7}, r, "deve preferir fronteira sem peer mais perto");
    }
}
