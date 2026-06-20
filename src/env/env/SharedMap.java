package env;

import cartago.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SharedMap extends Artifact {

    ConcurrentHashMap<String, String> cells;              // package-private p/ teste (Fase D / R7)
    Set<String> knownDispensers;                          // package-private p/ teste (Fase D / R7)
    Set<String> knownGoalZones;                           // package-private p/ teste (Fase D / R7)
    Set<String> knownRoleZones;                           // package-private p/ teste (Fase D / R7)
    Set<String> visitedCells;                             // package-private p/ teste (Fase D / R7)
    ConcurrentHashMap<String, Integer> obstacles;         // package-private p/ teste (backfill Track 1)
    ConcurrentHashMap<String, int[]> occupancy;           // nome -> {x, y, step}
    int occupancyStep = 0;                                // ultimo step reportado (p/ expirar entradas obsoletas)
    private static final int TEAMMATE_PENALTY = 16;
    int gridWidth = 0;
    int gridHeight = 0;
    List<int[]> cachedFrontiers = new ArrayList<>();       // package-private p/ teste
    private int lastFrontierVisitedSize = -1;
    final ArrayDeque<int[]> recentPos = new ArrayDeque<>(); // #27: posições recentes (detecção de preso)
    static final int RECENT_WINDOW = 10;                    // janela p/ medir confinamento
    static final int STUCK_SPAN = 3;                        // bbox <= 3x3 na janela → preso

    void init() {
        cells = new ConcurrentHashMap<>();
        knownDispensers = ConcurrentHashMap.newKeySet();
        knownGoalZones = ConcurrentHashMap.newKeySet();
        knownRoleZones = ConcurrentHashMap.newKeySet();
        visitedCells = ConcurrentHashMap.newKeySet();
        obstacles = new ConcurrentHashMap<>();
        occupancy = new ConcurrentHashMap<>();
    }

    @OPERATION
    void set_grid_dimensions(Object owidth, Object oheight) {
        gridWidth = toInt(owidth);
        gridHeight = toInt(oheight);
        hive.GridConfig.set(gridWidth, gridHeight);
    }

    /**
     * U4: define as dimensões a partir da fonte única hive.GridConfig
     * (default 40x40; override por -Dhive.grid.width/-Dhive.grid.height).
     */
    @OPERATION
    void apply_grid_config() {
        gridWidth = hive.GridConfig.width();
        gridHeight = hive.GridConfig.height();
    }

    private int norm(int v, int size) {
        if (size <= 0) return v;
        return ((v % size) + size) % size;
    }

    private int normX(int x) { return norm(x, gridWidth); }
    private int normY(int y) { return norm(y, gridHeight); }

    private String key(int x, int y) {
        return normX(x) + "," + normY(y);
    }

    private int wrapDist(int a, int b, int size) {
        if (size <= 0) return Math.abs(a - b);
        int d = Math.abs(norm(a, size) - norm(b, size));
        return Math.min(d, size - d);
    }

    private int wrappedManhattan(int x1, int y1, int x2, int y2) {
        return wrapDist(x1, x2, gridWidth) + wrapDist(y1, y2, gridHeight);
    }

    private int toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        return Integer.parseInt(o.toString());
    }

    @OPERATION
    void update_cell(Object ox, Object oy, Object otype, Object odetails) {
        int x = normX(toInt(ox));
        int y = normY(toInt(oy));
        String type = otype.toString();
        String details = odetails.toString();
        String k = x + "," + y;
        cells.put(k, type + ":" + details);
        visitedCells.add(k);

        if (type.equals("dispenser")) {
            String dispKey = k + ":" + details;
            if (knownDispensers.add(dispKey)) {
                defineObsProperty("known_dispenser", x, y, details);
                signal("new_dispenser", x, y, details);
            }
        } else if (type.equals("goal_zone")) {
            if (knownGoalZones.add(k)) {
                defineObsProperty("known_goal_zone", x, y);
                signal("new_goal_zone", x, y);
            }
        } else if (type.equals("role_zone")) {
            if (knownRoleZones.add(k)) {
                defineObsProperty("known_role_zone", x, y);
                signal("new_role_zone", x, y);
            }
        } else if (type.equals("obstacle")) {
            obstacles.put(k, Integer.MAX_VALUE);
        }
    }

    @OPERATION
    void mark_visited(Object ox, Object oy) {
        String k = key(toInt(ox), toInt(oy));
        visitedCells.add(k);
        obstacles.remove(k);
    }

    @OPERATION
    void get_nearest_dispenser(Object oagX, Object oagY, Object otype,
                               OpFeedbackParam<Integer> resX,
                               OpFeedbackParam<Integer> resY) {
        int agX = normX(toInt(oagX)), agY = normY(toInt(oagY));
        String type = otype.toString();
        int bestCost = Integer.MAX_VALUE;
        int bx = -1, by = -1;

        int nearestGoalX = -1, nearestGoalY = -1;
        int bestGoalDist = Integer.MAX_VALUE;
        for (String gk : knownGoalZones) {
            String[] gp = gk.split(",");
            int gx = Integer.parseInt(gp[0]), gy = Integer.parseInt(gp[1]);
            int gd = wrappedManhattan(gx, gy, agX, agY);
            if (gd < bestGoalDist) { bestGoalDist = gd; nearestGoalX = gx; nearestGoalY = gy; }
        }

        for (String dispKey : knownDispensers) {
            String[] parts = dispKey.split("[:,]");
            if (parts.length >= 3 && parts[2].equals(type)) {
                int dx = Integer.parseInt(parts[0]);
                int dy = Integer.parseInt(parts[1]);
                int costToDisp = wrappedManhattan(dx, dy, agX, agY);
                int costDispToGoal = 0;
                if (nearestGoalX != -1) {
                    costDispToGoal = wrappedManhattan(dx, dy, nearestGoalX, nearestGoalY);
                }
                int totalCost = costToDisp + costDispToGoal;
                if (totalCost < bestCost) {
                    bestCost = totalCost;
                    bx = dx;
                    by = dy;
                }
            }
        }
        resX.set(bx);
        resY.set(by);
    }

    @OPERATION
    void get_nearest_goal_zone(Object oagX, Object oagY,
                               OpFeedbackParam<Integer> resX,
                               OpFeedbackParam<Integer> resY) {
        int agX = normX(toInt(oagX)), agY = normY(toInt(oagY));
        int bestDist = Integer.MAX_VALUE;
        int bx = -1, by = -1;

        for (String k : knownGoalZones) {
            String[] parts = k.split(",");
            int gx = Integer.parseInt(parts[0]);
            int gy = Integer.parseInt(parts[1]);
            int pathCost = astarCost(agX, agY, gx, gy);
            if (pathCost < bestDist) {
                bestDist = pathCost;
                bx = gx;
                by = gy;
            }
        }
        resX.set(bx);
        resY.set(by);
    }

    // Fase C / U1: role-zone LEMBRADA mais proxima (custo A*), p/ navegar a ela
    // fora da visao e adotar role. Espelha get_nearest_goal_zone; -1,-1 se nenhuma.
    @OPERATION
    void get_nearest_role_zone(Object oagX, Object oagY,
                               OpFeedbackParam<Integer> resX,
                               OpFeedbackParam<Integer> resY) {
        int[] rz = nearestRoleZone(normX(toInt(oagX)), normY(toInt(oagY)));
        resX.set(rz[0]);
        resY.set(rz[1]);
    }

    // Logica pura (package-private p/ teste, Fase C / U1): {x,y} da role-zone mais
    // proxima por custo A*, ou {-1,-1} se nenhuma conhecida.
    int[] nearestRoleZone(int agX, int agY) {
        int bestDist = Integer.MAX_VALUE;
        int bx = -1, by = -1;
        for (String k : knownRoleZones) {
            String[] parts = k.split(",");
            int rx = Integer.parseInt(parts[0]);
            int ry = Integer.parseInt(parts[1]);
            int pathCost = astarCost(agX, agY, rx, ry);
            if (pathCost < bestDist) {
                bestDist = pathCost;
                bx = rx;
                by = ry;
            }
        }
        return new int[]{bx, by};
    }

    @OPERATION
    void get_alternative_goal_zone(Object oagX, Object oagY, Object ocurX, Object ocurY,
                                   OpFeedbackParam<Integer> resX,
                                   OpFeedbackParam<Integer> resY) {
        int agX = normX(toInt(oagX)), agY = normY(toInt(oagY));
        int curX = normX(toInt(ocurX)), curY = normY(toInt(ocurY));
        String curKey = curX + "," + curY;
        int bestDist = Integer.MAX_VALUE;
        int bx = -1, by = -1;
        for (String k : knownGoalZones) {
            if (k.equals(curKey)) continue;
            String[] parts = k.split(",");
            int gx = Integer.parseInt(parts[0]);
            int gy = Integer.parseInt(parts[1]);
            int dist = astarCost(agX, agY, gx, gy);
            if (dist < bestDist) {
                bestDist = dist;
                bx = gx;
                by = gy;
            }
        }
        if (bx == -1) { bx = curX; by = curY; }
        resX.set(bx);
        resY.set(by);
    }

    private void rebuildFrontierCache() {
        List<int[]> result = new ArrayList<>();
        int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
        Set<String> seen = new HashSet<>();
        for (String visited : visitedCells) {
            String[] parts = visited.split(",");
            int vx = Integer.parseInt(parts[0]);
            int vy = Integer.parseInt(parts[1]);
            for (int[] d : dirs) {
                int nx = normX(vx + d[0]);
                int ny = normY(vy + d[1]);
                String nk = nx + "," + ny;
                if (!visitedCells.contains(nk) && !seen.contains(nk)) {
                    String cellContent = cells.get(nk);
                    if (cellContent == null || !cellContent.startsWith("obstacle")) {
                        result.add(new int[]{nx, ny});
                        seen.add(nk);
                    }
                }
            }
        }
        cachedFrontiers = result;
    }

    @OPERATION
    void get_nearest_frontier(Object oagX, Object oagY,
                              OpFeedbackParam<Integer> resX,
                              OpFeedbackParam<Integer> resY) {
        int agX = normX(toInt(oagX)), agY = normY(toInt(oagY));
        int vSize = visitedCells.size();
        if (cachedFrontiers.isEmpty() || Math.abs(vSize - lastFrontierVisitedSize) >= 3) {
            rebuildFrontierCache();
            lastFrontierVisitedSize = vSize;
        }

        int bestDist = Integer.MAX_VALUE;
        int bx = agX, by = agY;
        for (int[] f : cachedFrontiers) {
            int dist = wrappedManhattan(f[0], f[1], agX, agY);
            if (dist < bestDist) {
                bestDist = dist;
                bx = f[0];
                by = f[1];
            }
        }
        resX.set(bx);
        resY.set(by);
    }

    @OPERATION
    void get_nearest_frontier_biased(Object oagX, Object oagY, Object oAgentName,
                                     OpFeedbackParam<Integer> resX,
                                     OpFeedbackParam<Integer> resY) {
        int agX = normX(toInt(oagX)), agY = normY(toInt(oagY));
        int vSize = visitedCells.size();
        if (cachedFrontiers.isEmpty() || Math.abs(vSize - lastFrontierVisitedSize) >= 3) {
            rebuildFrontierCache();
            lastFrontierVisitedSize = vSize;
        }
        int[] result = nearestFrontierBiased(agX, agY, oAgentName.toString());
        resX.set(result[0]);
        resY.set(result[1]);
    }

    // Núcleo testável: seleciona frontier mais próxima no setor preferencial do agente;
    // fallback para frontier global quando o setor está vazio. Issue #27 (Cap A): pula
    // fronteiras em cul-de-sac (beco visível); se TODAS forem beco, não filtra (evita
    // ficar sem destino).
    int[] nearestFrontierBiased(int agX, int agY, String agentName) {
        int heading = -1;
        int idx = extractAgentIndex(agentName);
        if (idx >= 0) heading = idx % 4;  // 0=N, 1=E, 2=S, 3=W

        int[] r = pickFrontier(agX, agY, heading, true);    // evitando becos
        if (r == null) r = pickFrontier(agX, agY, heading, false);  // fallback: aceita beco
        return r != null ? r : new int[]{agX, agY};
    }

    // Setor preferencial primeiro; global se o setor vazia. Com avoidCulDeSac, pula
    // fronteiras que o isCulDeSacFrontier marca. Devolve null se nada elegível.
    private int[] pickFrontier(int agX, int agY, int heading, boolean avoidCulDeSac) {
        int bestDist = Integer.MAX_VALUE;
        int bx = -1, by = -1;
        if (heading >= 0) {
            for (int[] f : cachedFrontiers) {
                if (!inPreferredDirection(f[0], f[1], agX, agY, heading)) continue;
                if (avoidCulDeSac && isCulDeSacFrontier(agX, agY, f[0], f[1])) continue;
                int dist = wrappedManhattan(f[0], f[1], agX, agY);
                if (dist < bestDist) { bestDist = dist; bx = f[0]; by = f[1]; }
            }
        }
        if (bestDist == Integer.MAX_VALUE) {
            for (int[] f : cachedFrontiers) {
                if (avoidCulDeSac && isCulDeSacFrontier(agX, agY, f[0], f[1])) continue;
                int dist = wrappedManhattan(f[0], f[1], agX, agY);
                if (dist < bestDist) { bestDist = dist; bx = f[0]; by = f[1]; }
            }
        }
        return bestDist == Integer.MAX_VALUE ? null : new int[]{bx, by};
    }

    // ===== issue #27 (Cap A): detecção de cul-de-sac (beco de UMA boca) =====
    // Vista do agente, F é cul-de-sac se, expandindo SÓ para o lado OPOSTO a ele
    // (células não mais próximas do agente que F), a região livre FECHA — cercada por
    // obstáculos — dentro do orçamento. A "boca" fica ENTRE F e o agente (lado mais
    // próximo) e por isso não é explorada: corredores (2 saídas) e campo aberto
    // estouram o orçamento e NÃO são marcados; só o beco real fecha. Reconhece o U de
    // 3 paredes/1 boca, não a trema (¨) nem a barra dupla (||). Puro → testável (ms).
    // Conservador: na dúvida (região grande) devolve false (não recusa corredor).
    static final int CULDESAC_BUDGET = 64;   // ~cabe um beco até a visão; aberto estoura

    boolean isCulDeSacFrontier(int agX, int agY, int fx, int fy) {
        agX = normX(agX); agY = normY(agY);
        fx = normX(fx); fy = normY(fy);
        String fk = fx + "," + fy;
        if (obstacles.containsKey(fk)) return false;   // F é parede: não conta
        // Bloqueia obstáculos + o anel-1 ao redor do agente (o gargalo). Se a ÚNICA
        // saída de F p/ o espaço aberto passa pela vizinhança do agente, a região fecha
        // dentro do orçamento → beco. Sela a boca mesmo com o agente colado nela (onde o
        // corte por distância falhava). Vale dos dois lados: evitar (agente fora, anel
        // sela a boca) e escapar (agente dentro, anel sela o fundo → fronteira p/ a saída
        // NÃO fecha → é escolhida). Aberto/corredor/2-pontos estouram o orçamento.
        Set<String> blocked = new HashSet<>(obstacles.keySet());
        int[][] ring = {{0,0},{0,-1},{0,1},{-1,0},{1,0},{-1,-1},{-1,1},{1,-1},{1,1}};
        for (int[] r : ring) {
            String k = normX(agX + r[0]) + "," + normY(agY + r[1]);
            if (!k.equals(fk)) blocked.add(k);
        }
        int[][] dirs = {{0,-1},{0,1},{-1,0},{1,0}};
        Deque<int[]> stack = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        stack.push(new int[]{fx, fy});
        seen.add(fk);
        while (!stack.isEmpty()) {
            if (seen.size() > CULDESAC_BUDGET) return false;      // região grande → aberto
            int[] c = stack.pop();
            for (int[] d : dirs) {
                int nx = normX(c[0] + d[0]);
                int ny = normY(c[1] + d[1]);
                String nk = nx + "," + ny;
                if (seen.contains(nk) || blocked.contains(nk)) continue;
                seen.add(nk);
                stack.push(new int[]{nx, ny});
            }
        }
        return true;   // fechou dentro do orçamento → beco
    }

    // @OPERATION fino p/ o .asl (KTD8, meia-volta antecipada): expõe a detecção sobre a
    // célula-destino atual. 1 = é beco (deve dar meia-volta), 0 = segue.
    @OPERATION
    void is_cul_de_sac(Object oagX, Object oagY, Object otx, Object oty,
                       OpFeedbackParam<Integer> res) {
        res.set(isCulDeSacFrontier(toInt(oagX), toInt(oagY), toInt(otx), toInt(oty)) ? 1 : 0);
    }

    // ===== issue #27 (Cap B): "não ficar preso, SAIR" — escape por A*-para-longe =====
    // Detecta preso por POSIÇÃO (oscilação num ciclo pequeno) — gatilho fácil e robusto,
    // ao contrário de detectar "isto é um beco?" (chokepoint, difícil). Quando preso, o
    // agente mira uma fronteira LONGE e não-beco; o A* (ciente das paredes via #15) roteia
    // para FORA do pocket pela boca e embora — sai de qualquer ângulo, pequeno ou grande.

    @OPERATION
    void note_recent_pos(Object ox, Object oy) {
        recentPos.addLast(new int[]{normX(toInt(ox)), normY(toInt(oy))});
        while (recentPos.size() > RECENT_WINDOW) recentPos.removeFirst();
    }

    @OPERATION
    void is_stuck(OpFeedbackParam<Integer> res) {
        res.set(isStuck() ? 1 : 0);
    }

    // Preso = CONFINADO a uma bounding-box pequena na janela recente (o agente oscila
    // num cantinho, mesmo visitando >4 células distintas no ciclo). Mais robusto que
    // contar distintas — pega ciclos de 6-8 células num quadrado 3x3.
    boolean isStuck() {
        if (recentPos.size() < RECENT_WINDOW) return false;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int[] p : recentPos) {
            minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
        }
        return (maxX - minX) <= STUCK_SPAN && (maxY - minY) <= STUCK_SPAN;
    }

    // Alvo de escape por RAY-CASTING: a direção cardinal com MAIS células livres até
    // bater parede é a abertura (boca) do pocket. Mira fundo nela; o A* (ciente das
    // paredes) roteia p/ fora. Robusto p/ U axis-aligned, não depende de existir
    // fronteira distante (o agente preso não explorou p/ fora).
    static final int ESCAPE_RAY = 10;     // alcance do raio
    static final int ESCAPE_REACH = 8;    // quão fundo mirar na abertura

    @OPERATION
    void get_escape_target(Object oagX, Object oagY,
                           OpFeedbackParam<Integer> resX, OpFeedbackParam<Integer> resY) {
        int[] r = escapeTarget(normX(toInt(oagX)), normY(toInt(oagY)));
        resX.set(r[0]);
        resY.set(r[1]);
    }

    int[] escapeTarget(int agX, int agY) {
        int[][] dirs = {{0,-1},{0,1},{-1,0},{1,0}};   // N,S,O,L
        int bestDir = -1, bestFree = -1;
        for (int d = 0; d < 4; d++) {
            int free = 0;
            for (int s = 1; s <= ESCAPE_RAY; s++) {
                int nx = normX(agX + dirs[d][0] * s);
                int ny = normY(agY + dirs[d][1] * s);
                if (obstacles.containsKey(nx + "," + ny)) break;
                free++;
            }
            if (free > bestFree) { bestFree = free; bestDir = d; }
        }
        if (bestDir < 0) return new int[]{agX, agY};
        return new int[]{ normX(agX + dirs[bestDir][0] * ESCAPE_REACH),
                          normY(agY + dirs[bestDir][1] * ESCAPE_REACH) };
    }

    // Extrai o sufixo numérico do nome do agente (ex: "connectionA7" → 7; sem dígitos → -1).
    int extractAgentIndex(String name) {
        int i = name.length() - 1;
        while (i >= 0 && Character.isDigit(name.charAt(i))) i--;
        String digits = name.substring(i + 1);
        try { return Integer.parseInt(digits); }
        catch (NumberFormatException e) { return -1; }
    }

    // Retorna true se o frontier (fx,fy) está na direção preferencial em relação ao agente.
    boolean inPreferredDirection(int fx, int fy, int agX, int agY, int heading) {
        switch (heading) {
            case 0: return fy < agY;   // N
            case 1: return fx > agX;   // E
            case 2: return fy > agY;   // S
            case 3: return fx < agX;   // W
            default: return false;
        }
    }

    @OPERATION
    void get_map_stats(OpFeedbackParam<Integer> totalVisited,
                       OpFeedbackParam<Integer> totalDispensers,
                       OpFeedbackParam<Integer> totalGoalZones,
                       OpFeedbackParam<Integer> totalRoleZones) {
        totalVisited.set(visitedCells.size());
        totalDispensers.set(knownDispensers.size());
        totalGoalZones.set(knownGoalZones.size());
        totalRoleZones.set(knownRoleZones.size());
    }

    @OPERATION
    void mark_obstacle(Object ox, Object oy, Object ostep) {
        String k = key(toInt(ox), toInt(oy));
        if (obstacles.containsKey(k)) return;
        obstacles.put(k, toInt(ostep));
    }

    @OPERATION
    void decay_obstacles(Object ostep) {
        int step = toInt(ostep);
        if (step % 5 != 0) return;
        obstacles.entrySet().removeIf(e -> e.getValue() < step - 30);
        // Fase D (#7): poda entradas de ocupacao 'seen_' obsoletas. O astar so usa
        // as do ultimo step (gate occupancyStep-1, linha ~358); as velhas so ocupam
        // memoria e crescem monotonicamente. Entradas de posicao de agente (chave =
        // nome do agente) NAO sao podadas aqui — expiram pelo proprio gate.
        occupancy.entrySet().removeIf(
            e -> e.getKey().startsWith("seen_") && e.getValue()[2] < occupancyStep - 1);
    }

    @OPERATION
    void compute_next_move(Object ofx, Object ofy, Object otx, Object oty,
                           OpFeedbackParam<String> dir) {
        int fx = normX(toInt(ofx)), fy = normY(toInt(ofy));
        int tx = normX(toInt(otx)), ty = normY(toInt(oty));
        if (fx == tx && fy == ty) { dir.set("skip"); return; }

        dir.set(astar(fx, fy, tx, ty));
    }

    @OPERATION
    void manhattan_dist(Object ox1, Object oy1, Object ox2, Object oy2,
                        OpFeedbackParam<Integer> dist) {
        dist.set(wrappedManhattan(toInt(ox1), toInt(oy1), toInt(ox2), toInt(oy2)));
    }

    // #2: indice de ocupacao viva (overlay efemero do A*). Cada agente empurra
    // sua posicao por step (ao lado de update_agent_pos no SquadCoordinator);
    // o astar penaliza essas celulas. Sobrescreve (sem decay).
    @OPERATION
    void update_occupancy(Object oName, Object ox, Object oy, Object ostep) {
        int s = toInt(ostep);
        occupancy.put(oName.toString(), new int[]{normX(toInt(ox)), normY(toInt(oy)), s});
        if (s > occupancyStep) occupancyStep = s;
    }

    private int astarCost(int fx, int fy, int tx, int ty) {
        fx = normX(fx); fy = normY(fy);
        tx = normX(tx); ty = normY(ty);
        int mDist = wrappedManhattan(fx, fy, tx, ty);
        if (mDist > 60) return mDist;

        Set<String> blocked = new HashSet<>(obstacles.keySet());
        blocked.remove(tx + "," + ty);
        blocked.remove(fx + "," + fy);
        int[][] dirs = {{0,-1},{0,1},{-1,0},{1,0}};

        PriorityQueue<int[]> open = new PriorityQueue<>(Comparator.comparingInt(a -> a[2]));
        Set<String> closed = new HashSet<>();
        Map<String, Integer> gScore = new HashMap<>();

        String startKey = fx + "," + fy;
        gScore.put(startKey, 0);
        open.add(new int[]{fx, fy, mDist});

        while (!open.isEmpty() && closed.size() < 3000) {
            int[] cur = open.poll();
            int cx = cur[0], cy = cur[1];
            String ck = cx + "," + cy;
            if (closed.contains(ck)) continue;
            closed.add(ck);
            if (cx == tx && cy == ty) return gScore.get(ck);

            int cg = gScore.get(ck);
            for (int[] d : dirs) {
                int nx = normX(cx + d[0]);
                int ny = normY(cy + d[1]);
                String nk = nx + "," + ny;
                if (blocked.contains(nk) || closed.contains(nk)) continue;
                int ng = cg + 1;
                if (ng < gScore.getOrDefault(nk, Integer.MAX_VALUE)) {
                    gScore.put(nk, ng);
                    open.add(new int[]{nx, ny, ng + wrappedManhattan(tx, ty, nx, ny)});
                }
            }
        }
        return mDist * 3;
    }

    String astar(int fx, int fy, int tx, int ty) {   // package-private p/ teste (backfill Track 1)
        fx = normX(fx); fy = normY(fy);
        tx = normX(tx); ty = normY(ty);
        int mDist = wrappedManhattan(fx, fy, tx, ty);
        if (mDist > 60) return greedy(fx, fy, tx, ty);

        Set<String> blocked = new HashSet<>(obstacles.keySet());
        blocked.remove(tx + "," + ty);
        blocked.remove(fx + "," + fy);
        // #2: overlay de ocupacao viva — penaliza (nao bloqueia) celula de colega,
        // exceto origem e alvo. So no astar (escolha do passo), nao no astarCost.
        Set<String> occupied = new HashSet<>();
        for (int[] p : occupancy.values()) {
            if (p[2] >= occupancyStep - 1) {   // #1: so posicoes frescas; entrada de agente desconectado expira (step congelado)
                occupied.add(p[0] + "," + p[1]);
            }
        }
        occupied.remove(tx + "," + ty);
        occupied.remove(fx + "," + fy);
        int[][] dirs = {{0,-1},{0,1},{-1,0},{1,0}};

        PriorityQueue<int[]> open = new PriorityQueue<>(Comparator.comparingInt(a -> a[2]));
        Set<String> closed = new HashSet<>();
        Map<String, String> cameFrom = new HashMap<>();
        Map<String, Integer> gScore = new HashMap<>();

        String startKey = fx + "," + fy;
        gScore.put(startKey, 0);
        open.add(new int[]{fx, fy, mDist});

        while (!open.isEmpty() && closed.size() < 8000) {
            int[] cur = open.poll();
            int cx = cur[0], cy = cur[1];
            String ck = cx + "," + cy;

            if (closed.contains(ck)) continue;
            closed.add(ck);

            if (cx == tx && cy == ty) {
                String step = ck;
                while (cameFrom.containsKey(step) && !cameFrom.get(step).equals(startKey)) {
                    step = cameFrom.get(step);
                }
                if (!cameFrom.containsKey(step)) step = ck;
                String[] parts = step.split(",");
                int sx = Integer.parseInt(parts[0]), sy = Integer.parseInt(parts[1]);
                int ddx = sx - fx, ddy = sy - fy;
                if (gridWidth > 0 && Math.abs(ddx) > gridWidth / 2) {
                    ddx = ddx > 0 ? ddx - gridWidth : ddx + gridWidth;
                }
                if (gridHeight > 0 && Math.abs(ddy) > gridHeight / 2) {
                    ddy = ddy > 0 ? ddy - gridHeight : ddy + gridHeight;
                }
                if (ddx == 0 && ddy == -1) return "n";
                if (ddx == 0 && ddy == 1)  return "s";
                if (ddx == -1 && ddy == 0) return "w";
                if (ddx == 1 && ddy == 0)  return "e";
                return greedy(fx, fy, tx, ty);
            }

            int cg = gScore.get(ck);
            for (int i = 0; i < 4; i++) {
                int nx = normX(cx + dirs[i][0]);
                int ny = normY(cy + dirs[i][1]);
                String nk = nx + "," + ny;
                if (blocked.contains(nk) || closed.contains(nk)) continue;
                int ng = cg + 1 + (occupied.contains(nk) ? TEAMMATE_PENALTY : 0);
                if (ng < gScore.getOrDefault(nk, Integer.MAX_VALUE)) {
                    gScore.put(nk, ng);
                    cameFrom.put(nk, ck);
                    int f = ng + wrappedManhattan(tx, ty, nx, ny);
                    open.add(new int[]{nx, ny, f});
                }
            }
        }
        return greedy(fx, fy, tx, ty);
    }

    private String greedy(int fx, int fy, int tx, int ty) {
        fx = normX(fx); fy = normY(fy);
        tx = normX(tx); ty = normY(ty);
        int dx, dy;
        if (gridWidth > 0) {
            int rawDx = tx - fx;
            int absDx = Math.abs(rawDx);
            dx = (absDx <= gridWidth - absDx) ? rawDx : (rawDx > 0 ? rawDx - gridWidth : rawDx + gridWidth);
        } else {
            dx = tx - fx;
        }
        if (gridHeight > 0) {
            int rawDy = ty - fy;
            int absDy = Math.abs(rawDy);
            dy = (absDy <= gridHeight - absDy) ? rawDy : (rawDy > 0 ? rawDy - gridHeight : rawDy + gridHeight);
        } else {
            dy = ty - fy;
        }
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx > 0 ? "e" : "w";
        }
        return dy > 0 ? "s" : "n";
    }

    // ===== Fase D / R7: costura de traducao de frame =====
    // Re-keia todo o estado por-celula deste mapa por um offset (dX,dY), traduzindo
    // este frame para outro (toroidalmente, com as dims correntes). Inerte no
    // incremento 1 (nenhuma fusao chama); existe para a fusao cross-agente (U9)
    // entrar como camada de traducao SEM reescrever o mapa, e e exercitada em teste
    // (prova que o mapa por-agente e parametrizado por frame). Nao re-emite obs
    // properties (known_*) — isso fica para a U9.
    // #9 (review): translateCells NAO e @OPERATION (e metodo puro, chamado so em teste).
    // Quando a U9 a invocar a partir de .asl, ela DEVE ser embrulhada num @OPERATION
    // (ex.: merge_frame(dX,dY)) — senao fica invisivel aos agentes/CArtAgO.
    private String shiftKey(String key, int dX, int dY) {
        int ci = key.indexOf(',');
        int x = Integer.parseInt(key.substring(0, ci));
        int y = Integer.parseInt(key.substring(ci + 1));
        return normX(x + dX) + "," + normY(y + dY);
    }

    private Set<String> shiftKeySet(Set<String> in, int dX, int dY) {
        Set<String> out = ConcurrentHashMap.newKeySet();
        for (String k : in) out.add(shiftKey(k, dX, dY));
        return out;
    }

    private Set<String> shiftDispensers(Set<String> in, int dX, int dY) {
        Set<String> out = ConcurrentHashMap.newKeySet();
        for (String k : in) {
            int ci = k.indexOf(',');
            int co = k.indexOf(':');
            int x = Integer.parseInt(k.substring(0, ci));
            int y = Integer.parseInt(k.substring(ci + 1, co));
            String details = k.substring(co + 1);
            out.add(normX(x + dX) + "," + normY(y + dY) + ":" + details);
        }
        return out;
    }

    void translateCells(int dX, int dY) {
        ConcurrentHashMap<String, String> nc = new ConcurrentHashMap<>();
        for (Map.Entry<String, String> e : cells.entrySet()) {
            nc.put(shiftKey(e.getKey(), dX, dY), e.getValue());
        }
        cells = nc;

        ConcurrentHashMap<String, Integer> no = new ConcurrentHashMap<>();
        for (Map.Entry<String, Integer> e : obstacles.entrySet()) {
            no.put(shiftKey(e.getKey(), dX, dY), e.getValue());
        }
        obstacles = no;

        visitedCells = shiftKeySet(visitedCells, dX, dY);
        knownGoalZones = shiftKeySet(knownGoalZones, dX, dY);
        knownRoleZones = shiftKeySet(knownRoleZones, dX, dY);
        knownDispensers = shiftDispensers(knownDispensers, dX, dY);

        for (int[] p : occupancy.values()) {
            p[0] = normX(p[0] + dX);
            p[1] = normY(p[1] + dY);
        }

        // Fase D (#9): as fronteiras em cache estao no frame antigo — invalida p/
        // forcar recomputo no novo frame. (As obs properties known_* tambem ficam
        // stale; re-emiti-las exige estar dentro de um @OPERATION e fica para a U9,
        // quando translateCells ganhar um chamador real — ver comentario acima.)
        cachedFrontiers = new ArrayList<>();
        lastFrontierVisitedSize = -1;
    }
}
