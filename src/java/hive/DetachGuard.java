package hive;

/**
 * Constante de threshold para o guard de detach do STUCK recovery (#48).
 * Sincronizar MAX_CONSECUTIVE_FAILS com os literais em perception.asl:
 *   abort se detach_stuck_fails(TaskName, F) & F >= 2
 *
 * Contexto: check_stuck dispara a cada ~50 steps; 2 falhas = ~100 steps de desperdício
 * garantido antes do abort, mantendo o threshold abaixo do limite DoD (< 5 por agente).
 */
public class DetachGuard {

    /** Falhas consecutivas de detach (failed_target) que disparam o abort da task. */
    public static final int MAX_CONSECUTIVE_FAILS = 2;
}
