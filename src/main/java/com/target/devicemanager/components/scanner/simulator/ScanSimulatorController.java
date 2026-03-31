package com.target.devicemanager.components.scanner.simulator;

import com.target.devicemanager.common.SimulatorState;
import com.target.devicemanager.components.scanner.entities.Barcode;
import com.target.devicemanager.components.scanner.entities.ScannerError;
import com.target.devicemanager.components.scanner.entities.ScannerException;
import com.target.devicemanager.configuration.ApplicationConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/v1/simulate")
@Tag(name = "Scanner")
@ConditionalOnProperty(name = "possum.device.scanner.enabled", havingValue = "true", matchIfMissing = true)
public class ScanSimulatorController {
    private final SimulatedJposScanner simulatedScanner;
    private final ApplicationConfig applicationConfig;

    @Autowired
    public ScanSimulatorController(ApplicationConfig applicationConfig,
                                   SimulatedJposScanner simulatedScanner) {
        if (applicationConfig == null) {
            throw new IllegalArgumentException("applicationConfig cannot be null");
        }
        if (simulatedScanner == null) {
            throw new IllegalArgumentException("simulatedScanner cannot be null");
        }
        this.applicationConfig = applicationConfig;
        this.simulatedScanner = simulatedScanner;
    }

    @Operation(description = "Set barcode to complete the currently pending scan request")
    @PostMapping(path = "scan")
    public void setBarcodeData(@RequestBody Barcode barcode) throws ScannerException {
        if (!applicationConfig.IsSimulationMode()) {
            throw new UnsupportedOperationException("Simulation mode is not enabled.");
        }
        simulatedScanner.setBarcode(barcode);
    }

    @Operation(description = "Set current state of the scanner")
    @PostMapping(path = "scannerState")
    public void setDeviceState(@RequestParam SimulatorState simulatorState) {
        if (!applicationConfig.IsSimulationMode()) {
            throw new UnsupportedOperationException("Simulation mode is not enabled.");
        }
        simulatedScanner.setState(simulatorState);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<String> handleException(UnsupportedOperationException exception) {
        return new ResponseEntity<>("Not Found", HttpStatus.NOT_FOUND);
    }
}
