package com.target.devicemanager.components.cashdrawer;

import com.target.devicemanager.common.DeviceLifecycleResponse;
import com.target.devicemanager.common.StructuredEventLogger;
import com.target.devicemanager.common.entities.DeviceError;
import com.target.devicemanager.common.entities.DeviceException;
import com.target.devicemanager.common.entities.DeviceHealthResponse;
import com.target.devicemanager.components.cashdrawer.entities.CashDrawerError;
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
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/cashdrawer")
@Tag(name = "Cash Drawer")
@Profile({"local", "dev", "prod"})
@ConditionalOnProperty(name = "possum.device.cashdrawer.enabled", havingValue = "true", matchIfMissing = true)
public class CashDrawerController {

    private final CashDrawerManager cashDrawerManager;
    private static final Logger LOGGER = LoggerFactory.getLogger(CashDrawerController.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getCashDrawerServiceName(), "CashDrawerController", LOGGER);

    @Autowired
    public CashDrawerController(CashDrawerManager cashDrawerManager) {
        if (cashDrawerManager == null) {
            throw new IllegalArgumentException("cashDrawerManager cannot be null");
        }
        this.cashDrawerManager = cashDrawerManager;
    }

    @Operation(description = "Opens the cash drawer and waits until the cash drawer is closed before returning.")
    @PostMapping(path = {"/open", "/open/{drawerId}"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "412", description = "ALREADY_OPEN, OPEN_FAILED",
                    content = @Content(schema = @Schema(implementation = CashDrawerError.class))),
            @ApiResponse(responseCode = "500", description = "UNEXPECTED_ERROR",
                    content = @Content(schema = @Schema(implementation = DeviceError.class))),
            @ApiResponse(responseCode = "404", description = "DEVICE_OFFLINE",
                    content = @Content(schema = @Schema(implementation = DeviceError.class))),
            @ApiResponse(responseCode = "409", description = "DEVICE_BUSY",
                    content = @Content(schema = @Schema(implementation = DeviceError.class)))
    })
    public void openCashDrawer(
            @Parameter(description = "Drawer ID (1-based). Defaults to 1.")
            @PathVariable(required = false) Integer drawerId) throws DeviceException {
        int id = (drawerId != null) ? drawerId : 1;
        String url = "/v1/cashdrawer/open" + (drawerId != null ? "/" + drawerId : "");
        log.successAPI("request", 1, url, null, 0);
        try {
            cashDrawerManager.openCashDrawer(id);
            log.successAPI("response", 1, url, null, 200);
        } catch (DeviceException deviceException) {
            int statusCode = deviceException.getDeviceError().getStatusCode().value();
            log.failureAPI("response", 13, url, deviceException.getDeviceError().toString(), statusCode, deviceException);
            throw deviceException;
        }
    }

    @Operation(description = "Reconnects to cash drawer(s)")
    @PostMapping(path = {"/reconnect", "/reconnect/{drawerId}"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "DEVICE_OFFLINE",
                    content = @Content(schema = @Schema(implementation = DeviceError.class))),
            @ApiResponse(responseCode = "409", description = "DEVICE_BUSY",
                    content = @Content(schema = @Schema(implementation = DeviceError.class)))
    })
    public void reconnect(
            @Parameter(description = "Drawer ID (1-based). Omit to reconnect all.")
            @PathVariable(required = false) Integer drawerId) throws DeviceException {
        String url = "/v1/cashdrawer/reconnect" + (drawerId != null ? "/" + drawerId : "");
        log.successAPI("request", 1, url, null, 0);
        try {
            if (drawerId != null) {
                cashDrawerManager.reconnectDevice(drawerId);
            } else {
                cashDrawerManager.reconnectDevice();
            }
            log.successAPI("response", 1, url, null, 200);
        } catch (DeviceException deviceException) {
            int statusCode = deviceException.getDeviceError().getStatusCode().value();
            log.failureAPI("response", 13, url, deviceException.getDeviceError().toString(), statusCode, deviceException);
            throw deviceException;
        }
    }

    @Operation(description = "Reports cash drawer health")
    @GetMapping(path = {"/health", "/health/{drawerId}"})
    public ResponseEntity<List<DeviceHealthResponse>> getHealth(
            @Parameter(description = "Drawer ID (1-based). Omit for all drawers.")
            @PathVariable(required = false) Integer drawerId) {
        String url = "/v1/cashdrawer/health" + (drawerId != null ? "/" + drawerId : "");
        log.successAPI("request", 1, url, null, 0);

        List<DeviceHealthResponse> responseList;
        if (drawerId != null) {
            responseList = List.of(cashDrawerManager.getHealth(drawerId));
        } else {
            responseList = cashDrawerManager.getAllHealth();
        }

        for (DeviceHealthResponse response : responseList) {
            log.successAPI("response", 1, url, response.toString(), 200);
        }
        return ResponseEntity.ok(responseList);
    }

    @Operation(description = "Reports cash drawer status")
    @GetMapping("/healthstatus")
    public DeviceHealthResponse getStatus() {
        String url = "/v1/cashdrawer/healthstatus";
        log.successAPI("request", 1, url, null, 0);
        DeviceHealthResponse response = cashDrawerManager.getStatus();
        log.successAPI("response", 1, url, response.toString(), 200);
        return response;
    }

    // --- Lifecycle endpoints ---

    @Operation(description = "JPOS open -- establish connection to cash drawer")
    @PostMapping("/lifecycle/open")
    public List<DeviceLifecycleResponse> lifecycleOpen(
            @RequestParam String logicalName,
            @RequestParam(defaultValue = "1") int drawerId) throws JposException {
        String url = "/v1/cashdrawer/lifecycle/open";
        log.successAPI("request", 1, url, logicalName, 0);
        cashDrawerManager.openDevice(logicalName, drawerId);
        return cashDrawerManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS claim -- exclusive access to cash drawer")
    @PostMapping("/lifecycle/claim")
    public List<DeviceLifecycleResponse> lifecycleClaim(
            @RequestParam(defaultValue = "30000") int timeout,
            @RequestParam(defaultValue = "1") int drawerId) throws JposException {
        String url = "/v1/cashdrawer/lifecycle/claim";
        log.successAPI("request", 1, url, null, 0);
        cashDrawerManager.claimDevice(timeout, drawerId);
        return cashDrawerManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS enable -- enable cash drawer for operations")
    @PostMapping("/lifecycle/enable")
    public List<DeviceLifecycleResponse> lifecycleEnable(
            @RequestParam(defaultValue = "1") int drawerId) throws JposException {
        String url = "/v1/cashdrawer/lifecycle/enable";
        log.successAPI("request", 1, url, null, 0);
        cashDrawerManager.enableDevice(drawerId);
        return cashDrawerManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS disable -- disable cash drawer")
    @PostMapping("/lifecycle/disable")
    public List<DeviceLifecycleResponse> lifecycleDisable(
            @RequestParam(defaultValue = "1") int drawerId) throws JposException {
        String url = "/v1/cashdrawer/lifecycle/disable";
        log.successAPI("request", 1, url, null, 0);
        cashDrawerManager.disableDevice(drawerId);
        return cashDrawerManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS release -- release exclusive access")
    @PostMapping("/lifecycle/release")
    public List<DeviceLifecycleResponse> lifecycleRelease(
            @RequestParam(defaultValue = "1") int drawerId) throws JposException {
        String url = "/v1/cashdrawer/lifecycle/release";
        log.successAPI("request", 1, url, null, 0);
        cashDrawerManager.releaseDevice(drawerId);
        return cashDrawerManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS close -- close connection to cash drawer")
    @PostMapping("/lifecycle/close")
    public List<DeviceLifecycleResponse> lifecycleClose(
            @RequestParam(defaultValue = "1") int drawerId) throws JposException {
        String url = "/v1/cashdrawer/lifecycle/close";
        log.successAPI("request", 1, url, null, 0);
        cashDrawerManager.closeDevice(drawerId);
        return cashDrawerManager.getLifecycleStatus();
    }

    @Operation(description = "Get current JPOS lifecycle state for all cash drawers")
    @GetMapping("/lifecycle")
    public List<DeviceLifecycleResponse> getLifecycleStatus() {
        String url = "/v1/cashdrawer/lifecycle";
        log.successAPI("request", 1, url, null, 0);
        return cashDrawerManager.getLifecycleStatus();
    }

    @Operation(description = "Switch back to automatic reconnect mode")
    @PostMapping("/lifecycle/auto")
    public List<DeviceLifecycleResponse> lifecycleAuto() {
        String url = "/v1/cashdrawer/lifecycle/auto";
        log.successAPI("request", 1, url, null, 0);
        cashDrawerManager.setAutoMode();
        return cashDrawerManager.getLifecycleStatus();
    }
}
