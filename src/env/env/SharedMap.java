package env;

import cartago.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SharedMap extends Artifact {

    private ConcurrentHashMap<String, String> cells;
    private Set<String> knownDispensers;
    private Set<String> knownGoalZones;
    private Set<String> knownRoleZones;
    private Set<String> visitedCells;
    private ConcurrentHashMap<String, Integer> obstacles;
    private int gridWidth = 0;
    private int gridHeight = 0;
    private List<int[]> cachedFrontiers = new ArrayList<>();
    private int lastFrontierVisitedSize = -1;

    void init() {
        cells = new ConcurrentHashMap<>();
        knownDispensers = ConcurrentHashMap.newKeySet();
        knownGoalZones = ConcurrentHashMap.newKeySet();
        knownRoleZones = ConcurrentHashMap.newKeySet();
        visitedCells = ConcurrentHashMap.newKeySet();
        obstacles = new ConcurrentHashMap<>();
    }

    @OPERATION
    void set_grid_dimensions(Object owidth, Object oheight) {
        gridWidth = toInt(owidth);
        gridHeight = toInt(oheight);
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

    private String astar(int fx, int fy, int tx, int ty) {
        fx = normX(fx); fy = normY(fy);
        tx = normX(tx); ty = normY(ty);
        int mDist = wrappedManhattan(fx, fy, tx, ty);
        if (mDist > 60) return greedy(fx, fy, tx, ty);

        Set<String> blocked = new HashSet<>(obstacles.keySet());
        blocked.remove(tx + "," + ty);
        blocked.remove(fx + "," + fy);
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
                int ng = cg + 1;
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
}
