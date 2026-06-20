// ============================================================
// role_adoption.asl — Track 3 Fase C / U3
// Adoção do role MAPC `worker` — o GATE DE SCORE no cenário oficial:
// o role inicial `default` não tem request/attach/connect/submit, então sem
// adotar `worker` (numa role-zone) o time anda mas faz score 0.
//
// Adaptado de ~/repos/MAPC/src/agt/worker_role.asl (citar + melhorar):
//   - usa my_pos DEAD-RECKONED (Fase D) no lugar de position absoluto;
//   - usa a navegação do HIVE (get_nearest_role_zone [U1] + compute_next_move +
//     escape_move) no lugar de navigate_step/move_relative (que não existem aqui);
//   - integra como +step(N) com prioridade por ORDEM DE INCLUDE (este módulo entra
//     ANTES de collection.asl): enquanto my_role(default) a adoção tem prioridade e
//     GATEIA a coleta; quando my_role(worker) todos os contextos abaixo falham e o
//     step cai normalmente em connect_protocol/collection/navigation.
//
// GOTCHA Nº 1 (corrigido aqui): a adoção é um plano +step(N), produzindo UMA ÚNICA
// ação MASSim por step (adopt OU move OU explore OU skip). NÃO usar handler reativo
// +role(default) — o próprio ref avisa que ele compete com o step e pode submeter
// DUAS ações no mesmo step.
//
// Incluído pelo tipo único hive_agent (time flat — issue #38). Todos os agentes adotam
// worker via !ensure_worker_role e auto-iniciam coleta solo via SELF-ASSIGN (plano (c)).
// ============================================================

// ------------------------------------------------------------
// Gate config-agnóstico: o role MAPC ATUAL já pode PONTUAR?
// `can_score_role` é verdadeiro quando o role atual (my_role) tem `submit` no
// catálogo de roles percebido (role/6 do InitialPercept: Nome,Vision,Actions,...).
//   - cenário OFICIAL: default = [skip,move,rotate,adopt,detach,clear] (sem submit)
//     → false → adoção dispara.
//   - config DEV (default permissivo, com submit) → true → adoção NÃO dispara
//     (sem regressão no pipeline de coleta do dev).
//   - após adotar worker (que tem submit) → true → adoção para.
// Robusto a não ter o catálogo ainda (início): false → explora até o catálogo chegar.
// ------------------------------------------------------------

// Gate pela CRENÇA DE PERCEPT CRUA `role/1` (o role atual que o servidor envia todo step),
// NÃO pela `my_role` derivada. O adopt-spam (workers re-adotando 200+× sem nunca coletar)
// mostrou que a derivação `+role(R)→my_role` não refletia o worker de forma confiável no gate.
// worker/constructor SEMPRE pontuam; a 3ª cláusula cobre o DEV (default permissivo) cruzando
// o role atual (role/1) com o catálogo (role/6) para ver se tem submit.
// `explorer` é estado intermediário no path default→explorer→worker: NÃO pontua (sem submit)
// mas está em trânsito → !ensure_worker_role continua gerenciando a transição step-a-step.
can_score_role :- role(worker).
can_score_role :- role(constructor).
can_score_role :- role(Cur) & role(Cur, _, Acts, _, _, _) & .member(submit, Acts).

// ------------------------------------------------------------
// Planos +step(N) (gate de prioridade: só agem enquanto o role atual NÃO pontua)
// ------------------------------------------------------------

// (a) Bloqueado a caminho da role-zone → escape reativo (mesma camada #3/#4 da
//     navigation.asl). Vem ANTES do trigger geral p/ não re-emitir o move travado.
+step(N)
    : not can_score_role & my_pos(MX, MY)
      & has_destination(DX, DY) & (last_move_blocked | escape_pending(_, _))
    <- .abolish(last_move_blocked);
       .abolish(escape_pending(_, _));
       // travou indo p/ a role-zone: DISPERSAR por ~15 steps em vez de re-mirar a mesma
       // — quebra a oscilação escape→re-beeline→bloqueio que prende o agente na área
       // (e descongestiona a vizinhança das role-zones).
       .abolish(rz_disperse_until(_));
       +rz_disperse_until(N + 15);
       !escape_move(MX, MY, DX, DY).

// (b) Trigger geral: enquanto o role atual não pontua, perseguir o worker.
//     TODO U4 (elo MOISE+): trocar este gatilho INCONDICIONAL pelo dirigido por
//     obrigação — a org emite adopt_worker_role, o handler chama !ensure_worker_role
//     e descarrega via goalAchieved. Por ora (boot intermediário de U3, sem org) é
//     incondicional para validar a mecânica de adoção + score>0.
+step(N)
    : not can_score_role
    <- !ensure_worker_role.

// (c) Worker idle — selecionar task por valor + reservar footprint na goal-zone (#40 allocator).
// Substitui claim_task (lock exclusivo de TASK) pela reserva atômica de ESPAÇO FÍSICO na
// GOAL-ZONE: tasks MASSim são não-exclusivas (multi-submit, perduram até deadline). O recurso
// disputado é a célula de entrega, não a task em si. Fluxo:
//   1. Obter goal-zone mais próxima (footprint alvo — R2).
//   2. Obter dispenser do bloco necessário; explorar se desconhecido.
//   3. select_task (TaskBoard, atômico via CArtAgO): tenta reservar a célula da goal-zone.
//      · Won=true → prossegue à coleta solo (R3/R5).
//      · Won=false → DISPERSA em vez de empilhar na goal-zone (R4).
// O bid (Reward) é proporcional à task; calibração de pesos por evidência → follow-up #45.
+step(N)
    : can_score_role & N > 30
      & not my_active_task(_, _) & not collecting(_, _, _)
      & not solo_mode(_) & not searching_dispenser(_)
      & my_pos(MX, MY)
      & known_task(TaskName, Deadline, Reward, 1)
      & Deadline > N + 20
      & task_req(TaskName, _, _, BlockType)
    <- .my_name(Me);
       // R2: goal-zone mais próxima é o footprint a reservar
       get_nearest_goal_zone(MX, MY, GZX, GZY);
       if (GZX == -1) {
           // Nenhuma goal-zone conhecida — explorar primeiro
           .print("[ROLE] Step ", N, ": sem goal-zone conhecida — explorando.");
           !do_explore(MX, MY)
       } else {
           get_nearest_dispenser(MX, MY, BlockType, DX, DY);
           if (DX == -1) {
               // Dispenser não descoberto ainda — explorar para encontrá-lo
               .print("[ROLE] Step ", N, ": sem dispenser ", BlockType, " — explorando.");
               +searching_dispenser(BlockType);
               !do_explore(MX, MY)
           } else {
               // R2/R3: reserva atômica de footprint (goal-zone cell) via TaskBoard
               select_task(Me, TaskName, Reward, GZX, GZY, Won);
               if (Won) {
                   // Ganhou: procede à coleta solo (R5)
                   .print("[ROLE] Step ", N, ": allocated task=", TaskName, " gz=(", GZX, ",", GZY, ") — solo (#40)");
                   mark_busy(Me);
                   +my_active_task(TaskName, "solo");
                   +solo_mode(TaskName);
                   +solo_block_type(BlockType);
                   +task_accepted_step(TaskName, N);
                   +collecting(BlockType, DX, DY);
                   +has_destination(DX, DY);
                   action("skip")
               } else {
                   // R4: goal-zone reservada por outro agente — DISPERSAR (explorar) em vez de
                   // empilhar na goal-zone. (#53: o flag dispersal_step que bloqueava a coleta
                   // oportunista saiu junto do regime squad — agora só o do_explore importa.)
                   .print("[ROLE] Step ", N, ": goal-zone (", GZX, ",", GZY, ") ocupada — dispersando (#40).");
                   !do_explore(MX, MY)
               }
           }
       }.

// ------------------------------------------------------------
// Capacidade reutilizável (também acionada pelo elo MOISE+ em U4)
// !ensure_worker_role: idempotente; emite exatamente UMA ação MASSim.
// ------------------------------------------------------------

// role atual já pontua: nada a fazer (caminho do discharge idempotente em U4)
+!ensure_worker_role : can_score_role
    <- true.

// explorer sobre a role-zone → segundo passo do path default→explorer→worker
+!ensure_worker_role : role(explorer) & roleZone(0, 0)
    <- .print("[ROLE] Explorer sobre role-zone — adopt(worker).");
       .abolish(has_destination(_, _));
       action("adopt(worker)").

// explorer fora da role-zone → buscar com speed=3 (herdado de default via merge aditivo)
+!ensure_worker_role : role(explorer) & my_pos(MX, MY)
    <- !seek_role_zone(MX, MY).

// default sobre a role-zone → primeiro passo: adopt explorer (speed=3 para chegar ao worker)
+!ensure_worker_role : roleZone(0, 0)
    <- .print("[ROLE] Default sobre role-zone — adopt(explorer).");
       .abolish(has_destination(_, _));
       action("adopt(explorer)").

// tem posição → buscar a role-zone (lembrada via A*, ou explorar p/ achá-la)
+!ensure_worker_role : my_pos(MX, MY)
    <- !seek_role_zone(MX, MY).

// sem posição ainda (início da sim) → aguarda o próximo step
+!ensure_worker_role
    <- action("skip").

// defensivo: qualquer falha não prevista ainda emite 1 ação (evita timeout do step)
-!ensure_worker_role
    <- action("skip").

// ------------------------------------------------------------
// !seek_role_zone: navega à role-zone conhecida mais próxima (U1 + compute_next_move),
// ou explora se nenhuma é conhecida ainda. Espelha o idioma de !collect_block.
// ------------------------------------------------------------

// dispersando após travar numa role-zone inalcançável → EXPLORA (espalha) em vez de
// re-mirar a mesma; re-tenta a role-zone quando a janela expira (já possivelmente noutro lugar).
+!seek_role_zone(MX, MY)
    : rz_disperse_until(Until) & step(N) & N < Until
    <- !do_explore(MX, MY).

+!seek_role_zone(MX, MY)
    <- .abolish(rz_disperse_until(_));      // janela expirou (ou nunca houve) → volta a mirar role-zone
       get_nearest_role_zone(MX, MY, RX, RY);
       if (RX == -1) {
           .print("[ROLE] Nenhuma role-zone conhecida — explorando.");
           !do_explore(MX, MY)
       } elif (RX == MX & RY == MY) {
           // mapa diz role-zone aqui, mas sem percept roleZone(0,0) (drift do
           // dead-reckoning) → explorar p/ reaquisitar a posição da role-zone
           .print("[ROLE] Role-zone lembrada coincide com my_pos sem percept — reexplorando.");
           !do_explore(MX, MY)
       } else {
           .abolish(has_destination(_, _));
           +has_destination(RX, RY);
           compute_next_move(MX, MY, RX, RY, Dir);
           .abolish(last_attempted_dir(_));
           +last_attempted_dir(Dir);
           .concat("move(", Dir, ")", Act);
           action(Act)
       }.

-!seek_role_zone(_, _)
    <- action("skip").
