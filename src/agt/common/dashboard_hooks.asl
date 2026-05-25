// ============================================================
// dashboard_hooks.asl — Hooks para o HiveDashboard (WebSocket)
// Incluir em todos os agentes que devem reportar ao dashboard.
// Todas as operacoes usam fallback -! para nao quebrar agentes
// se o artefato nao estiver disponivel.
// ============================================================

+!setup_hive_dashboard
    <- lookupArtifact("hive_dashboard", HdId); focus(HdId).
-!setup_hive_dashboard
    <- .wait(50); !try_create_dashboard.
+!try_create_dashboard
    <- makeArtifact("hive_dashboard", "env.HiveDashboard", [], HdId); focus(HdId).
-!try_create_dashboard
    <- .wait(100); !setup_hive_dashboard.

+!dash_log(EventType, DataJson)
    <- .my_name(Me);
       log_event(EventType, Me, DataJson).
-!dash_log(_, _) <- true.

+!dash_step(N)
    <- set_step(N).
-!dash_step(_) <- true.

+!dash_step_safe : step(N)
    <- !dash_step(N);
       !dash_agent_state.
+!dash_step_safe <- true.
-!dash_step_safe <- true.

+!dash_agent_state
    : my_pos(MX, MY) & my_role_type(Role) & my_energy(E) & lastAction(LA) & lastActionResult(LR) & am_deactivated & has_destination(DX, DY)
    <- .my_name(Me);
       .concat("{\"x\":", MX, ",\"y\":", MY, ",\"role\":\"", Role, "\",\"energy\":", E,
               ",\"action\":\"", LA, "\",\"result\":\"", LR,
               "\",\"active\":false,\"destX\":", DX, ",\"destY\":", DY, "}", SJ);
       log_event("agent_state", Me, SJ).

+!dash_agent_state
    : my_pos(MX, MY) & my_role_type(Role) & my_energy(E) & lastAction(LA) & lastActionResult(LR) & am_deactivated
    <- .my_name(Me);
       .concat("{\"x\":", MX, ",\"y\":", MY, ",\"role\":\"", Role, "\",\"energy\":", E,
               ",\"action\":\"", LA, "\",\"result\":\"", LR,
               "\",\"active\":false}", SJ);
       log_event("agent_state", Me, SJ).

+!dash_agent_state
    : my_pos(MX, MY) & my_role_type(Role) & my_energy(E) & lastAction(LA) & lastActionResult(LR) & has_destination(DX, DY)
    <- .my_name(Me);
       .concat("{\"x\":", MX, ",\"y\":", MY, ",\"role\":\"", Role, "\",\"energy\":", E,
               ",\"action\":\"", LA, "\",\"result\":\"", LR,
               "\",\"active\":true,\"destX\":", DX, ",\"destY\":", DY, "}", SJ);
       log_event("agent_state", Me, SJ).

+!dash_agent_state
    : my_pos(MX, MY) & my_role_type(Role) & my_energy(E) & lastAction(LA) & lastActionResult(LR)
    <- .my_name(Me);
       .concat("{\"x\":", MX, ",\"y\":", MY, ",\"role\":\"", Role, "\",\"energy\":", E,
               ",\"action\":\"", LA, "\",\"result\":\"", LR,
               "\",\"active\":true}", SJ);
       log_event("agent_state", Me, SJ).

+!dash_agent_state
    : my_pos(MX, MY) & my_role_type(Role) & my_energy(E)
    <- .my_name(Me);
       .concat("{\"x\":", MX, ",\"y\":", MY, ",\"role\":\"", Role, "\",\"energy\":", E,
               ",\"action\":\"none\",\"result\":\"none\",\"active\":true}", SJ);
       log_event("agent_state", Me, SJ).

+!dash_agent_state
    : my_pos(MX, MY) & my_role_type(Role)
    <- .my_name(Me);
       .concat("{\"x\":", MX, ",\"y\":", MY, ",\"role\":\"", Role,
               "\",\"energy\":-1,\"action\":\"none\",\"result\":\"none\",\"active\":true}", SJ);
       log_event("agent_state", Me, SJ).

+!dash_agent_state <- true.
-!dash_agent_state <- true.

+!dash_score(S)
    <- update_score(S).
-!dash_score(_) <- true.

+!dash_task_phase(TaskName, Phase, Progress)
    <- update_task_phase(TaskName, Phase, Progress).
-!dash_task_phase(_, _, _) <- true.

+!dash_squad(SquadId, MembersJson)
    <- update_squad(SquadId, MembersJson).
-!dash_squad(_, _) <- true.

+!dash_map_dispenser(X, Y, Type)
    <- register_map_dispenser(X, Y, Type).
-!dash_map_dispenser(_, _, _) <- true.

+!dash_map_goal_zone(X, Y)
    <- register_map_goal_zone(X, Y).
-!dash_map_goal_zone(_, _) <- true.
