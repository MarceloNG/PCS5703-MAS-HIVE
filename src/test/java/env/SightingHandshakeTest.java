package env;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/**
 * U9 / #17: prova o calculo de offset do handshake de avistamento mutuo.
 * computeSightingOffset e metodo puro (sem CArtAgO), testavel sem sim.
 * Cenario: receptor B em (receiverX,receiverY) ve emissor A em (relX,relY)
 * relativo a B; A declarou estar em (senderX,senderY) no proprio frame.
 * Offset resultante converte frame-A -> frame-B: TX = X_A + dX.
 */
class SightingHandshakeTest {

    // Caso base do plano: B em (0,0), ve A em (+5,0), A declarou estar em (10,0).
    // DX = 0 - 5 - 10 = -15. Celula A em X=10: TX = 10 - 15 = -5 = Bpos-relX = 0-5 ✓
    @Test
    void offsetBaseCase() {
        int[] off = SharedMap.computeSightingOffset(0, 0, 5, 0, 10, 0);
        assertArrayEquals(new int[]{-15, 0}, off);
    }

    // Offset zero: A e B no mesmo frame (sem deslocamento relativo de origem).
    // B em (0,0), ve A em (-3,-2), A declarou estar em (3,2). DX = 0-(-3)-3 = 0.
    @Test
    void offsetZeroMesmoFrame() {
        int[] off = SharedMap.computeSightingOffset(0, 0, -3, -2, 3, 2);
        assertArrayEquals(new int[]{0, 0}, off);
    }

    // Simetria: DX_B = -(DX_A). Quando A ve B em (RelX,RelY), B ve A em (-RelX,-RelY).
    @Test
    void simetria() {
        int ax = 3, ay = 1, bx = -2, by = 4;
        int relX = 5, relY = -3; // A ve B em (5,-3) relativo a A
        // B ve A em (-5,3) relativo a B
        int[] dxB = SharedMap.computeSightingOffset(bx, by, -relX, -relY, ax, ay);
        int[] dxA = SharedMap.computeSightingOffset(ax, ay,  relX,  relY, bx, by);
        assertArrayEquals(new int[]{-dxA[0], -dxA[1]}, dxB);
    }

    // Agentes na mesma posicao absoluta mas frames deslocados.
    @Test
    void agentesComFramesDeslocados() {
        // B em (10,5) no frame de B; A em (3,8) no frame de A.
        // B ve A em (-2,1) relativo a B.
        int[] off = SharedMap.computeSightingOffset(10, 5, -2, 1, 3, 8);
        // DX = 10 - (-2) - 3 = 9; DY = 5 - 1 - 8 = -4
        assertArrayEquals(new int[]{9, -4}, off);
        // Verificacao: A em (3,8) no frame-A -> TX = 3+9 = 12 = Bx + (-relX) = 10+2 = 12 ✓
    }
}
