// ============================================================
// perception.asl — Processamento generico de percepts
// ============================================================

// --- Posicao: regra que consulta position diretamente da BB ---
my_pos(X, Y) :- position(X, Y).

+position(X, Y)
    <- mark_visited(X, Y);
       .my_name(Me);
       !try_update_pos(Me, X, Y);
       !dash_step_safe;
       !check_stuck(X, Y);
       !periodic_cleanup.

+!check_stuck(X, Y)
    : stuck_since(SX, SY, SStep) & step(N) & SX == X & SY == Y
      & (N - SStep >= 20) & (pending_submit(_) | solo_mode(_))
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

// --- Things ---

+thing(X, Y, Type, Details)
    : my_pos(MX, MY)
    <- update_cell(MX + X, MY + Y, Type, Details).

+thing(X, Y, marker, "ci")
    <- .print("!!! CLEAR IMINENTE em (", X, ",", Y, ")! Evacuar!");
       .concat("{\"x\":", X, ",\"y\":", Y, "}", CJ);
       !dash_log("clear_warning", CJ).

// --- Zonas ---

+goalZone(X, Y)
    : my_pos(MX, MY)
    <- update_cell(MX + X, MY + Y, "goal_zone", "").

+roleZone(X, Y)
    : my_pos(MX, MY)
    <- update_cell(MX + X, MY + Y, "role_zone", "").

// --- Tasks ---

+task(Name, Deadline, Reward, Reqs)
    <- .length(Reqs, NBlocks);
       -known_task(Name, _, _, _);
       +known_task(Name, Deadline, Reward, NBlocks);
       .abolish(task_req(Name, _, _, _));
       for (.member(req(RX, RY, RT), Reqs)) {
           +task_req(Name, RX, RY, RT);
           register_task_block(Name, RT)
       };
       !try_register_task(Name, Deadline, Reward, NBlocks).

+!try_register_task(Name, Deadline, Reward, NBlocks)
    <- register_task(Name, Deadline, Reward, NBlocks).
-!try_register_task(_, _, _, _) <- true.

// --- Normas ---

+norm(Id, Start, End, Reqs, Fine)
    <- +active_norm(Id, Start, End, Reqs, Fine).

-norm(Id, _, _, _, _)
    <- .abolish(active_norm(Id, _, _, _, _)).

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
       if (Dir == n) { OY = MY - 1; mark_obstacle(MX, OY, N) }
       elif (Dir == s) { OY = MY + 1; mark_obstacle(MX, OY, N) }
       elif (Dir == e) { OX = MX + 1; mark_obstacle(OX, MY, N) }
       elif (Dir == w) { OX = MX - 1; mark_obstacle(OX, MY, N) }.

+lastActionResult(failed_path)
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

+!periodic_cleanup : step(N)
    <- !check_expired_task;
       decay_obstacles(N).
+!periodic_cleanup
    <- !check_expired_task.
-!periodic_cleanup <- true.
