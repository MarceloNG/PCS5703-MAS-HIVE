{ include("common/perception.asl") }
{ include("common/shared_map_init.asl") }
{ include("$jacamo/templates/common-cartago.asl") }
{ include("$jacamo/templates/common-moise.asl") }
{ include("common/organization.asl") }
{ include("common/dashboard_hooks.asl") }
{ include("common/communication.asl") }
{ include("common/connect_protocol.asl") }
{ include("common/role_adoption.asl") }
{ include("common/collection.asl") }
{ include("common/navigation.asl") }

// Tipo ÚNICO do time flat (issue #38): todos os agentes são hive_agent. A diferenciação
// funcional (coletar/montar/explorar) é por missão/crença dinâmica, não por tipo fixo.
// Base: o antigo collector (agente solo provado em #26/#06c). A orquestração multi-bloco
// (montagem central via líder) foi aposentada — volta como Contract-Net descentralizado (#22).
my_role_type(hive_agent).

!start.

+!start
    <- .my_name(Me);
       .print("[AGENT] ", Me, " iniciado.");
       !setup_shared_map;
       !try_set_grid_dims;
       !setup_task_board;
       !setup_squad_coordinator;
       !setup_hive_dashboard;
       makeArtifact(Me, "connection.EISAccess", ["eismassimconfig.json", Me], EisId);
       focus(EisId);
       .print("[AGENT] Conectado. Modo: exploracao + coleta.").

+!setup_task_board
    <- lookupArtifact("task_board", TbId); focus(TbId).
-!setup_task_board
    <- .wait(50); !try_create_task_board.
+!try_create_task_board
    <- makeArtifact("task_board", "env.TaskBoard", [], TbId); focus(TbId).
-!try_create_task_board
    <- .wait(100); !setup_task_board.

+!setup_squad_coordinator
    <- lookupArtifact("squad_coordinator", ScId); focus(ScId).
-!setup_squad_coordinator
    <- .wait(50); !try_create_squad_coordinator.
+!try_create_squad_coordinator
    <- makeArtifact("squad_coordinator", "env.SquadCoordinator", [], ScId); focus(ScId).
-!try_create_squad_coordinator
    <- .wait(100); !setup_squad_coordinator.

+name(N)  <- .print("[AGENT] SIM-START: nome = ", N).
+team(T)  <- -my_team(_); +my_team(T); .print("[AGENT] SIM-START: time = ", T).
+steps(S) <- .print("[AGENT] SIM-START: steps = ", S).

// --- Reagir a ordem de coleta do leader (via mensagem direta) ---

+do_collect(BlockType)[source(S)]
    <- .print("[AGENT] Recebi ordem de ", S, ": coletar ", BlockType);
       .concat("{\"block\":\"", BlockType, "\"}", CJson);
       !dash_log("collect_started", CJson);
       .abolish(collecting(_, _, _));
       .abolish(has_destination(_, _));
       .abolish(waiting_request(_, _));
       .abolish(waiting_attach_result(_, _));
       .abolish(collected_block(_));
       +assigned_task_block(BlockType);
       !collect_block(BlockType).

// --- Fallback: coleta oportunista desabilitada durante task ativa ---

+new_dispenser(X, Y, Type)
    : not collecting(_, _, _) & not navigating_to_meeting_point(_)
      & not assigned_task_block(_) & not collected_block(_)
      & not solo_mode(_) & not my_active_task(_, _)
      & not dispersal_step(_)
    <- .my_name(Me);
       get_my_assignment(Me, Assignment);
       if (Assignment == "none") {
           get_my_squad(Me, MySquad);
           get_squad_task(MySquad, STask);
           if (STask == "none") {
               .print("[AGENT] Dispenser ", Type, " em (", X, ",", Y, ")! (coleta oportunista)");
               !collect_block(Type)
           }
       }.

// --- Apos coletar (multi-block ou oportunista, NAO solo) ---

+collected_block(Type)
    : not solo_mode(_)
    <- .my_name(Me);
       .concat("{\"block\":\"", Type, "\"}", BCJson);
       !dash_log("block_collected", BCJson);
       get_my_squad(Me, MySquad);
       if (MySquad \== "none") {
           get_meeting_point(MySquad, MPX, MPY);
           if (MPX \== -1) {
               .print("[AGENT] Bloco ", Type, " coletado! Indo ao meeting point (", MPX, ",", MPY, ")");
               .abolish(has_destination(_, _));
               +has_destination(MPX, MPY);
               +navigating_to_meeting_point(MySquad)
           } else {
               .print("[AGENT] Bloco coletado mas sem meeting point definido")
           }
       }.

// --- SOLOIST TASK: recebida do leader via pool ---

+soloist_task(TaskName, BlockType, Deadline)[source(S)]
    : not my_active_task(_, _) & step(CurStep)
    <- .print("[AGENT] Soloist task ", TaskName, ": coletar ", BlockType, " deadline=", Deadline);
       .my_name(Me);
       .abolish(collecting(_, _, _));
       .abolish(has_destination(_, _));
       .abolish(waiting_request(_, _));
       .abolish(waiting_attach_result(_, _));
       .abolish(collected_block(_));
       .abolish(solo_mode(_));
       .abolish(solo_block_type(_));
       .abolish(request_retries(_, _));
       .abolish(task_accepted_step(_, _));
       .abolish(navigating_to_meeting_point(_));
       .abolish(assigned_task_block(_));
       .abolish(my_task_deadline(_, _));
       .abolish(searching_dispenser(_));
       .abolish(needs_clear_blocks(_));
       -norm_detach_blocked;
       .abolish(norm_detach_fails(_));
       +my_task_deadline(TaskName, Deadline);
       +my_active_task(TaskName, "solo");
       +solo_mode(TaskName);
       +solo_block_type(BlockType);
       +task_accepted_step(TaskName, CurStep);
       if (attached(DX, DY) & task_req(TaskName, DX, DY, BlockType)) {
           .print("[AGENT] Bloco ", BlockType, " já na mão p/ ", TaskName, " → submit (sem coletar/descartar).");
           +collected_block(BlockType)
       } elif (attached(_, _)) {
           +needs_clear_blocks(BlockType)
       } else {
           !collect_block(BlockType)
       }.

+soloist_task(TaskName, BlockType, Deadline)[source(S)]
    <- .print("[AGENT] Rejeitando soloist_task ", TaskName, " (task ativa)").

// --- SOLO: bloco coletado → buscar goal zone (nav se perto, senao explorar) ---

+collected_block(Type)
    : solo_mode(TaskName) & my_role_type(hive_agent) & my_pos(MX, MY)
    <- .print("[AGENT] Bloco ", Type, " coletado p/ ", TaskName, " — pre-alinhamento no dispenser (U3).");
       !dash_task_phase(TaskName, "prealign", 40).

// Inicia a navegação à goal zone p/ submit — pós pré-alinhamento no dispenser (ou bloco já
// alinhado). Disparado pelos handlers de PRÉ-ALINHAMENTO em connect_protocol.asl (U3).
+!start_submit_nav(TaskName)
    : my_pos(MX, MY)
    <- +pending_submit(TaskName);
       !dash_task_phase(TaskName, "submit_nav", 50);
       get_nearest_goal_zone(MX, MY, GX, GY);
       if (GX \== -1) {
           .abolish(has_destination(_, _));
           +has_destination(GX, GY);
           .print("[AGENT] Nav goal zone (", GX, ",", GY, ") para submit ", TaskName)
       } else {
           .print("[AGENT] Nenhuma goal zone conhecida. pending_submit ativo.")
       }.
+!start_submit_nav(_) <- true.

// --- Finalizar soloist task e liberar no pool ---

+!finalize_task(TaskName)
    <- .my_name(Me);
       .abolish(dispersal_step(_));
       mark_free(Me);
       complete_task(TaskName);
       release_agent_from_task(TaskName, Me);
       .abolish(my_active_task(_, _));
       .abolish(pending_submit(_));
       .abolish(submitted_task(_));
       .abolish(task_accepted_step(_, _));
       .abolish(solo_mode(_));
       .abolish(solo_block_type(_));
       .abolish(my_task_deadline(_, _));
       .abolish(collecting(_, _, _));
       .abolish(has_destination(_, _));
       .abolish(assigned_task_block(_));
       .abolish(navigating_to_meeting_point(_));
       .abolish(pending_connect(_, _, _, _));
       .abolish(pending_connect_backup(_, _, _, _));
       .abolish(waiting_connect_collector(_));
       .abolish(nav_block_count(_));
       .abolish(searching_dispenser(_));
       .abolish(needs_clear_blocks(_));
       .abolish(trying_rotate(_, _, _));
       .abolish(rotate_pre_submit_fails(_, _));
       .abolish(prealign_fails(_, _));
       .abolish(detach_stuck_fails(_, _));
       .abolish(norm_detach_fails(_));
       -norm_detach_blocked;
       .concat("{\"task\":\"", TaskName, "\"}", FJson);
       !dash_log("task_finalized", FJson);
       !dash_task_phase(TaskName, "done", 100);
       .print("[AGENT] Task ", TaskName, " finalizada.").

-!finalize_task(_) <- true.

// --- Limpar task expirada ---

+!check_expired_task
    : my_active_task(TaskName, _) & step(N) & known_task(TaskName, Deadline, _, _) & N >= Deadline
    <- .print("[AGENT] Task ", TaskName, " expirou! Limpando...");
       !finalize_task(TaskName).

+!check_expired_task
    : my_active_task(TaskName, _) & step(N)
      & task_accepted_step(TaskName, AccStep) & (N - AccStep > 300)
    <- .print("[AGENT] Task ", TaskName, " timeout (", N - AccStep, " steps). Limpando...");
       !finalize_task(TaskName).

+!check_expired_task <- true.
-!check_expired_task <- true.
