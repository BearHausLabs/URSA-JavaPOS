package com.target.devicemanager.components.msr.simulator;

import com.target.devicemanager.common.SimulatorState;
import com.target.devicemanager.components.msr.entities.MsrData;
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
@Tag(name = "MSR")
@Profile("local")
@ConditionalOnProperty(name = "possum.device.msr.enabled", havingValue = "true", matchIfMissing = true)
public class MsrSimulatorController {
    private final SimulatedJposMsr simulatedJposMsr;
    private final ApplicationConfig applicationConfig;

    public MsrSimulatorController(ApplicationConfig applicationConfig, SimulatedJposMsr simulatedJposMsr) {
        if (applicationConfig == null) {
            throw new IllegalArgumentException("applicationConfig cannot be null");
        }
        if (simulatedJposMsr == null) {
            throw new IllegalArgumentException("simulatedJposMsr cannot be null");
        }
        this.simulatedJposMsr = simulatedJposMsr;
        this.applicationConfig = applicationConfig;
    }

    @Operation(description = "Simulate a card swipe with track data")
    @PostMapping(path = "msrSwipe")
    public void swipeCard(@RequestBody MsrData msrData) {
        if (!applicationConfig.IsSimulationMode()) {
            throw new UnsupportedOperationException("Simulation mode is not enabled.");
        }
        simulatedJposMsr.swipeCard(msrData);
    }

    @Operation(description = "Set current state of the MSR")
    @PostMapping(path = "msrState")
    public void setDeviceState(@RequestParam SimulatorState simulatorState) {
        if (!applicationConfig.IsSimulationMode()) {
            throw new UnsupportedOperationException("Simulation mode is not enabled.");
        }
        simulatedJposMsr.setState(simulatorState);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<String> handleException(UnsupportedOperationException exception) {
        return new ResponseEntity<>("Not Found", HttpStatus.NOT_FOUND);
    }
}
