package com.target.devicemanager.components.tone;

import com.target.devicemanager.common.DeviceLifecycleResponse;
import com.target.devicemanager.common.StructuredEventLogger;
import com.target.devicemanager.common.entities.DeviceError;
import com.target.devicemanager.common.entities.DeviceException;
import com.target.devicemanager.common.entities.DeviceHealthResponse;
import com.target.devicemanager.components.tone.entities.ToneError;
import com.target.devicemanager.components.tone.entities.ToneRequest;
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
@RequestMapping("/v1/tone")
@Tag(name = "ToneIndicator")
@Profile({"local", "dev", "prod"})
@ConditionalOnProperty(name = "possum.device.tone.enabled", havingValue = "true", matchIfMissing = true)
public class ToneController {

    private final ToneManager toneManager;
    private static final Logger LOGGER = LoggerFactory.getLogger(ToneController.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getToneIndicatorServiceName(), "ToneController", LOGGER);

    @Autowired
    public ToneController(ToneManager toneManager) {
        if (toneManager == null) {
            throw new IllegalArgumentException("toneManager cannot be null");
        }
        this.toneManager = toneManager;
    }

    @Operation(description = "Play a standard beep")
    @PostMapping("/beep")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "500", description = "UNEXPECTED_ERROR",
                    content = @Content(schema = @Schema(implementation = DeviceError.class))),
            @ApiResponse(responseCode = "404", description = "DEVICE_OFFLINE",
                    content = @Content(schema = @Schema(implementation = DeviceError.class))),
            @ApiResponse(responseCode = "409", description = "DEVICE_BUSY",
                    content = @Content(schema = @Schema(implementation = DeviceError.class)))
    })
    public void beep() throws DeviceException {
        String url = "/v1/tone/beep";
        log.successAPI("request", 1, url, null, 0);
        try {
            toneManager.beep();
            log.successAPI("response", 1, url, null, 200);
        } catch (DeviceException deviceException) {
            int statusCode = deviceException.getDeviceError().getStatusCode().value();
            log.failureAPI("response", 13, url, deviceException.getDeviceError().toString(), statusCode, deviceException);
            throw deviceException;
        }
    }

    @Operation(description = "Play a custom tone with specified parameters")
    @PostMapping("/sound")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "412", description = "TONE_FAILED",
                    content = @Content(schema = @Schema(implementation = ToneError.class))),
            @ApiResponse(responseCode = "500", description = "UNEXPECTED_ERROR",
                    content = @Content(schema = @Schema(implementation = DeviceError.class))),
            @ApiResponse(responseCode = "404", description = "DEVICE_OFFLINE",
                    content = @Content(schema = @Schema(implementation = DeviceError.class))),
            @ApiResponse(responseCode = "409", description = "DEVICE_BUSY",
                    content = @Content(schema = @Schema(implementation = DeviceError.class)))
    })
    public void sound(@RequestBody ToneRequest toneRequest) throws DeviceException {
        String url = "/v1/tone/sound";
        log.successAPI("request", 1, url, toneRequest.toString(), 0);
        try {
            toneManager.playTone(toneRequest);
            log.successAPI("response", 1, url, null, 200);
        } catch (DeviceException deviceException) {
            int statusCode = deviceException.getDeviceError().getStatusCode().value();
            log.failureAPI("response", 13, url, deviceException.getDeviceError().toString(), statusCode, deviceException);
            throw deviceException;
        }
    }

    @Operation(description = "Reports ToneIndicator health")
    @GetMapping("/health")
    public DeviceHealthResponse getHealth() {
        String url = "/v1/tone/health";
        log.successAPI("request", 1, url, null, 0);
        DeviceHealthResponse response = toneManager.getHealth();
        log.successAPI("response", 1, url, response.toString(), 200);
        return response;
    }

    @Operation(description = "Reports ToneIndicator cached health status")
    @GetMapping("/healthstatus")
    public DeviceHealthResponse getStatus() {
        String url = "/v1/tone/healthstatus";
        log.successAPI("request", 1, url, null, 0);
        DeviceHealthResponse response = toneManager.getStatus();
        log.successAPI("response", 1, url, response.toString(), 200);
        return response;
    }

    // --- Lifecycle endpoints ---

    @Operation(description = "JPOS open — establish connection to ToneIndicator")
    @PostMapping("/lifecycle/open")
    public DeviceLifecycleResponse lifecycleOpen(@RequestParam String logicalName) throws JposException {
        String url = "/v1/tone/lifecycle/open";
        log.successAPI("request", 1, url, logicalName, 0);
        toneManager.openDevice(logicalName);
        return toneManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS claim — exclusive access to ToneIndicator")
    @PostMapping("/lifecycle/claim")
    public DeviceLifecycleResponse lifecycleClaim(@RequestParam(defaultValue = "30000") int timeout) throws JposException {
        String url = "/v1/tone/lifecycle/claim";
        log.successAPI("request", 1, url, null, 0);
        toneManager.claimDevice(timeout);
        return toneManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS enable — enable ToneIndicator for operations")
    @PostMapping("/lifecycle/enable")
    public DeviceLifecycleResponse lifecycleEnable() throws JposException {
        String url = "/v1/tone/lifecycle/enable";
        log.successAPI("request", 1, url, null, 0);
        toneManager.enableDevice();
        return toneManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS disable — disable ToneIndicator")
    @PostMapping("/lifecycle/disable")
    public DeviceLifecycleResponse lifecycleDisable() throws JposException {
        String url = "/v1/tone/lifecycle/disable";
        log.successAPI("request", 1, url, null, 0);
        toneManager.disableDevice();
        return toneManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS release — release exclusive access")
    @PostMapping("/lifecycle/release")
    public DeviceLifecycleResponse lifecycleRelease() throws JposException {
        String url = "/v1/tone/lifecycle/release";
        log.successAPI("request", 1, url, null, 0);
        toneManager.releaseDevice();
        return toneManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS close — close connection to ToneIndicator")
    @PostMapping("/lifecycle/close")
    public DeviceLifecycleResponse lifecycleClose() throws JposException {
        String url = "/v1/tone/lifecycle/close";
        log.successAPI("request", 1, url, null, 0);
        toneManager.closeDevice();
        return toneManager.getLifecycleStatus();
    }

    @Operation(description = "Get current JPOS lifecycle state")
    @GetMapping("/lifecycle")
    public DeviceLifecycleResponse getLifecycleStatus() {
        String url = "/v1/tone/lifecycle";
        log.successAPI("request", 1, url, null, 0);
        return toneManager.getLifecycleStatus();
    }

}
