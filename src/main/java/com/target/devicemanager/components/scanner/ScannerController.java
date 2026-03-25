package com.target.devicemanager.components.scanner;

import com.target.devicemanager.common.DeviceLifecycleResponse;
import com.target.devicemanager.common.StructuredEventLogger;
import com.target.devicemanager.common.entities.DeviceError;
import com.target.devicemanager.common.entities.DeviceException;
import com.target.devicemanager.common.entities.DeviceHealthResponse;
import com.target.devicemanager.components.scanner.entities.Barcode;
import com.target.devicemanager.components.scanner.entities.ScannerException;
import com.target.devicemanager.components.scanner.entities.ScannerType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

import java.util.List;
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
    @GetMapping(path = {"/scan", "/scan/{scannerType}"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "204", description = "Scan request was cancelled"),
            @ApiResponse(responseCode = "500", description = "UNEXPECTED_ERROR",
                    content = @Content(schema = @Schema( implementation = DeviceError.class))),
            @ApiResponse(responseCode = "404", description = "DEVICE_OFFLINE",
                    content = @Content(schema = @Schema( implementation = DeviceError.class))),
            @ApiResponse(responseCode = "409", description = "DEVICE_BUSY",
                    content = @Content(schema = @Schema( implementation = DeviceError.class)))
    })
    public Barcode getScannerData(@Parameter(description = "Scanner to Call") @PathVariable(required = false) ScannerType scannerType) throws ScannerException {
        String url;
        if (scannerType == null) {
            url = "/v1/scan";
            log.success("API Request Received", 1);
            try {
                Barcode data = scannerManager.getData(ScannerType.BOTH);
                log.successAPI("API Request Completed Successfully", 1, url, data == null ? null : data.toString(), 200);
                return data;
            } catch (ScannerException scannerException) {
                // If getCode() is DISABLED or DEVICE_BUSY, it means the scan request was cancelled either by the client or due to another scan request, so we log it as a less severe failure than other exceptions
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
        } else {
            url = "/v1/scan/" + scannerType;
            log.success("API Request Received", 1);
            try {
                Barcode data = scannerManager.getData(scannerType);
                log.successAPI("API Request Completed Successfully", 1, url, data == null ? null : data.toString(), 200);
                return data;
            } catch (ScannerException scannerException) {
                log.failureAPI("API Request Failed with ScannerException", 13, url, scannerException.getDeviceError() == null ? null : scannerException.getDeviceError().toString(), scannerException.getDeviceError() == null ? 0 : scannerException.getDeviceError().getStatusCode().value(), scannerException);
                throw scannerException;
            }
        }
    }

    @Operation(description = "Cancel previously requested scan")
    @DeleteMapping(path = "/scan")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Scan request canceled. Scanner has been disabled"),
            @ApiResponse(responseCode = "412", description = "ALREADY_DISABLED",
                    content = @Content(schema = @Schema( implementation = DeviceError.class)))
    })
    public void cancelScanRequest() throws ScannerException {
        String url = "/v1/scan";
        log.success("API Request Received", 1);
        try {
            scannerManager.cancelScanRequest();
            log.successAPI("API Request Completed Successfully", 1, url, "OK", 200);
        } catch (ScannerException scannerException) {
            // If getCode() is ALREADY_DISABLED, it means the scan request was already cancelled before this call, so we log it as a less severe failure than other exceptions
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

    @Operation(description = "Reports the health of one scanner (FLATBED or HANDHELD)")
    @GetMapping(path = {"/scanner/health", "/scanner/health/{scannerType}"})
    public ResponseEntity<List<DeviceHealthResponse>> getHealth(@Parameter(description = "Scanner to Call") @PathVariable(required = false) ScannerType scannerType) {
        List<DeviceHealthResponse> responseList;
        String url;
        if (scannerType == null) {
            url = "/v1/scanner/health";
            log.success("API Request Received", 1);
            responseList = scannerManager.getHealth(ScannerType.BOTH);
        } else {
            url = "/v1/scanner/health/" + scannerType;
            log.success("API Request Received", 1);
            responseList = scannerManager.getHealth(scannerType);
        }

        for (DeviceHealthResponse deviceResponse : responseList) {
            log.successAPI("API Request Completed Successfully", 1, url, deviceResponse == null ? null : deviceResponse.toString(), 200);
        }
        return ResponseEntity.ok(responseList);
    }

    @Operation(description = "Reports scanners status")
    @GetMapping(path = "/scanner/healthstatus")
    public ResponseEntity<List<DeviceHealthResponse>> getStatus() {
        String url = "/v1/scanner/healthstatus";
        log.success("API Request Received", 1);

        List<DeviceHealthResponse> responseList = scannerManager.getStatus();

        for (DeviceHealthResponse deviceResponse : responseList) {
            log.successAPI("API Request Completed Successfully", 1, url, deviceResponse == null ? null : deviceResponse.toString(), 200);
        }
        return ResponseEntity.ok(responseList);
    }

    @Operation(description = "Reconnects scanners")
    @PostMapping(path = "/scanner/reconnect")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "DEVICE_OFFLINE",
                    content = @Content(schema = @Schema( implementation = DeviceError.class))),
            @ApiResponse(responseCode = "409", description = "DEVICE_BUSY",
                    content = @Content(schema = @Schema( implementation = DeviceError.class))),
            @ApiResponse(responseCode = "500", description = "UNEXPECTED_ERROR",
                    content = @Content(schema = @Schema( implementation = DeviceError.class)))
    })
    void reconnect() throws DeviceException {
        String url = "/v1/scanner/reconnect";
        log.success("API Request Received", 1);
        try {
            scannerManager.reconnectScanners();
            log.successAPI("API Request Completed Successfully", 1, url, "OK", 200);
        } catch (DeviceException deviceException) {
            log.failureAPI("API Request Failed with DeviceException", 13, url, deviceException.getDeviceError() == null ? null : deviceException.getDeviceError().toString(), deviceException.getDeviceError() == null ? 0 : deviceException.getDeviceError().getStatusCode().value(), deviceException);
            throw deviceException;
        }
    }

    // --- Step 5e: Lifecycle endpoints ---
    // Scanner lifecycle requires a scannerType (FLATBED or HANDHELD)

    @Operation(description = "JPOS open — establish connection to scanner")
    @PostMapping("/scanner/lifecycle/open")
    public List<DeviceLifecycleResponse> lifecycleOpen(
            @RequestParam String logicalName,
            @RequestParam(defaultValue = "FLATBED") ScannerType scannerType) throws JposException {
        String url = "/v1/scanner/lifecycle/open";
        log.successAPI("request", 1, url, logicalName, 0);
        scannerManager.openDevice(logicalName, scannerType);
        return scannerManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS claim — exclusive access to scanner")
    @PostMapping("/scanner/lifecycle/claim")
    public List<DeviceLifecycleResponse> lifecycleClaim(
            @RequestParam(defaultValue = "30000") int timeout,
            @RequestParam(defaultValue = "FLATBED") ScannerType scannerType) throws JposException {
        String url = "/v1/scanner/lifecycle/claim";
        log.successAPI("request", 1, url, null, 0);
        scannerManager.claimDevice(timeout, scannerType);
        return scannerManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS enable — enable scanner for operations")
    @PostMapping("/scanner/lifecycle/enable")
    public List<DeviceLifecycleResponse> lifecycleEnable(
            @RequestParam(defaultValue = "FLATBED") ScannerType scannerType) throws JposException {
        String url = "/v1/scanner/lifecycle/enable";
        log.successAPI("request", 1, url, null, 0);
        scannerManager.enableDevice(scannerType);
        return scannerManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS disable — disable scanner")
    @PostMapping("/scanner/lifecycle/disable")
    public List<DeviceLifecycleResponse> lifecycleDisable(
            @RequestParam(defaultValue = "FLATBED") ScannerType scannerType) throws JposException {
        String url = "/v1/scanner/lifecycle/disable";
        log.successAPI("request", 1, url, null, 0);
        scannerManager.disableDevice(scannerType);
        return scannerManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS release — release exclusive access")
    @PostMapping("/scanner/lifecycle/release")
    public List<DeviceLifecycleResponse> lifecycleRelease(
            @RequestParam(defaultValue = "FLATBED") ScannerType scannerType) throws JposException {
        String url = "/v1/scanner/lifecycle/release";
        log.successAPI("request", 1, url, null, 0);
        scannerManager.releaseDevice(scannerType);
        return scannerManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS close — close connection to scanner")
    @PostMapping("/scanner/lifecycle/close")
    public List<DeviceLifecycleResponse> lifecycleClose(
            @RequestParam(defaultValue = "FLATBED") ScannerType scannerType) throws JposException {
        String url = "/v1/scanner/lifecycle/close";
        log.successAPI("request", 1, url, null, 0);
        scannerManager.closeDevice(scannerType);
        return scannerManager.getLifecycleStatus();
    }

    @Operation(description = "Get current JPOS lifecycle state for all scanners")
    @GetMapping("/scanner/lifecycle")
    public List<DeviceLifecycleResponse> getLifecycleStatus() {
        String url = "/v1/scanner/lifecycle";
        log.successAPI("request", 1, url, null, 0);
        return scannerManager.getLifecycleStatus();
    }

    @Operation(description = "Switch back to automatic reconnect mode")
    @PostMapping("/scanner/lifecycle/auto")
    public List<DeviceLifecycleResponse> lifecycleAuto() {
        String url = "/v1/scanner/lifecycle/auto";
        log.successAPI("request", 1, url, null, 0);
        scannerManager.setAutoMode();
        return scannerManager.getLifecycleStatus();
    }
}
