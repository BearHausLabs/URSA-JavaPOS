package com.target.devicemanager.components.msr;

import com.target.devicemanager.common.DeviceLifecycleResponse;
import com.target.devicemanager.common.StructuredEventLogger;
import com.target.devicemanager.common.entities.DeviceError;
import com.target.devicemanager.common.entities.DeviceException;
import com.target.devicemanager.common.entities.DeviceHealthResponse;
import com.target.devicemanager.components.msr.entities.MsrData;
import com.target.devicemanager.components.msr.entities.MsrError;
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
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/msr")
@Tag(name = "MSR")
@Profile({"local", "dev", "prod"})
@ConditionalOnProperty(name = "possum.device.msr.enabled", havingValue = "true", matchIfMissing = true)
public class MsrController {

    private final MsrManager msrManager;
    private static final Logger LOGGER = LoggerFactory.getLogger(MsrController.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getMsrServiceName(), "MsrController", LOGGER);

    @Autowired
    public MsrController(MsrManager msrManager) {
        if (msrManager == null) {
            throw new IllegalArgumentException("msrManager cannot be null");
        }
        this.msrManager = msrManager;
    }

    @Operation(description = "Blocking wait for card swipe. Returns track data when card is swiped.")
    @GetMapping("/read")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "412", description = "READ_FAILED, NO_DATA",
                    content = @Content(schema = @Schema(implementation = MsrError.class))),
            @ApiResponse(responseCode = "500", description = "UNEXPECTED_ERROR",
                    content = @Content(schema = @Schema(implementation = DeviceError.class))),
            @ApiResponse(responseCode = "404", description = "DEVICE_OFFLINE",
                    content = @Content(schema = @Schema(implementation = DeviceError.class))),
            @ApiResponse(responseCode = "409", description = "DEVICE_BUSY",
                    content = @Content(schema = @Schema(implementation = DeviceError.class)))
    })
    public MsrData readCard() throws DeviceException {
        String url = "/v1/msr/read";
        log.successAPI("request", 1, url, null, 0);
        try {
            MsrData data = msrManager.readCard();
            log.successAPI("response", 1, url, data.toString(), 200);
            return data;
        } catch (DeviceException deviceException) {
            int statusCode = deviceException.getDeviceError().getStatusCode().value();
            log.failureAPI("response", 13, url, deviceException.getDeviceError().toString(), statusCode, deviceException);
            throw deviceException;
        }
    }

    @Operation(description = "Cancel a pending card read")
    @DeleteMapping("/read")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Read cancelled")
    })
    public void cancelRead() {
        String url = "/v1/msr/read";
        log.successAPI("request", 1, url, "cancel", 0);
        msrManager.cancelRead();
        log.successAPI("response", 1, url, "OK", 200);
    }

    @Operation(description = "Reports MSR health")
    @GetMapping("/health")
    public DeviceHealthResponse getHealth() {
        String url = "/v1/msr/health";
        log.successAPI("request", 1, url, null, 0);
        DeviceHealthResponse response = msrManager.getHealth();
        log.successAPI("response", 1, url, response.toString(), 200);
        return response;
    }

    @Operation(description = "Reports MSR cached health status")
    @GetMapping("/healthstatus")
    public DeviceHealthResponse getStatus() {
        String url = "/v1/msr/healthstatus";
        log.successAPI("request", 1, url, null, 0);
        DeviceHealthResponse response = msrManager.getStatus();
        log.successAPI("response", 1, url, response.toString(), 200);
        return response;
    }

    // --- Lifecycle endpoints ---

    @Operation(description = "JPOS open — establish connection to MSR")
    @PostMapping("/lifecycle/open")
    public DeviceLifecycleResponse lifecycleOpen(@RequestParam String logicalName) throws JposException {
        String url = "/v1/msr/lifecycle/open";
        log.successAPI("request", 1, url, logicalName, 0);
        msrManager.openDevice(logicalName);
        return msrManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS claim — exclusive access to MSR")
    @PostMapping("/lifecycle/claim")
    public DeviceLifecycleResponse lifecycleClaim(@RequestParam(defaultValue = "30000") int timeout) throws JposException {
        String url = "/v1/msr/lifecycle/claim";
        log.successAPI("request", 1, url, null, 0);
        msrManager.claimDevice(timeout);
        return msrManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS enable — enable MSR for operations")
    @PostMapping("/lifecycle/enable")
    public DeviceLifecycleResponse lifecycleEnable() throws JposException {
        String url = "/v1/msr/lifecycle/enable";
        log.successAPI("request", 1, url, null, 0);
        msrManager.enableDevice();
        return msrManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS disable — disable MSR")
    @PostMapping("/lifecycle/disable")
    public DeviceLifecycleResponse lifecycleDisable() throws JposException {
        String url = "/v1/msr/lifecycle/disable";
        log.successAPI("request", 1, url, null, 0);
        msrManager.disableDevice();
        return msrManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS release — release exclusive access")
    @PostMapping("/lifecycle/release")
    public DeviceLifecycleResponse lifecycleRelease() throws JposException {
        String url = "/v1/msr/lifecycle/release";
        log.successAPI("request", 1, url, null, 0);
        msrManager.releaseDevice();
        return msrManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS close — close connection to MSR")
    @PostMapping("/lifecycle/close")
    public DeviceLifecycleResponse lifecycleClose() throws JposException {
        String url = "/v1/msr/lifecycle/close";
        log.successAPI("request", 1, url, null, 0);
        msrManager.closeDevice();
        return msrManager.getLifecycleStatus();
    }

    @Operation(description = "Get current JPOS lifecycle state")
    @GetMapping("/lifecycle")
    public DeviceLifecycleResponse getLifecycleStatus() {
        String url = "/v1/msr/lifecycle";
        log.successAPI("request", 1, url, null, 0);
        return msrManager.getLifecycleStatus();
    }

}
