package env;

import cartago.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SquadCoordinator extends Artifact {

    ConcurrentHashMap<String, String> agentSquad;          // package-private p/ teste (backfill Track 1)
    ConcurrentHashMap<String, List<String>> squadMembers;
    ConcurrentHashMap<String, String> squadRole;
    private ConcurrentHashMap<String, int[]> meetingPoints;
    private ConcurrentHashMap<String, Set<String>> readyAgents;
    private ConcurrentHashMap<String, String> collectorAssignments;
    private ConcurrentHashMap<String, String> squadActiveTask;
    ConcurrentHashMap<String, Boolean> soloistBusy;
    ConcurrentHashMap<String, int[]> agentPositions;
    private ConcurrentHashMap<String, java.util.Set<String>> taskSoloist;

    void init() {
        agentSquad = new ConcurrentHashMap<>();
        squadMembers = new ConcurrentHashMap<>();
        squadRole = new ConcurrentHashMap<>();
        meetingPoints = new ConcurrentHashMap<>();
        readyAgents = new ConcurrentHashMap<>();
        collectorAssignments = new ConcurrentHashMap<>();
        squadActiveTask = new ConcurrentHashMap<>();
        soloistBusy = new ConcurrentHashMap<>();
        agentPositions = new ConcurrentHashMap<>();
        taskSoloist = new ConcurrentHashMap<>();
        // taskSoloist maps task name → set of assigned agent names (max 2)
        setupDefaultSquads();
    }

    private void setupDefaultSquads() {
        String[][] squads = {
            {"squad1", "connectionA1", "connectionA4", "connectionA5", "connectionA10"},
            {"squad2", "connectionA2", "connectionA6", "connectionA7", "connectionA11"},
            {"squad3", "connectionA3", "connectionA8", "connectionA9", "connectionA12"}
        };
        String[][] roles = {
            {"connectionA1", "leader"}, {"connectionA2", "leader"}, {"connectionA3", "leader"},
            {"connectionA4", "collector"}, {"connectionA5", "collector"},
            {"connectionA6", "collector"}, {"connectionA7", "collector"},
            {"connectionA8", "collector"}, {"connectionA9", "collector"},
            {"connectionA10", "assembler"}, {"connectionA11", "assembler"}, {"connectionA12", "assembler"},
            {"connectionA13", "sentinel"}, {"connectionA14", "sentinel"}, {"connectionA15", "sentinel"}
        };
        for (String[] sq : squads) {
            String sid = sq[0];
            List<String> members = new ArrayList<>();
            for (int i = 1; i < sq.length; i++) {
                agentSquad.put(sq[i], sid);
                members.add(sq[i]);
            }
            squadMembers.put(sid, members);
        }
        for (String[] r : roles) {
            squadRole.put(r[0], r[1]);
        }
        String[] soloists = {
            "connectionA1", "connectionA2", "connectionA3",
            "connectionA4", "connectionA5", "connectionA6", "connectionA7",
            "connectionA8", "connectionA9", "connectionA10", "connectionA11",
            "connectionA12", "connectionA13", "connectionA14", "connectionA15"
        };
        for (String ag : soloists) {
            soloistBusy.put(ag, false);
        }
    }

    @OPERATION
    void get_my_squad(Object oagentName, OpFeedbackParam<String> squadId) {
        String ag = oagentName.toString();
        squadId.set(agentSquad.getOrDefault(ag, "none"));
    }

    @OPERATION
    void get_squad_collectors(Object osquadId,
                              OpFeedbackParam<String> col1,
                              OpFeedbackParam<String> col2) {
        String sid = osquadId.toString();
        List<String> members = squadMembers.getOrDefault(sid, Collections.emptyList());
        List<String> collectors = new ArrayList<>();
        for (String m : members) {
            if ("collector".equals(squadRole.get(m))) {
                collectors.add(m);
            }
        }
        col1.set(collectors.size() > 0 ? collectors.get(0) : "none");
        col2.set(collectors.size() > 1 ? collectors.get(1) : "none");
    }

    @OPERATION
    void get_squad_assembler(Object osquadId, OpFeedbackParam<String> assembler) {
        String sid = osquadId.toString();
        List<String> members = squadMembers.getOrDefault(sid, Collections.emptyList());
        for (String m : members) {
            if ("assembler".equals(squadRole.get(m))) {
                assembler.set(m);
                return;
            }
        }
        assembler.set("none");
    }

    @OPERATION
    void get_squad_leader(Object osquadId, OpFeedbackParam<String> leader) {
        String sid = osquadId.toString();
        List<String> members = squadMembers.getOrDefault(sid, Collections.emptyList());
        for (String m : members) {
            if ("leader".equals(squadRole.get(m))) {
                leader.set(m);
                return;
            }
        }
        leader.set("none");
    }

    @OPERATION
    void set_meeting_point(Object osquadId, Object ox, Object oy) {
        String sid = osquadId.toString();
        int x = toInt(ox), y = toInt(oy);
        meetingPoints.put(sid, new int[]{x, y});
        signal("meeting_point_set", sid, x, y);
    }

    @OPERATION
    void get_meeting_point(Object osquadId,
                           OpFeedbackParam<Integer> resX,
                           OpFeedbackParam<Integer> resY) {
        String sid = osquadId.toString();
        int[] mp = meetingPoints.get(sid);
        if (mp != null) {
            resX.set(mp[0]);
            resY.set(mp[1]);
        } else {
            resX.set(-1);
            resY.set(-1);
        }
    }

    @OPERATION
    void assign_block_to_collector(Object oagentName, Object oblockType) {
        String agName = oagentName.toString();
        String blockType = oblockType.toString();
        collectorAssignments.put(agName, blockType);
        signal("collect_order", agName, blockType);
    }

    @OPERATION
    void get_my_assignment(Object oagentName, OpFeedbackParam<String> blockType) {
        blockType.set(collectorAssignments.getOrDefault(oagentName.toString(), "none"));
    }

    @OPERATION
    void set_squad_task(Object osquadId, Object otaskName) {
        squadActiveTask.put(osquadId.toString(), otaskName.toString());
    }

    @OPERATION
    void get_squad_task(Object osquadId, OpFeedbackParam<String> taskName) {
        taskName.set(squadActiveTask.getOrDefault(osquadId.toString(), "none"));
    }

    @OPERATION
    void signal_ready(Object osquadId, Object oagentName) {
        String sid = osquadId.toString();
        String ag = oagentName.toString();
        readyAgents.computeIfAbsent(sid, k -> ConcurrentHashMap.newKeySet()).add(ag);
        signal("agent_ready", sid, ag);
    }

    @OPERATION
    void all_ready(Object osquadId, OpFeedbackParam<Boolean> result) {
        String sid = osquadId.toString();
        Set<String> ready = readyAgents.get(sid);
        List<String> collectors = new ArrayList<>();
        for (String m : squadMembers.getOrDefault(sid, Collections.emptyList())) {
            if ("collector".equals(squadRole.get(m)) && collectorAssignments.containsKey(m)) {
                collectors.add(m);
            }
        }
        int expected = collectors.size();
        if (expected == 0) { result.set(false); return; }
        int readyCount = 0;
        if (ready != null) {
            for (String c : collectors) {
                if (ready.contains(c)) readyCount++;
            }
        }
        result.set(readyCount >= expected);
    }

    @OPERATION
    void clear_ready(Object osquadId) {
        readyAgents.remove(osquadId.toString());
        String sid = osquadId.toString();
        List<String> members = squadMembers.getOrDefault(sid, Collections.emptyList());
        for (String m : members) {
            collectorAssignments.remove(m);
        }
    }

    @OPERATION
    void mark_busy(Object oagName) {
        soloistBusy.put(oagName.toString(), true);
    }

    @OPERATION
    void mark_free(Object oagName) {
        soloistBusy.put(oagName.toString(), false);
    }

    @OPERATION
    void update_agent_pos(Object oagName, Object ox, Object oy) {
        agentPositions.put(oagName.toString(), new int[]{toInt(ox), toInt(oy)});
    }

    @OPERATION
    void find_free_soloist(Object odispX, Object odispY,
                           OpFeedbackParam<String> winner) {
        int dx = toInt(odispX), dy = toInt(odispY);
        String best = "none";
        int bestDist = Integer.MAX_VALUE;
        for (Map.Entry<String, Boolean> e : soloistBusy.entrySet()) {
            if (e.getValue()) continue;
            int[] pos = agentPositions.get(e.getKey());
            if (pos == null) continue;
            int dist = wrapDist(pos[0], dx, hive.GridConfig.width())
                     + wrapDist(pos[1], dy, hive.GridConfig.height());
            if (dist < bestDist) {
                bestDist = dist;
                best = e.getKey();
            }
        }
        winner.set(best);
    }

    @OPERATION
    void is_soloist_busy(Object oagName, OpFeedbackParam<Boolean> busy) {
        busy.set(soloistBusy.getOrDefault(oagName.toString(), false));
    }

    @OPERATION
    void claim_task_soloist(Object otaskName, Object oagName, OpFeedbackParam<Boolean> claimed) {
        String task = otaskName.toString();
        String agent = oagName.toString();
        java.util.Set<String> agents = taskSoloist.computeIfAbsent(task, k -> java.util.Collections.newSetFromMap(new ConcurrentHashMap<>()));
        if (agents.size() < 2) {
            agents.add(agent);
            claimed.set(true);
        } else {
            claimed.set(false);
        }
    }

    @OPERATION
    void release_task_soloist(Object otaskName) {
        taskSoloist.remove(otaskName.toString());
    }

    @OPERATION
    void release_agent_from_task(Object otaskName, Object oagName) {
        String task = otaskName.toString();
        String agent = oagName.toString();
        java.util.Set<String> agents = taskSoloist.get(task);
        if (agents != null) {
            agents.remove(agent);
            if (agents.isEmpty()) taskSoloist.remove(task);
        }
    }

    int wrapDist(int a, int b, int size) {   // package-private p/ teste (backfill Track 1)
        int d = Math.abs(a - b);
        return Math.min(d, size - d);
    }

    private int toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        return Integer.parseInt(o.toString());
    }
}
