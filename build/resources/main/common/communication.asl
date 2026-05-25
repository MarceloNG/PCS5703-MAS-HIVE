// ============================================================
// communication.asl — Mensagens de sincronizacao para connect
// ============================================================

+!request_connect(CollectorName, TargetStep)
    : my_pos(MX, MY)
    <- .my_name(Me);
       .send(CollectorName, tell, connect_request(Me, MX, MY, TargetStep));
       .print("[COMM] Pedido de connect enviado para ", CollectorName, " no step ", TargetStep).

+connect_request(AssemblerName, AsmX, AsmY, TargetStep)[source(S)]
    <- .print("[COMM] Recebi pedido de connect de ", AssemblerName, " para step ", TargetStep);
       .abolish(navigating_to_meeting_point(_));
       .abolish(has_destination(_, _));
       +pending_connect(AssemblerName, AsmX, AsmY, TargetStep).

+!confirm_connect(AssemblerName)
    : my_pos(MX, MY)
    <- .my_name(Me);
       .send(AssemblerName, tell, connect_confirmed(Me, MX, MY));
       .print("[COMM] Confirmacao de connect enviada para ", AssemblerName).

+connect_confirmed(CollectorName, ColX, ColY)[source(S)]
    <- .print("[COMM] ", CollectorName, " confirmou connect em (", ColX, ",", ColY, ")");
       +partner_confirmed(CollectorName, ColX, ColY).

+!request_connect(_, _) <- true.
+!confirm_connect(_) <- true.
