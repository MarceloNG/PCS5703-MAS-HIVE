// ============================================================
// shared_map_init.asl — inicializacao do mapa por-agente (Fase D / U3)
// ============================================================
// Cada agente cria a SUA propria instancia de SharedMap ("map_"+nome): um frame
// local privado. Nao ha mais um shared_map compartilhado (sem frame global
// pre-fusao; a fusao cross-agente e a U9, deferida). 'Me' ja nomeia o EISAccess,
// dai o prefixo "map_". Plan unico, incluido pelos 5 .asl de role — antes era
// copiado verbatim em cada um (review Fase D).

+!setup_shared_map
    <- .my_name(Me);
       .concat("map_", Me, MapName);
       makeArtifact(MapName, "env.SharedMap", [], MapId);
       focus(MapId).
// Falha de makeArtifact e rara (nome unico), mas se ocorrer o agente roda SEM
// mapa e toda operacao de mapa/A* vira no-op. Logar alto em vez de engolir em
// silencio (era `<- true`), para a degradacao ser diagnosticavel no log.
-!setup_shared_map
    <- .my_name(Me);
       .print("[FATAL] setup_shared_map falhou — agente roda SEM mapa (navegacao no-op): ", Me).
