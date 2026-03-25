package com.target.devicemanager.components.keylock.simulator;

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
@Tag(name = "Keylock")
@ConditionalOnProperty(name = "possum.device.keylock.enabled", havingValue = "true", matchIfMissing = true)
public class KeylockSimulatorController {
    private final SimulatedJposKeylock simulatedJposKeylock;
    private final ApplicationConfig applicationConfig;

    public KeylockSimulatorController(ApplicationConfig applicationConfig, SimulatedJposKeylock simulatedJposKeylock) {
        if (applicationConfig == null) {
            throw new IllegalArgumentException("applicationConfig cannot be null");
        }

        if (simulatedJposKeylock == null) {
            throw new IllegalArgumentException("simulatedJposKeylock cannot be null");
        }

        this.simulatedJposKeylock = simulatedJposKeylock;
        this.applicationConfig = applicationConfig;
    }

    @Operation(description = "Set current position of the keylock")
    @PostMapping(path = "keylockPosition")
    public void setDevicePosition(@RequestParam KeylockPositionStatus keylockPositionStatus) {
        if (!applicationConfig.IsSimulationMode()) {
            throw new UnsupportedOperationException("Simulation mode is not enabled.");
        }

        simulatedJposKeylock.setPosition(keylockPositionStatus);
    }

    @Operation(description = "Set current state of the keylock")
    @PostMapping(path = "keylockState")
    public void setDeviceState(@RequestParam SimulatorState simulatorState) {
        if (!applicationConfig.IsSimulationMode()) {
            throw new UnsupportedOperationException("Simulation mode is not enabled.");
        }

        simulatedJposKeylock.setState(simulatorState);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<String> handleException(UnsupportedOperationException exception) {
        return new ResponseEntity<>("Not Found", HttpStatus.NOT_FOUND);
    }
}
