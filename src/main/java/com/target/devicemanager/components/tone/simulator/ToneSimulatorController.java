package com.target.devicemanager.components.tone.simulator;

import com.target.devicemanager.common.SimulatorState;
import com.target.devicemanager.configuration.ApplicationConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/v1/simulate")
@Tag(name = "ToneIndicator")
@Profile("local")
@ConditionalOnProperty(name = "possum.device.tone.enabled", havingValue = "true", matchIfMissing = true)
public class ToneSimulatorController {
    private final SimulatedJposTone simulatedJposTone;
    private final ApplicationConfig applicationConfig;

    public ToneSimulatorController(ApplicationConfig applicationConfig, SimulatedJposTone simulatedJposTone) {
        if (applicationConfig == null) {
            throw new IllegalArgumentException("applicationConfig cannot be null");
        }
        if (simulatedJposTone == null) {
            throw new IllegalArgumentException("simulatedJposTone cannot be null");
        }
        this.simulatedJposTone = simulatedJposTone;
        this.applicationConfig = applicationConfig;
    }

    @Operation(description = "Set current state of the ToneIndicator")
    @PostMapping(path = "toneState")
    public void setDeviceState(@RequestParam SimulatorState simulatorState) {
        if (!applicationConfig.IsSimulationMode()) {
            throw new UnsupportedOperationException("Simulation mode is not enabled.");
        }
        simulatedJposTone.setState(simulatorState);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<String> handleException(UnsupportedOperationException exception) {
        return new ResponseEntity<>("Not Found", HttpStatus.NOT_FOUND);
    }
}
