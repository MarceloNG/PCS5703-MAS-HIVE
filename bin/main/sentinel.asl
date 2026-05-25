{ include("common/perception.asl") }
{ include("common/dashboard_hooks.asl") }
{ include("common/connect_protocol.asl") }
{ include("common/collection.asl") }
{ include("common/navigation.asl") }

my_role_type(sentinel).

!start.

+!start
    <- .my_name(Me);
       .print("[SENTINEL] ", Me, " iniciado.");
       !setup_shared_map;
       !setup_task_board;
       !setup_squad_coordinator;
       !setup_hive_dashboard;
       makeArtifact(Me, "connection.EISAccess", ["eismassimconfig.json", Me], EisId);
       focus(EisId);
       .print("[SENTINEL] Conectado. Modo: hibrido (soloist + patrulha).").

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

+name(N)  <- .print("[SENTINEL] SIM-START: nome = ", N).
+team(T)  <- .print("[SENTINEL] SIM-START: time = ", T).
+steps(S) <- .print("[SENTINEL] SIM-START: steps = ", S).

// --- SOLOIST TASK: recebida do leader via pool ---

+soloist_task(TaskName, BlockType)[source(S)]
    : not my_active_task(_, _) & step(CurStep)
    <- .print("[SENTINEL] Soloist task ", TaskName, ": coletar ", BlockType, " e submeter");
       .my_name(Me);
       mark_busy(Me);
       .abolish(collecting(_, _, _));
       .abolish(has_destination(_, _));
       .abolish(waiting_request(_, _));
       .abolish(waiting_attach_result(_, _));
       .abolish(collected_block(_));
       .abolish(solo_mode(_));
       .abolish(request_retries(_, _));
       .abolish(task_accepted_step(_, _));
       +my_active_task(TaskName, "solo");
       +solo_mode(TaskName);
       +task_accepted_step(TaskName, CurStep);
       !collect_block(BlockType).

+soloist_task(TaskName, BlockType)[source(S)]
    <- .print("[SENTINEL] Rejeitando soloist_task ", TaskName, " (ocupado)").

// --- SOLO: bloco coletado → ir a goal zone para submit ---

+collected_block(Type)
    : solo_mode(TaskName) & my_role_type(sentinel) & my_pos(MX, MY)
    <- .print("[SENTINEL] Bloco ", Type, " coletado para submit ", TaskName);
       !dash_task_phase(TaskName, "submit_nav", 50);
       +pending_submit(TaskName);
       get_nearest_goal_zone(MX, MY, GX, GY);
       if (GX \== -1) {
           .abolish(has_destination(_, _));
           +has_destination(GX, GY);
           .print("[SENTINEL] Nav goal zone (", GX, ",", GY, ") para submit ", TaskName)
       } else {
           .print("[SENTINEL] Nenhuma goal zone conhecida. pending_submit ativo.")
       }.

// --- Finalizar task e liberar no pool ---

+!finalize_task(TaskName)
    <- .my_name(Me);
       mark_free(Me);
       .abolish(my_active_task(_, _));
       .abolish(pending_submit(_));
       .abolish(submitted_task(_));
       .abolish(submit_rotate_count(_, _));
       .abolish(task_accepted_step(_, _));
       .abolish(solo_mode(_));
       .abolish(collecting(_, _, _));
       .abolish(has_destination(_, _));
       .abolish(nav_block_count(_));
       .concat("{\"task\":\"", TaskName, "\"}", FJson);
       !dash_log("task_finalized", FJson);
       !dash_task_phase(TaskName, "done", 100);
       .print("[SENTINEL] Task ", TaskName, " finalizada. Voltando a patrulha.").

-!finalize_task(_) <- true.

// --- Limpar task expirada ---

+!check_expired_task
    : my_active_task(TaskName, _) & step(N) & known_task(TaskName, Deadline, _, _) & N >= Deadline
    <- .print("[SENTINEL] Task ", TaskName, " expirou! Limpando...");
       !finalize_task(TaskName).

+!check_expired_task
    : my_active_task(TaskName, _) & step(N)
      & task_accepted_step(TaskName, AccStep) & (N - AccStep > 200)
    <- .print("[SENTINEL] Task ", TaskName, " timeout (", N - AccStep, " steps). Limpando...");
       !finalize_task(TaskName).

+!check_expired_task <- true.
-!check_expired_task <- true.
