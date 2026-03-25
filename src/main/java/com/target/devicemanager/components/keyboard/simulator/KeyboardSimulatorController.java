package com.target.devicemanager.components.keyboard.simulator;

import com.target.devicemanager.common.SimulatorState;
import com.target.devicemanager.configuration.ApplicationConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/v1/simulate")
@Tag(name = "Keyboard")
@ConditionalOnProperty(name = "possum.device.keyboard.enabled", havingValue = "true", matchIfMissing = true)
public class KeyboardSimulatorController {
    private final SimulatedJposKeyboard simulatedJposKeyboard;
    private final ApplicationConfig applicationConfig;

    public KeyboardSimulatorController(ApplicationConfig applicationConfig, SimulatedJposKeyboard simulatedJposKeyboard) {
        if (applicationConfig == null) {
            throw new IllegalArgumentException("applicationConfig cannot be null");
        }

        if (simulatedJposKeyboard == null) {
            throw new IllegalArgumentException("simulatedJposKeyboard cannot be null");
        }

        this.simulatedJposKeyboard = simulatedJposKeyboard;
        this.applicationConfig = applicationConfig;
    }

    @Operation(description = "Simulate a key press on the POS keyboard")
    @PostMapping(path = "keyboardKey")
    public void simulateKeyPress(@RequestParam int keyCode) {
        if (!applicationConfig.IsSimulationMode()) {
            throw new UnsupportedOperationException("Simulation mode is not enabled.");
        }

        simulatedJposKeyboard.simulateKeyPress(keyCode);
    }

    @Operation(description = "Set current state of the POS keyboard")
    @PostMapping(path = "keyboardState")
    public void setDeviceState(@RequestParam SimulatorState simulatorState) {
        if (!applicationConfig.IsSimulationMode()) {
            throw new UnsupportedOperationException("Simulation mode is not enabled.");
        }

        simulatedJposKeyboard.setState(simulatorState);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<String> handleException(UnsupportedOperationException exception) {
        return new ResponseEntity<>("Not Found", HttpStatus.NOT_FOUND);
    }
}
