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

    // ===== Issue #15: A* ciente de obstáculos percebidos =====

    private SharedMap mapWithInit() {
        SharedMap sm = new SharedMap();
        sm.init();          // popula cells, obstacles, knownDispensers, etc.
        sm.gridWidth = 0;   // sem wrapping toroidal (replica absolutePosition:false)
        sm.gridHeight = 0;
        sm.occupancyStep = 0;
        return sm;
    }

    @Test
    void percebidoMarcaObstacle() {
        SharedMap sm = mapWithInit();
        sm.update_cell(1, 0, "obstacle", "");
        assertTrue(sm.obstacles.containsKey("1,0"), "obstáculo percebido deve entrar em obstacles");
        String dir = sm.astar(0, 0, 5, 0);
        assertTrue(dir.equals("n") || dir.equals("s"),
            "A* deve desviar de obstacle percebido em (1,0), veio: " + dir);
    }

    @Test
    void percebidoNaoDecai() {
        SharedMap sm = mapWithInit();
        sm.update_cell(1, 0, "obstacle", "");
        sm.decay_obstacles(1000);   // step=1000; MAX_VALUE não satisfaz < 970
        assertTrue(sm.obstacles.containsKey("1,0"), "sentinela MAX_VALUE não deve decair");
    }

    @Test
    void colisaoAposPercebidoIdempotente() {
        SharedMap sm = mapWithInit();
        sm.update_cell(1, 0, "obstacle", "");
        sm.mark_obstacle(1, 0, 42); // guard containsKey → não sobrescreve sentinela
        assertEquals(Integer.MAX_VALUE, sm.obstacles.get("1,0"),
            "mark_obstacle não deve sobrescrever sentinela MAX_VALUE");
    }

    @Test
    void tipoNaoObstacleNaoMarcaObstacles() {
        SharedMap sm = mapWithInit();
        // obstacles começa vazio — outros tipos não populam obstacles
        assertTrue(sm.obstacles.isEmpty(), "obstacles deve estar vazio em mapWithInit() sem update_cell");
    }

    // ===== Issue #27 (Cap A): detecção de cul-de-sac (beco de uma boca) =====
    // Geometria do 03c: U com boca a OESTE, fundo a LESTE, agente a oeste em (4,5).
    //   N: y=3, x6..10 | S: y=7, x6..10 | L (fundo): x=10, y4..6 | boca: x6 aberto.

    private void wallU(SharedMap sm) {
        for (int x = 6; x <= 10; x++) { sm.obstacles.put(x + ",3", Integer.MAX_VALUE);
                                        sm.obstacles.put(x + ",7", Integer.MAX_VALUE); }
        for (int y = 4; y <= 6; y++)  { sm.obstacles.put("10," + y, Integer.MAX_VALUE); }
    }

    @Test
    void culDeSac_uShapeEhBeco() {
        SharedMap sm = mapWithInit();
        wallU(sm);
        // agente a oeste (4,5); fronteira no interior do U → é beco (deve evitar)
        assertTrue(sm.isCulDeSacFrontier(4, 5, 8, 5), "interior do U visto de fora é cul-de-sac");
    }

    @Test
    void culDeSac_corredorNaoEhBeco() {
        SharedMap sm = mapWithInit();
        // barra dupla || : corredor horizontal (paredes em y4 e y6), AMBOS os extremos
        // abertos → passagem, não beco.
        for (int x = 6; x <= 12; x++) { sm.obstacles.put(x + ",4", Integer.MAX_VALUE);
                                        sm.obstacles.put(x + ",6", Integer.MAX_VALUE); }
        assertTrue(!sm.isCulDeSacFrontier(4, 5, 8, 5), "corredor de 2 saídas NÃO é cul-de-sac");
    }

    @Test
    void culDeSac_doisPontosNaoEhBeco() {
        SharedMap sm = mapWithInit();
        // trema ¨ : dois obstáculos isolados, sem fechar região
        sm.obstacles.put("8,4", Integer.MAX_VALUE);
        sm.obstacles.put("8,6", Integer.MAX_VALUE);
        assertTrue(!sm.isCulDeSacFrontier(4, 5, 8, 5), "dois pontos isolados NÃO são cul-de-sac");
    }

    @Test
    void culDeSac_campoAbertoNaoEhBeco() {
        SharedMap sm = mapWithInit();   // sem obstáculos
        assertTrue(!sm.isCulDeSacFrontier(4, 5, 8, 5), "campo aberto NÃO é cul-de-sac");
    }

    @Test
    void culDeSac_paredeNaoConta() {
        SharedMap sm = mapWithInit();
        wallU(sm);
        // F sobre uma parede não é fronteira válida → false
        assertTrue(!sm.isCulDeSacFrontier(4, 5, 10, 5), "célula de parede não é cul-de-sac");
    }

    @Test
    void culDeSac_filtroEscolheFronteiraAberta() {
        SharedMap sm = mapWithInit();
        wallU(sm);
        // duas fronteiras candidatas: uma no interior do beco, outra em campo aberto a oeste.
        sm.cachedFrontiers.add(new int[]{8, 5});   // interior do U (beco) — manhattan 4
        sm.cachedFrontiers.add(new int[]{1, 5});   // aberto a oeste — manhattan 3
        // agentA1 (idx 1 → heading E) NÃO tem a aberta a oeste no setor; o fallback global
        // com filtro deve evitar o beco e escolher a aberta.
        int[] f = sm.nearestFrontierBiased(4, 5, "agentA1");
        assertEquals(1, f[0], "deve evitar o beco e ir à fronteira aberta");
        assertEquals(5, f[1]);
    }

    // ===== Issue #27 (Cap B): preso por confinamento + escape por abertura =====

    @Test
    void isStuck_confinamentoEmQuadrado() {
        SharedMap sm = mapWithInit();
        // oscila entre 2 células (quadrado 1x1 de span) por toda a janela → preso
        for (int i = 0; i < SharedMap.RECENT_WINDOW; i++)
            sm.recentPos.addLast(new int[]{7 + (i % 2), 5});
        assertTrue(sm.isStuck(), "confinado a 2 células deve ser preso");
    }

    @Test
    void isStuck_progressoNaoEhPreso() {
        SharedMap sm = mapWithInit();
        // anda em linha (span grande) → NÃO preso
        for (int i = 0; i < SharedMap.RECENT_WINDOW; i++)
            sm.recentPos.addLast(new int[]{i, 5});
        assertTrue(!sm.isStuck(), "andar em linha não é preso");
    }

    @Test
    void escapeTarget_apontaParaAbertura() {
        SharedMap sm = mapWithInit();
        wallU(sm);   // boca a OESTE (x6 aberto); N/L/S com parede perto
        // de dentro (8,5), o raio mais longo é a oeste (a boca) → alvo a oeste
        int[] t = sm.escapeTarget(8, 5);
        assertTrue(t[0] < 8, "escape deve mirar a abertura (oeste), veio x=" + t[0]);
        assertEquals(5, t[1]);
    }
}
