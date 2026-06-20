package hive;

/**
 * Constante de threshold para o guard de pré-rotação de submit (#47).
 * Sincronizar MAX_CONSECUTIVE_FAILS com os literais em connect_protocol.asl:
 *   P0: F >= 3  |  P2: F < 3
 */
public class RotationGuard {

    /** Falhas acumuladas de rotate que disparam o abort da task. */
    public static final int MAX_CONSECUTIVE_FAILS = 3;
}
