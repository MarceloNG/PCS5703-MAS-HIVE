{ include("common/perception.asl") }
{ include("common/collection.asl") }
{ include("common/navigation.asl") }

!start.

+!start
    <- .my_name(Me);
       .print("Agente ", Me, " iniciado.");
       !setup_shared_map;
       makeArtifact(Me, "connection.EISAccess", ["eismassimconfig.json", Me], EisId);
       focus(EisId);
       .print("Conectado ao EIS. Aguardando percepts...").

+!setup_shared_map
    <- lookupArtifact("shared_map", MapId);
       focus(MapId).
-!setup_shared_map
    <- .wait(50);
       !try_create_map.

+!try_create_map
    <- makeArtifact("shared_map", "env.SharedMap", [], MapId);
       focus(MapId).
-!try_create_map
    <- .wait(100);
       !setup_shared_map.

// SIM-START percepts
+name(N)     <- .print("SIM-START: nome = ", N).
+team(T)     <- .print("SIM-START: time = ", T).
+steps(S)    <- .print("SIM-START: total steps = ", S).

// Quando descobre um dispenser, tenta coletar
+new_dispenser(X, Y, Type)
    : not has_block & not collecting(_, _, _)
    <- .print("Novo dispenser ", Type, " em (", X, ",", Y, ")! Indo coletar.");
       !collect_block(Type).
