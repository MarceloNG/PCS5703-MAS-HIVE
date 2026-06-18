package env;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/**
 * Caracteriza o A* toroidal do SharedMap — núcleo de navegação (fix #2 do
 * livelock): caminho, contorno de obstáculo, wrap toroidal e overlay de
 * ocupação. Regressão sem rodar a simulação. NÃO altera a lógica do A*;
 * testa em-lugar (visibilidade afrouxada para package-private).
 */
class SharedMapAStarTest {

    private SharedMap mapWith(int w, int h) {
        SharedMap sm = new SharedMap();
        sm.obstacles = new ConcurrentHashMap<>();
        sm.occupancy = new ConcurrentHashMap<>();
        sm.gridWidth = w;
        sm.gridHeight = h;
        sm.occupancyStep = 0;
        return sm;
    }

    @Test
    void caminhoRetoLeste() {
        assertEquals("e", mapWith(40, 40).astar(0, 0, 5, 0));
    }

    @Test
    void caminhoRetoSul() {
        assertEquals("s", mapWith(40, 40).astar(0, 0, 0, 5));
    }

    @Test
    void contornaObstaculoAdjacente() {
        SharedMap sm = mapWith(40, 40);
        sm.obstacles.put("1,0", 1); // bloqueia o passo direto a leste
        String dir = sm.astar(0, 0, 5, 0);
        assertTrue(dir.equals("n") || dir.equals("s"),
                   "deveria desviar (n/s), veio: " + dir);
    }

    @Test
    void wrapToroidalLeste() {
        // 38 -> 1 num grid 40: mais curto dando a volta pelo leste (38->39->0->1)
        assertEquals("e", mapWith(40, 40).astar(38, 0, 1, 0));
    }

    @Test
    void wrapToroidal70() {
        // 68 -> 1 num grid 70: leste pela borda (68->69->0->1)
        assertEquals("e", mapWith(70, 70).astar(68, 0, 1, 0));
    }

    @Test
    void overlayOcupacao_contornaColega() {
        SharedMap sm = mapWith(40, 40);
        sm.occupancyStep = 5;
        sm.occupancy.put("colega", new int[]{1, 0, 5}); // colega na célula a leste
        String dir = sm.astar(0, 0, 6, 0);
        assertNotEquals("e", dir); // penalidade alta -> contorna, não pisa em (1,0)
    }

    @Test
    void overlayOcupacao_origemEAlvoNaoPenalizados() {
        SharedMap sm = mapWith(40, 40);
        sm.occupancyStep = 5;
        // colega exatamente na célula-alvo não impede chegar (alvo é removido do overlay)
        sm.occupancy.put("colega", new int[]{1, 0, 5});
        assertEquals("e", sm.astar(0, 0, 1, 0)); // alvo adjacente a leste, sem desvio
    }

    // ===== Fase C / U1: get_nearest_role_zone (via nearestRoleZone) =====

    private SharedMap mapWithRoleZones(int w, int h) {
        SharedMap sm = new SharedMap();
        sm.init();              // popula knownRoleZones + demais sets
        sm.gridWidth = w;
        sm.gridHeight = h;
        return sm;
    }

    @Test
    void roleZoneMaisProximaPorCusto() {
        SharedMap sm = mapWithRoleZones(40, 40);
        sm.knownRoleZones.add("3,0");   // custo 3
        sm.knownRoleZones.add("10,0");  // custo 10
        int[] rz = sm.nearestRoleZone(0, 0);
        assertEquals(3, rz[0]);
        assertEquals(0, rz[1]);
    }

    @Test
    void nenhumaRoleZoneConhecida_retornaMenosUm() {
        SharedMap sm = mapWithRoleZones(40, 40);
        int[] rz = sm.nearestRoleZone(0, 0);
        assertEquals(-1, rz[0]);
        assertEquals(-1, rz[1]);
    }

    @Test
    void roleZoneWrapToroidalEhMaisProxima() {
        SharedMap sm = mapWithRoleZones(40, 40);
        sm.knownRoleZones.add("38,0"); // custo 2 dando a volta (0->39->38)
        sm.knownRoleZones.add("5,0");  // custo 5
        int[] rz = sm.nearestRoleZone(0, 0);
        assertEquals(38, rz[0]);
        assertEquals(0, rz[1]);
    }

    @Test
    void roleZoneAchadaApesarDeObstaculo() {
        SharedMap sm = mapWithRoleZones(40, 40);
        sm.obstacles.put("1,0", 1);    // bloqueia o passo direto
        sm.knownRoleZones.add("3,0");
        int[] rz = sm.nearestRoleZone(0, 0);
        assertEquals(3, rz[0]);        // ainda acha a (unica) role-zone, via desvio
        assertEquals(0, rz[1]);
    }
}
