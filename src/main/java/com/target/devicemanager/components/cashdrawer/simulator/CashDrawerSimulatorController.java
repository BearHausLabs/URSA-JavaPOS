package com.target.devicemanager.components.cashdrawer.simulator;

import com.target.devicemanager.common.SimulatorState;
import com.target.devicemanager.configuration.ApplicationConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/v1/simulate")
@Tag(name = "Cash Drawer")
@Profile("local")
@ConditionalOnProperty(name = "possum.device.cashdrawer.enabled", havingValue = "true", matchIfMissing = true)
public class CashDrawerSimulatorController {
    private final List<SimulatedJposCashDrawer> simulatedDrawers;
    private final ApplicationConfig applicationConfig;

    @Autowired
    public CashDrawerSimulatorController(ApplicationConfig applicationConfig, @Autowired(required = false) List<SimulatedJposCashDrawer> simulatedDrawers) {
        if (applicationConfig == null) {
            throw new IllegalArgumentException("applicationConfig cannot be null");
        }

        this.simulatedDrawers = simulatedDrawers != null ? simulatedDrawers : List.of();
        this.applicationConfig = applicationConfig;
    }

    private SimulatedJposCashDrawer pickDrawer(int drawerId) {
        return simulatedDrawers.stream()
                .filter(d -> drawerId == d.getDrawerId())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown drawer ID: " + drawerId));
    }

    @Operation(description = "Set current status of the cash drawer")
    @PostMapping(path = "cashdrawerStatus")
    public void setDeviceStatus(
            @RequestParam CashDrawerStatus cashDrawerStatus,
            @RequestParam(defaultValue = "1") int drawerId) {
        if (!applicationConfig.IsSimulationMode()) {
            throw new UnsupportedOperationException("Simulation mode is not enabled.");
        }

        pickDrawer(drawerId).setStatus(cashDrawerStatus);
    }

    @Operation(description = "Set current state of the cash drawer")
    @PostMapping(path = "cashdrawerState")
    public void setDeviceState(
            @RequestParam SimulatorState simulatorState,
            @RequestParam(defaultValue = "1") int drawerId) {
        if (!applicationConfig.IsSimulationMode()) {
            throw new UnsupportedOperationException("Simulation mode is not enabled.");
        }

        pickDrawer(drawerId).setState(simulatorState);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<String> handleException(UnsupportedOperationException exception) {
        return new ResponseEntity<>("Not Found", HttpStatus.NOT_FOUND);
    }
}
