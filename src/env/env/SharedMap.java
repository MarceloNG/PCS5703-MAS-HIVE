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
    // fallback para frontier global quando o setor está vazio.
    int[] nearestFrontierBiased(int agX, int agY, String agentName) {
        int heading = -1;
        int idx = extractAgentIndex(agentName);
        if (idx >= 0) heading = idx % 4;  // 0=N, 1=E, 2=S, 3=W

        int bestDist = Integer.MAX_VALUE;
        int bx = agX, by = agY;

        if (heading >= 0) {
            for (int[] f : cachedFrontiers) {
                if (!inPreferredDirection(f[0], f[1], agX, agY, heading)) continue;
                int dist = wrappedManhattan(f[0], f[1], agX, agY);
                if (dist < bestDist) { bestDist = dist; bx = f[0]; by = f[1]; }
            }
        }

        if (bestDist == Integer.MAX_VALUE) {
            for (int[] f : cachedFrontiers) {
                int dist = wrappedManhattan(f[0], f[1], agX, agY);
                if (dist < bestDist) { bestDist = dist; bx = f[0]; by = f[1]; }
            }
        }

        return new int[]{bx, by};
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
