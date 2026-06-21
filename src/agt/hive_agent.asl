{ include("common/perception.asl") }
{ include("common/shared_map_init.asl") }
{ include("$jacamo/templates/common-cartago.asl") }
{ include("$jacamo/templates/common-moise.asl") }
{ include("common/organization.asl") }
{ include("common/dashboard_hooks.asl") }
{ include("common/communication.asl") }
{ include("common/survival.asl") }
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
       hive.AgentNr(Nr); +my_agent_nr(Nr);
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

// === Regime squad-era REMOVIDO (#53) ===
// do_collect (ordem de leader), coleta oportunista (+new_dispenser), meeting-point
// (collected_block : not solo_mode) e soloist_task eram do regime squad aposentado no #38.
// do_collect/soloist_task nunca eram emitidos por .send (handlers órfãos). A aquisição de
// bloco agora tem trilha ÚNICA: select_task (#40, role_adoption.asl) → solo_mode → coleta.

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
       mark_free(Me);
       complete_task(TaskName);
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
       .abolish(collected_block(_));
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
