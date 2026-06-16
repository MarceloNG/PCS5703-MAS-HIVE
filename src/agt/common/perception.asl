// ============================================================
// perception.asl — Processamento generico de percepts
// ============================================================

// --- Grid dimensions (toroidal wrapping) ---

+!try_set_grid_dims <- set_grid_dimensions(40, 40).

// --- Posicao: regra que consulta position diretamente da BB ---
my_pos(X, Y) :- position(X, Y).

+position(X, Y)
    <- !ingest_perception(X, Y);
       .my_name(Me);
       !try_update_pos(Me, X, Y);
       !dash_step_safe;
       !check_stuck(X, Y);
       !periodic_cleanup.

+!check_stuck(X, Y)
    : stuck_since(SX, SY, SStep) & step(N) & SX == X & SY == Y
      & (N - SStep >= 50) & (pending_submit(_) | solo_mode(_))
      & attached(AX, AY)
    <- .abolish(stuck_since(_, _, _));
       +stuck_since(X, Y, N);
       if (AY == -1) { DDir = n }
       elif (AY == 1) { DDir = s }
       elif (AX == 1) { DDir = e }
       else { DDir = w };
       .print("[STUCK] Marcando detach necessario dir=", DDir);
       +need_detach(DDir).

+!check_stuck(X, Y)
    : stuck_since(SX, SY, _) & (SX \== X | SY \== Y) & step(N)
    <- .abolish(stuck_since(_, _, _));
       +stuck_since(X, Y, N).

+!check_stuck(X, Y)
    : not stuck_since(_, _, _) & step(N)
    <- +stuck_since(X, Y, N).

+!check_stuck(_, _) <- true.
-!check_stuck(_, _) <- true.

+!try_update_pos(Me, X, Y)
    <- update_agent_pos(Me, X, Y).
-!try_update_pos(_, _, _) <- true.

// --- Ingestao da visao em lote (1 op/step no SharedMap) ---
// Substitui o disparo de update_cell por celula percebida. Coleta a visao
// inteira via findall e envia numa unica operacao ingest_view, derrubando a
// contencao serializada. O reporte ao dashboard (poucos dispensers/goal zones
// visiveis) e feito aqui para preservar o comportamento atual.

+!ingest_perception(MX, MY)
    <- .findall([RX, RY, T, D], thing(RX, RY, T, D), Things);
       .findall([RX, RY], goalZone(RX, RY), Goals);
       .findall([RX, RY], roleZone(RX, RY), Roles);
       ingest_view(MX, MY, Things, Goals, Roles);
       for ( thing(DX, DY, dispenser, Det) ) { !dash_map_dispenser(MX + DX, MY + DY, Det) };
       for ( goalZone(GX, GY) ) { !dash_map_goal_zone(MX + GX, MY + GY) }.
-!ingest_perception(_, _) <- true.

// --- Tasks ---

+task(Name, Deadline, Reward, Reqs)
    <- .length(Reqs, NBlocks);
       -known_task(Name, _, _, _);
       +known_task(Name, Deadline, Reward, NBlocks);
       .abolish(task_req(Name, _, _, _));
       !try_register_task(Name, Deadline, Reward, NBlocks);
       for (.member(req(RX, RY, RT), Reqs)) {
           +task_req(Name, RX, RY, RT);
           register_task_block(Name, RT)
       };
       signal_task_ready(Name).

+!try_register_task(Name, Deadline, Reward, NBlocks)
    <- register_task(Name, Deadline, Reward, NBlocks).
-!try_register_task(_, _, _, _) <- true.

// --- Normas ---

+norm(Id, Start, End, Reqs, Fine)
    <- +active_norm(Id, Start, End, Reqs, Fine);
       !check_carry_norm(Reqs).

-norm(Id, _, _, _, _)
    <- .abolish(active_norm(Id, _, _, _, _));
       .abolish(carry_limit(_)).

+!check_carry_norm(Reqs)
    <- for (.member(requirement(block, _, Qty, _), Reqs)) {
           if (not carry_limit(Qty)) {
               .abolish(carry_limit(_));
               +carry_limit(Qty)
           }
       }.
-!check_carry_norm(_) <- true.

norm_allows_carry :- not carry_limit(_).
norm_allows_carry :- carry_limit(Limit) & .count(attached(_, _), N) & N < Limit.

// --- Score ---

+score(S)
    <- -my_score(_); +my_score(S);
       !dash_score(S).

// --- Energia ---

+energy(E)
    <- -my_energy(_); +my_energy(E);
       if (E < 10) {
           .concat("{\"energy\":", E, "}", EJ);
           !dash_log("low_energy", EJ)
       }.

// --- Desativacao ---

+deactivated(true)
    <- -am_active; +am_deactivated;
       .print("*** DESATIVADO! Aguardando reativacao ***");
       !dash_log("deactivated", "{}").

+deactivated(false)
    : am_deactivated
    <- -am_deactivated; +am_active;
       .print("*** REATIVADO! Voltando ao normal ***");
       !dash_log("reactivated", "{}").

// --- Role ---

+role(R)
    <- -my_role(_); +my_role(R).

// --- Resultado de acao (tracking) ---

+lastActionResult(failed_path)
    : my_pos(MX, MY) & last_attempted_dir(Dir) & step(N)
    <- +last_move_blocked;
       -last_attempted_dir(Dir);
       if (Dir == n) { DX = 0; DY = -1 }
       elif (Dir == s) { DX = 0; DY = 1 }
       elif (Dir == e) { DX = 1; DY = 0 }
       else { DX = -1; DY = 0 };
       mark_obstacle(MX + DX, MY + DY, N);
       if (attached(AX, AY)) {
           mark_obstacle(MX + AX + DX, MY + AY + DY, N)
       }.

+lastActionResult(failed_path)
    <- +last_move_blocked.

+lastActionResult(failed)
    : lastAction(move) & last_attempted_dir(_)
    <- +last_move_blocked.

+lastActionResult(success)
    <- -last_move_blocked;
       -last_attempted_dir(_).

// --- Blocos attached ---

+attached(X, Y)
    <- -my_attached(X, Y); +my_attached(X, Y).

-attached(X, Y)
    <- -my_attached(X, Y).

carrying_blocks(N) :- .count(my_attached(_, _), N).
has_block :- my_attached(_, _).

+!periodic_cleanup : step(N) & (N mod 50) == 0
    <- !check_expired_task;
       decay_obstacles(N);
       remove_expired(N).
+!periodic_cleanup : step(N) & (N mod 10) == 0
    <- !check_expired_task;
       decay_obstacles(N).
+!periodic_cleanup
    <- !check_expired_task.
-!periodic_cleanup <- true.
