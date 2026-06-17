// ============================================================
// perception.asl — Processamento generico de percepts
// ============================================================

// --- Grid dimensions (toroidal wrapping) ---

// U4: dimensões vêm da fonte única hive.GridConfig (não mais hardcoded).
+!try_set_grid_dims <- apply_grid_config.

// --- Posicao: regra que consulta position diretamente da BB ---
my_pos(X, Y) :- position(X, Y).

+position(X, Y)
    <- .abolish(escape_pending(_, _));
       mark_visited(X, Y);
       .my_name(Me);
       !try_update_pos(Me, X, Y);
       !dash_step_safe;
       !check_stuck(X, Y);
       !check_osc(X, Y);
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

// --- Deteccao de oscilacao A<->B (passo 2 / #4) — SO-LOG (nao muda comportamento) ---
// "Ping-pong": voltar a celula de 2 steps atras tendo se movido, com destino ativo.
// E o ponto cego do check_stuck (que so ve mesma-celula por >=50 steps). Cada disparo
// conta uma oscilacao (mapeia a metrica "~180"). Quando isto for AGIR (replanejar/
// abandonar via #3), exigir padrao SUSTENTADO p/ nao acusar contorno legitimo.

+!check_osc(X, Y)
    : osc_p2(X2, Y2) & X == X2 & Y == Y2
      & osc_p1(X1, Y1) & (X \== X1 | Y \== Y1)
      & has_destination(DX, DY) & step(N)
    <- .print("[OSC] ping-pong (", X, ",", Y, ")<->(", X1, ",", Y1, ") rumo a (", DX, ",", DY, ") step ", N);
       .abolish(escape_pending(_, _));
       +escape_pending(X, Y);
       !osc_shift(X, Y).
+!check_osc(X, Y) <- !osc_shift(X, Y).
-!check_osc(_, _) <- true.

+!osc_shift(X, Y)
    <- if (osc_p1(PX, PY)) { .abolish(osc_p2(_, _)); +osc_p2(PX, PY) };
       .abolish(osc_p1(_, _)); +osc_p1(X, Y).
-!osc_shift(_, _) <- true.

+!try_update_pos(Me, X, Y)
    <- update_agent_pos(Me, X, Y);
       if (step(S)) { update_occupancy(Me, X, Y, S) }
       else { update_occupancy(Me, X, Y, 0) }.
-!try_update_pos(_, _, _) <- true.

// --- Things ---

+thing(X, Y, Type, Details)
    : my_pos(MX, MY) & Type == dispenser
    <- update_cell(MX + X, MY + Y, Type, Details);
       !dash_map_dispenser(MX + X, MY + Y, Details).

+thing(X, Y, Type, Details)
    : my_pos(MX, MY)
    <- update_cell(MX + X, MY + Y, Type, Details).

// --- Zonas ---

+goalZone(X, Y)
    : my_pos(MX, MY)
    <- update_cell(MX + X, MY + Y, "goal_zone", "");
       !dash_map_goal_zone(MX + X, MY + Y).

+roleZone(X, Y)
    : my_pos(MX, MY)
    <- update_cell(MX + X, MY + Y, "role_zone", "").

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
       // #1+B5 (passo 1): bloqueio por colega/oponente eh transitorio; marcar a celula
       // dele criava obstaculo-fantasma de ~30 steps no mapa compartilhado. So marca
       // obstaculo quando a celula-alvo NAO tem entity percebido (parede/bloco real).
       if (not thing(DX, DY, entity, _)) {
           mark_obstacle(MX + DX, MY + DY, N)
       };
       if (attached(AX, AY)) {
           ABX = AX + DX; ABY = AY + DY;
           if (not thing(ABX, ABY, entity, _)) {
               mark_obstacle(MX + ABX, MY + ABY, N)
           }
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
