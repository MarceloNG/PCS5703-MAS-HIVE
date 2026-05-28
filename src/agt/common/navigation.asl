// ============================================================
// navigation.asl — Navegacao step-by-step
// Chamado quando connect_protocol e collection nao interceptam o step
// ============================================================

// --- Collector: chegou ao meeting point → sinalizar pronto ---

+step(N)
    : has_destination(DX, DY) & my_pos(MX, MY) & DX == MX & DY == MY
      & navigating_to_meeting_point(SquadId)
    <- -has_destination(DX, DY);
       -navigating_to_meeting_point(SquadId);
       .my_name(Me);
       signal_ready(SquadId, Me);
       .concat("{\"squad\":\"", SquadId, "\"}", AMJson);
       !dash_log("arrived_meeting", AMJson);
       .print("[NAV] Step ", N, ": Cheguei ao meeting point! Sinalizando pronto.");
       action("skip").

// --- Assembler: chegou ao meeting point para connect ---

+step(N)
    : has_destination(DX, DY) & my_pos(MX, MY) & DX == MX & DY == MY
      & navigating_to_meeting_for_connect(SquadId, _, TaskName)
    <- -has_destination(DX, DY);
       .print("[NAV] Step ", N, ": Assembler no meeting point para task ", TaskName);
       action("skip").

// --- Destino generico alcancado ---

+step(N)
    : has_destination(DX, DY) & my_pos(MX, MY) & DX == MX & DY == MY
    <- -has_destination(DX, DY);
       .print("[NAV] Step ", N, ": Cheguei ao destino (", DX, ",", DY, "). Explorando...");
       .concat("{\"x\":", DX, ",\"y\":", DY, "}", DJ);
       !dash_log("arrived_dest", DJ);
       !do_explore(MX, MY).

// --- Detach forçado quando stuck por 10+ steps ---

+step(N)
    : need_detach(DDir) & solo_mode(TaskName) & pending_submit(TaskName) & my_pos(MX, MY)
    <- -need_detach(DDir);
       .print("[NAV] Step ", N, ": STUCK com bloco para submit! Tentando goal zone alternativa");
       get_alternative_goal_zone(MX, MY, MX, MY, AGX, AGY);
       if (AGX \== -1) {
           .abolish(has_destination(_, _));
           +has_destination(AGX, AGY);
           .print("[NAV] Rota alternativa para (", AGX, ",", AGY, ")")
       } else {
           get_nearest_goal_zone(MX, MY, GX, GY);
           if (GX \== -1) {
               .abolish(has_destination(_, _));
               +has_destination(GX, GY)
           }
       };
       .random(R);
       if (R < 0.25) { Dir = n }
       elif (R < 0.5) { Dir = e }
       elif (R < 0.75) { Dir = s }
       else { Dir = w };
       .concat("move(", Dir, ")", Act);
       action(Act).

+step(N)
    : need_detach(DDir) & solo_mode(TaskName)
    <- -need_detach(DDir);
       .print("[NAV] Step ", N, ": STUCK durante coleta solo. Detach(", DDir, ")");
       .concat("detach(", DDir, ")", Act);
       action(Act).

+step(N)
    : need_detach(DDir)
    <- -need_detach(DDir);
       .print("[NAV] Step ", N, ": STUCK! Detach(", DDir, ") para destravar");
       .concat("detach(", DDir, ")", Act);
       action(Act).

// --- Desvio de obstaculo: direcao aleatoria (4 direcoes iguais) ---

+step(N)
    : has_destination(DX, DY) & my_pos(MX, MY) & last_move_blocked
    <- -last_move_blocked;
       if (last_attempted_dir(PrevDir)) {
           if (PrevDir == n) { Dir = e }
           elif (PrevDir == e) { Dir = s }
           elif (PrevDir == s) { Dir = w }
           else { Dir = n }
       } else {
           Dir = e
       };
       .abolish(last_attempted_dir(_));
       +last_attempted_dir(Dir);
       .concat("move(", Dir, ")", Act);
       action(Act).

// --- Navegar ao destino (greedy inline) ---

+step(N)
    : has_destination(DX, DY) & my_pos(MX, MY) & pending_submit(TN)
    <- compute_next_move(MX, MY, DX, DY, Dir);
       .abolish(last_attempted_dir(_));
       +last_attempted_dir(Dir);
       .concat("move(", Dir, ")", Act);
       action(Act);
       if ((N mod 20) == 0) {
           .print("[NAV] Step ", N, ": nav to goal zone (", DX, ",", DY, ") for submit ", TN, " from (", MX, ",", MY, ")")
       }.

+step(N)
    : has_destination(DX, DY) & my_pos(MX, MY)
    <- compute_next_move(MX, MY, DX, DY, Dir);
       .abolish(last_attempted_dir(_));
       +last_attempted_dir(Dir);
       .concat("move(", Dir, ")", Act);
       action(Act).

// --- Exploracao (sem destino) ---

+step(N)
    : my_pos(MX, MY) & not my_active_task(_, _)
    <- if ((N mod 40) == 0) {
           get_map_stats(V, D, G, R);
           .print("[NAV] Step ", N, " Pos(", MX, ",", MY, ") Map: vis=", V, " disp=", D, " goal=", G, " role=", R)
       };
       !do_explore(MX, MY).

+step(N)
    : my_pos(MX, MY)
    <- !do_explore(MX, MY).

+step(N)
    <- .print("[NAV] Step ", N, ": Sem posicao, skip");
       action("skip").

// --- Exploracao: buscar fronteira e mover ---

+!do_explore(MX, MY)
    <- get_nearest_frontier(MX, MY, FX, FY);
       if (FX == MX & FY == MY) {
           if (last_attempted_dir(PrevDir)) {
               if (PrevDir == n) { Dir = e }
               elif (PrevDir == e) { Dir = s }
               elif (PrevDir == s) { Dir = w }
               else { Dir = n }
           } else { Dir = n };
           .abolish(last_attempted_dir(_));
           +last_attempted_dir(Dir);
           .concat("move(", Dir, ")", Act);
           action(Act)
       } else {
           +has_destination(FX, FY);
           compute_next_move(MX, MY, FX, FY, Dir);
           .abolish(last_attempted_dir(_));
           +last_attempted_dir(Dir);
           .concat("move(", Dir, ")", Act);
           action(Act)
       }.

-!do_explore(_, _)
    <- action("move(n)").
