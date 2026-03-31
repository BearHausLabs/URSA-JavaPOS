package com.target.devicemanager.components.scanner;

import com.target.devicemanager.common.DeviceLifecycleResponse;
import com.target.devicemanager.common.StructuredEventLogger;
import com.target.devicemanager.common.entities.DeviceError;
import com.target.devicemanager.common.entities.DeviceHealthResponse;
import com.target.devicemanager.components.scanner.entities.Barcode;
import com.target.devicemanager.components.scanner.entities.ScannerException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jpos.JposException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
@RequestMapping(value = "/v1")
@Tag(name = "Scanner")
@ConditionalOnProperty(name = "possum.device.scanner.enabled", havingValue = "true", matchIfMissing = true)
public class ScannerController {

    private final ScannerManager scannerManager;
    private static final Logger LOGGER = LoggerFactory.getLogger(ScannerController.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getScannerServiceName(), "ScannerController", LOGGER);

    @Autowired
    public ScannerController(ScannerManager scannerManager) {
        if (scannerManager == null) {
            throw new IllegalArgumentException("scannerManager cannot be null");
        }
        this.scannerManager = scannerManager;
    }

    @Operation(description = "Retrieve barcode data from connected scanner")
    @GetMapping(path = "/scan")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "204", description = "Scan request was cancelled"),
            @ApiResponse(responseCode = "500", description = "UNEXPECTED_ERROR",
                    content = @Content(schema = @Schema(implementation = DeviceError.class))),
            @ApiResponse(responseCode = "404", description = "DEVICE_OFFLINE",
                    content = @Content(schema = @Schema(implementation = DeviceError.class))),
            @ApiResponse(responseCode = "409", description = "DEVICE_BUSY",
                    content = @Content(schema = @Schema(implementation = DeviceError.class)))
    })
    public Barcode getScannerData() throws ScannerException {
        String url = "/v1/scan";
        log.success("API Request Received", 1);
        try {
            Barcode data = scannerManager.getData();
            log.successAPI("API Request Completed Successfully", 1, url, data == null ? null : data.toString(), 200);
            return data;
        } catch (ScannerException scannerException) {
            DeviceError error = scannerException.getDeviceError();
            String code = error != null ? error.getCode() : null;
            int status = (error != null && error.getStatusCode() != null)
                    ? error.getStatusCode().value()
                    : 0;

            int severity = (!Objects.equals(code, "DISABLED") &&
                    !Objects.equals(code, "DEVICE_BUSY")) ? 13 : 1;

            log.failureAPI(
                    "API Request Failed with ScannerException",
                    severity,
                    url,
                    error != null ? error.toString() : null,
                    status,
                    scannerException
            );
            throw scannerException;
        }
    }

    @Operation(description = "Cancel previously requested scan")
    @DeleteMapping(path = "/scan")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Scan request canceled. Scanner has been disabled"),
            @ApiResponse(responseCode = "412", description = "ALREADY_DISABLED",
                    content = @Content(schema = @Schema(implementation = DeviceError.class)))
    })
    public void cancelScanRequest() throws ScannerException {
        String url = "/v1/scan";
        log.success("API Request Received", 1);
        try {
            scannerManager.cancelScanRequest();
            log.successAPI("API Request Completed Successfully", 1, url, "OK", 200);
        } catch (ScannerException scannerException) {
            DeviceError error = scannerException.getDeviceError();
            String code = error != null ? error.getCode() : null;
            int status = (error != null && error.getStatusCode() != null)
                    ? error.getStatusCode().value()
                    : 0;

            int severity = !Objects.equals(code, "ALREADY_DISABLED") ? 13 : 1;

            log.failureAPI(
                    "API Request Failed with ScannerException",
                    severity,
                    url,
                    error != null ? error.toString() : null,
                    status,
                    scannerException
            );
            throw scannerException;
        }
    }

    @Operation(description = "Reports the health of the scanner")
    @GetMapping(path = "/scanner/health")
    public ResponseEntity<DeviceHealthResponse> getHealth() {
        String url = "/v1/scanner/health";
        log.success("API Request Received", 1);
        DeviceHealthResponse response = scannerManager.getHealth();
        log.successAPI("API Request Completed Successfully", 1, url, response == null ? null : response.toString(), 200);
        return ResponseEntity.ok(response);
    }

    @Operation(description = "Reports scanner status")
    @GetMapping(path = "/scanner/healthstatus")
    public ResponseEntity<DeviceHealthResponse> getStatus() {
        String url = "/v1/scanner/healthstatus";
        log.success("API Request Received", 1);
        DeviceHealthResponse response = scannerManager.getStatus();
        log.successAPI("API Request Completed Successfully", 1, url, response == null ? null : response.toString(), 200);
        return ResponseEntity.ok(response);
    }

    // --- Lifecycle endpoints (same pattern as printer, cashdrawer, etc.) ---

    @Operation(description = "JPOS open -- establish connection to scanner")
    @PostMapping("/scanner/lifecycle/open")
    public DeviceLifecycleResponse lifecycleOpen(
            @RequestParam String logicalName) throws JposException {
        String url = "/v1/scanner/lifecycle/open";
        log.successAPI("request", 1, url, logicalName, 0);
        scannerManager.openDevice(logicalName);
        return scannerManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS claim -- exclusive access to scanner")
    @PostMapping("/scanner/lifecycle/claim")
    public DeviceLifecycleResponse lifecycleClaim(
            @RequestParam(defaultValue = "30000") int timeout) throws JposException {
        String url = "/v1/scanner/lifecycle/claim";
        log.successAPI("request", 1, url, null, 0);
        scannerManager.claimDevice(timeout);
        return scannerManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS enable -- enable scanner for operations")
    @PostMapping("/scanner/lifecycle/enable")
    public DeviceLifecycleResponse lifecycleEnable() throws JposException {
        String url = "/v1/scanner/lifecycle/enable";
        log.successAPI("request", 1, url, null, 0);
        scannerManager.enableDevice();
        return scannerManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS disable -- disable scanner")
    @PostMapping("/scanner/lifecycle/disable")
    public DeviceLifecycleResponse lifecycleDisable() throws JposException {
        String url = "/v1/scanner/lifecycle/disable";
        log.successAPI("request", 1, url, null, 0);
        scannerManager.disableDevice();
        return scannerManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS release -- release exclusive access")
    @PostMapping("/scanner/lifecycle/release")
    public DeviceLifecycleResponse lifecycleRelease() throws JposException {
        String url = "/v1/scanner/lifecycle/release";
        log.successAPI("request", 1, url, null, 0);
        scannerManager.releaseDevice();
        return scannerManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS close -- close connection to scanner")
    @PostMapping("/scanner/lifecycle/close")
    public DeviceLifecycleResponse lifecycleClose() throws JposException {
        String url = "/v1/scanner/lifecycle/close";
        log.successAPI("request", 1, url, null, 0);
        scannerManager.closeDevice();
        return scannerManager.getLifecycleStatus();
    }

    @Operation(description = "Get current JPOS lifecycle state for scanner")
    @GetMapping("/scanner/lifecycle")
    public DeviceLifecycleResponse getLifecycleStatus() {
        String url = "/v1/scanner/lifecycle";
        log.successAPI("request", 1, url, null, 0);
        return scannerManager.getLifecycleStatus();
    }
}
