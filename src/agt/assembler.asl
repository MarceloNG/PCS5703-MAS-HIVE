{ include("common/perception.asl") }
{ include("common/dashboard_hooks.asl") }
{ include("common/communication.asl") }
{ include("common/connect_protocol.asl") }
{ include("common/collection.asl") }
{ include("common/navigation.asl") }

my_role_type(assembler).

!start.

+!start
    <- .my_name(Me);
       .print("[ASSEMBLER] ", Me, " iniciado.");
       !setup_shared_map;
       !try_set_grid_dims;
       !setup_task_board;
       !setup_squad_coordinator;
       !setup_hive_dashboard;
       makeArtifact(Me, "connection.EISAccess", ["eismassimconfig.json", Me], EisId);
       focus(EisId);
       .print("[ASSEMBLER] Conectado. Modo: exploracao + montagem.").

+!setup_shared_map
    <- lookupArtifact("shared_map", MapId); focus(MapId).
-!setup_shared_map
    <- .wait(50); !try_create_map.
+!try_create_map
    <- makeArtifact("shared_map", "env.SharedMap", [], MapId); focus(MapId).
-!try_create_map
    <- .wait(100); !setup_shared_map.

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

// --- Limpar task expirada ---

+!check_expired_task
    : my_active_task(TaskName, _) & step(N) & known_task(TaskName, Deadline, _, _) & N >= Deadline
    <- .print("[ASSEMBLER] Task ", TaskName, " expirou! Limpando...");
       !finalize_task(TaskName).

+!check_expired_task
    : my_active_task(TaskName, _) & step(N)
      & task_accepted_step(TaskName, AccStep) & (N - AccStep > 200)
    <- .print("[ASSEMBLER] Task ", TaskName, " timeout (", N - AccStep, " steps). Limpando...");
       !finalize_task(TaskName).

+!check_expired_task <- true.
-!check_expired_task <- true.

+name(N)  <- .print("[ASSEMBLER] SIM-START: nome = ", N).
+team(T)  <- .print("[ASSEMBLER] SIM-START: time = ", T).
+steps(S) <- .print("[ASSEMBLER] SIM-START: steps = ", S).


// --- SOLOIST TASK: via pool de soloists ---

+soloist_task(TaskName, BlockType, Deadline)[source(S)]
    : not my_active_task(_, _) & step(CurStep)
    <- .print("[ASSEMBLER] Soloist task ", TaskName, ": coletar ", BlockType, " deadline=", Deadline);
       .my_name(Me);
       .abolish(collecting(_, _, _));
       .abolish(has_destination(_, _));
       .abolish(waiting_request(_, _));
       .abolish(waiting_attach_result(_, _));
       .abolish(collected_block(_));
       .abolish(navigating_to_meeting_for_connect(_, _, _));
       .abolish(ready_to_connect(_, _, _, _));
       .abolish(waiting_connect_result(_, _));
       .abolish(pending_connect(_, _, _, _));
       .abolish(pending_connect_backup(_, _, _, _));
       .abolish(waiting_connect_collector(_));
       .abolish(solo_mode(_));
       .abolish(solo_block_type(_));
       .abolish(multi_block_mode(_, _));
       .abolish(request_retries(_, _));
       .abolish(task_accepted_step(_, _));
       .abolish(my_task_deadline(_, _));
       .abolish(searching_dispenser(_));
       .abolish(needs_clear_blocks(_));
       +my_task_deadline(TaskName, Deadline);
       +my_active_task(TaskName, "solo");
       +solo_mode(TaskName);
       +solo_block_type(BlockType);
       +task_accepted_step(TaskName, CurStep);
       if (attached(_, _)) {
           +needs_clear_blocks(BlockType)
       } else {
           !collect_block(BlockType)
       }.

+soloist_task(TaskName, BlockType, Deadline)[source(S)]
    <- .print("[ASSEMBLER] Rejeitando soloist_task ", TaskName, " (ocupado)").

// --- SOLO TASK: 1 bloco — assembler coleta e submete direto (legacy) ---

+solo_task(TaskName, SquadId, BlockType)[source(S)]
    : not my_active_task(_, _) & step(CurStep)
    <- .print("[ASSEMBLER] Solo task ", TaskName, ": coletar ", BlockType, " e submeter");
       .concat("{\"task\":\"", TaskName, "\",\"squad\":\"", SquadId, "\"}", TRJson);
       !dash_log("task_received", TRJson);
       .abolish(collecting(_, _, _));
       .abolish(has_destination(_, _));
       .abolish(waiting_request(_, _));
       .abolish(waiting_attach_result(_, _));
       .abolish(collected_block(_));
       .abolish(navigating_to_meeting_for_connect(_, _, _));
       .abolish(ready_to_connect(_, _, _, _));
       .abolish(waiting_connect_result(_, _));
       .abolish(pending_connect(_, _, _, _));
       .abolish(pending_connect_backup(_, _, _, _));
       .abolish(waiting_connect_collector(_));
       .abolish(solo_mode(_));
       .abolish(multi_block_mode(_, _));
       .abolish(request_retries(_, _));
       .abolish(task_accepted_step(_, _));
       +my_active_task(TaskName, SquadId);
       +solo_mode(TaskName);
       +task_accepted_step(TaskName, CurStep);
       !collect_block(BlockType).

+solo_task(TaskName, SquadId, BlockType)[source(S)]
    <- .print("[ASSEMBLER] Rejeitando solo_task ", TaskName, " (ocupado)").

// --- SOLO: bloco coletado → ir a goal zone para submit ---

+collected_block(Type)
    : solo_mode(TaskName) & my_role_type(assembler) & my_pos(MX, MY)
    <- .print("[ASSEMBLER] Bloco ", Type, " coletado para submit ", TaskName);
       !dash_task_phase(TaskName, "submit_nav", 50);
       +pending_submit(TaskName);
       get_nearest_goal_zone(MX, MY, GX, GY);
       if (GX \== -1) {
           .abolish(has_destination(_, _));
           +has_destination(GX, GY);
           .print("[ASSEMBLER] Nav goal zone (", GX, ",", GY, ") para submit ", TaskName)
       } else {
           .print("[ASSEMBLER] Nenhuma goal zone conhecida. pending_submit ativo.")
       }.

// --- MULTI-BLOCK TASK: assembler coleta bloco e vai ao meeting point ---

+collect_and_connect_task(TaskName, SquadId, BlockType)[source(S)]
    : not my_active_task(_, _) & step(CurStep)
    <- .print("[ASSEMBLER] Multi-block task ", TaskName, ": coletar ", BlockType, " para connect");
       .concat("{\"task\":\"", TaskName, "\",\"squad\":\"", SquadId, "\"}", TRJson);
       !dash_log("task_received", TRJson);
       .abolish(collecting(_, _, _));
       .abolish(has_destination(_, _));
       .abolish(solo_mode(_));
       .abolish(multi_block_mode(_, _));
       .abolish(request_retries(_, _));
       .abolish(task_accepted_step(_, _));
       +my_active_task(TaskName, SquadId);
       +multi_block_mode(TaskName, SquadId);
       +task_accepted_step(TaskName, CurStep);
       !collect_block(BlockType).

+collect_and_connect_task(TaskName, SquadId, BlockType)[source(S)]
    <- .print("[ASSEMBLER] Rejeitando multi-block ", TaskName, " (ocupado)").

// --- MULTI-BLOCK: bloco coletado → ir ao meeting point ---

+collected_block(Type)
    : multi_block_mode(TaskName, SquadId) & my_role_type(assembler) & my_pos(MX, MY)
    <- .print("[ASSEMBLER] Bloco ", Type, " coletado para multi-block task ", TaskName);
       get_meeting_point(SquadId, MPX, MPY);
       if (MPX \== -1) {
           .abolish(has_destination(_, _));
           +has_destination(MPX, MPY);
           +navigating_to_meeting_for_connect(SquadId, "pending", TaskName);
           .print("[ASSEMBLER] Indo ao meeting point (", MPX, ",", MPY, ")")
       }.

// --- Receber notificacao de task do leader (legacy) ---

+prepare_for_task(TaskName, SquadId)[source(S)]
    <- .print("[ASSEMBLER] Recebi task ", TaskName, " do squad ", SquadId, " via ", S);
       .concat("{\"task\":\"", TaskName, "\",\"squad\":\"", SquadId, "\"}", TRJson);
       !dash_log("task_received", TRJson);
       .abolish(my_active_task(_, _));
       +my_active_task(TaskName, SquadId).

// --- Reagir quando collector sinaliza pronto ---

+agent_ready(SquadId, AgentName)
    <- .my_name(Me);
       get_my_squad(Me, MySquad);
       if (SquadId == MySquad & my_active_task(TaskName, MySquad)) {
           all_ready(MySquad, Ready);
           if (Ready) {
               .print("[ASSEMBLER] Todos collectors prontos no squad ", MySquad, "! Verificando minha posicao...");
               get_meeting_point(MySquad, MPX, MPY);
               if (not has_destination(_, _) & not navigating_to_meeting_for_connect(_, _, _)) {
                   .abolish(has_destination(_, _));
                   +has_destination(MPX, MPY);
                   +navigating_to_meeting_for_connect(MySquad, AgentName, TaskName);
                   .print("[ASSEMBLER] Indo ao meeting point para connect")
               } else {
                   .print("[ASSEMBLER] Ja a caminho do meeting point, aguardando chegada")
               }
           } else {
               .print("[ASSEMBLER] ", AgentName, " pronto, aguardando demais...")
           }
       }.

// --- Handler de connect movido para connect_protocol.asl (prioridade) ---

// --- Finalizar task e resetar squad ---

+!finalize_task(TaskName)
    <- .my_name(Me);
       mark_free(Me);
       get_my_squad(Me, MySquad);
       complete_task(TaskName);
       release_agent_from_task(TaskName, Me);
       clear_ready(MySquad);
       .abolish(my_active_task(_, _));
       .abolish(pending_submit(_));
       .abolish(submitted_task(_));
       .abolish(submit_rotate_count(_, _));
       .abolish(submit_reposition_count(_, _));
       .abolish(need_goal_zone_for(_));
       .abolish(task_accepted_step(_, _));
       .abolish(ready_to_connect(_, _, _, _));
       .abolish(waiting_connect_result(_, _));
       .abolish(navigating_to_meeting_for_connect(_, _, _));
       .abolish(multi_block_mode(_, _));
       .abolish(solo_mode(_));
       .abolish(solo_block_type(_));
       .abolish(my_task_deadline(_, _));
       .abolish(collecting(_, _, _));
       .abolish(has_destination(_, _));
       .abolish(nav_block_count(_));
       .abolish(searching_dispenser(_));
       .abolish(needs_clear_blocks(_));
       .concat("{\"task\":\"", TaskName, "\",\"squad\":\"", MySquad, "\"}", FJson);
       !dash_log("task_finalized", FJson);
       !dash_task_phase(TaskName, "done", 100);
       .print("[ASSEMBLER] Task ", TaskName, " finalizada. Squad ", MySquad, " idle.").

-!finalize_task(_) <- true.
