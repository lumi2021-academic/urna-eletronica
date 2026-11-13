package com.lumi2021.urna.platform;

import com.lumi2021.urna.platform.events.InputAction;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.*;

import java.util.Arrays;
import java.util.concurrent.locks.LockSupport;

public class MatrixKeypad implements AutoCloseable {

    private static final int[] ROW_PINS = {5, 6, 13, 19};
    private static final int[] COL_PINS = {12, 16, 20, 21};

    private static final InputAction[][] KEYS = {
            { InputAction.KEY_1, InputAction.KEY_2, InputAction.KEY_3, InputAction.BRANCO},
            { InputAction.KEY_4, InputAction.KEY_5, InputAction.KEY_6, InputAction.CORRIGE},
            { InputAction.KEY_7, InputAction.KEY_8, InputAction.KEY_9, InputAction.CONFIRMA},
            { InputAction.KEY_0, InputAction.KEY_0, InputAction.KEY_0, InputAction.CONFIRMA},
    };

    private static final long SETTLE_NANOS = 50_000;

    private final DigitalOutput[] rows = new DigitalOutput[4];
    private final DigitalInput[] columns = new DigitalInput[4];

    public MatrixKeypad(Context pi4j) {

        for (int i = 0; i < 4; i++) {
            var config = DigitalOutput.newConfigBuilder(pi4j)
                    .id("keypad-row-" + i)
                    .address(ROW_PINS[i])
                    .initial(DigitalState.HIGH)
                    .shutdown(DigitalState.HIGH);

            rows[i] = pi4j.create(config);
        }

        for (int i = 0; i < 4; i++) {
            var config = DigitalInput.newConfigBuilder(pi4j)
                    .id("keypad-col-" + i)
                    .address(COL_PINS[i])
                    .pull(PullResistance.PULL_UP);

            columns[i] = pi4j.create(config);
        }
    }

    public InputAction[][] scan() {
        InputAction[][] pressed = new InputAction[4][4];
        for (InputAction[] line : pressed) Arrays.fill(line, InputAction.NONE);

        for (int row = 0; row < 4; row++) {

            rows[row].low();
            LockSupport.parkNanos(SETTLE_NANOS);

            for (int col = 0; col < 4; col++) {
                if (columns[col].state() == DigitalState.LOW) {
                    pressed[row][col] = KEYS[row][col];
                }
            }

            rows[row].high();
        }

        return pressed;
    }

    @Override
    public void close() {
        for (var row : rows) row.high();
    }
}
