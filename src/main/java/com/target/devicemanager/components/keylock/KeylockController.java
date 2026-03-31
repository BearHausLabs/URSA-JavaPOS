package com.target.devicemanager.components.keylock;

import com.target.devicemanager.common.DeviceLifecycleResponse;
import com.target.devicemanager.common.StructuredEventLogger;
import com.target.devicemanager.common.entities.DeviceError;
import com.target.devicemanager.common.entities.DeviceException;
import com.target.devicemanager.common.entities.DeviceHealthResponse;
import com.target.devicemanager.components.keylock.entities.KeyPosition;
import com.target.devicemanager.components.keylock.entities.KeylockError;
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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/v1/keylock")
@Tag(name = "Keylock")
@ConditionalOnProperty(name = "possum.device.keylock.enabled", havingValue = "true", matchIfMissing = true)
public class KeylockController {

    private final KeylockManager keylockManager;
    private static final Logger LOGGER = LoggerFactory.getLogger(KeylockController.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of("keylock", "KeylockController", LOGGER);

    @Autowired
    public KeylockController(KeylockManager keylockManager) {
        if (keylockManager == null) {
            throw new IllegalArgumentException("keylockManager cannot be null");
        }
        this.keylockManager = keylockManager;
    }

    @Operation(description = "Returns the current keylock position (LOCK, NORMAL, SUPERVISOR, UNKNOWN).")
    @GetMapping("/position")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "DEVICE_OFFLINE",
                    content = @Content(schema = @Schema(implementation = DeviceError.class))),
            @ApiResponse(responseCode = "409", description = "DEVICE_BUSY",
                    content = @Content(schema = @Schema(implementation = DeviceError.class))),
            @ApiResponse(responseCode = "412", description = "POSITION_READ_FAILED",
                    content = @Content(schema = @Schema(implementation = KeylockError.class))),
            @ApiResponse(responseCode = "500", description = "UNEXPECTED_ERROR",
                    content = @Content(schema = @Schema(implementation = DeviceError.class)))
    })
    public KeyPosition getKeyPosition() throws DeviceException {
        String url = "/v1/keylock/position";
        log.successAPI("request", 1, url, null, 0);
        try {
            KeyPosition position = keylockManager.getKeyPosition();
            log.successAPI("response", 1, url, position.toString(), 200);
            return position;
        } catch (DeviceException deviceException) {
            int statusCode = deviceException.getDeviceError().getStatusCode().value();
            log.failureAPI("response", 13, url, deviceException.getDeviceError().toString(), statusCode, deviceException);
            throw deviceException;
        }
    }

    @Operation(description = "Subscribe to real-time keylock position changes via Server-Sent Events (SSE). " +
            "Events are pushed as JSON objects containing the position field (LOCK, NORMAL, SUPERVISOR, UNKNOWN).")
    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE stream opened")
    })
    public SseEmitter subscribeToKeylockEvents() {
        String url = "/v1/keylock/events";
        log.successAPI("request", 1, url, null, 0);
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        keylockManager.addEventSubscriber(emitter);
        log.successAPI("response", 1, url, "SSE stream opened", 200);
        return emitter;
    }

    @Operation(description = "Reports keylock health")
    @GetMapping("/health")
    public DeviceHealthResponse getHealth() {
        String url = "/v1/keylock/health";
        log.successAPI("request", 1, url, null, 0);
        DeviceHealthResponse response = keylockManager.getHealth();
        log.successAPI("response", 1, url, response.toString(), 200);
        return response;
    }

    @Operation(description = "Reports keylock status")
    @GetMapping("/healthstatus")
    public DeviceHealthResponse getStatus() {
        String url = "/v1/keylock/healthstatus";
        log.successAPI("request", 1, url, null, 0);
        DeviceHealthResponse response = keylockManager.getStatus();
        log.successAPI("response", 1, url, response.toString(), 200);
        return response;
    }

    // --- Lifecycle endpoints ---

    @Operation(description = "JPOS open - establish connection to keylock")
    @PostMapping("/lifecycle/open")
    public DeviceLifecycleResponse lifecycleOpen(@RequestParam String logicalName) throws JposException {
        String url = "/v1/keylock/lifecycle/open";
        log.successAPI("request", 1, url, logicalName, 0);
        keylockManager.openDevice(logicalName);
        return keylockManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS claim - exclusive access to keylock")
    @PostMapping("/lifecycle/claim")
    public DeviceLifecycleResponse lifecycleClaim(@RequestParam(defaultValue = "30000") int timeout) throws JposException {
        String url = "/v1/keylock/lifecycle/claim";
        log.successAPI("request", 1, url, null, 0);
        keylockManager.claimDevice(timeout);
        return keylockManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS enable - enable keylock for operations")
    @PostMapping("/lifecycle/enable")
    public DeviceLifecycleResponse lifecycleEnable() throws JposException {
        String url = "/v1/keylock/lifecycle/enable";
        log.successAPI("request", 1, url, null, 0);
        keylockManager.enableDevice();
        return keylockManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS disable - disable keylock")
    @PostMapping("/lifecycle/disable")
    public DeviceLifecycleResponse lifecycleDisable() throws JposException {
        String url = "/v1/keylock/lifecycle/disable";
        log.successAPI("request", 1, url, null, 0);
        keylockManager.disableDevice();
        return keylockManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS release - release exclusive access")
    @PostMapping("/lifecycle/release")
    public DeviceLifecycleResponse lifecycleRelease() throws JposException {
        String url = "/v1/keylock/lifecycle/release";
        log.successAPI("request", 1, url, null, 0);
        keylockManager.releaseDevice();
        return keylockManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS close - close connection to keylock")
    @PostMapping("/lifecycle/close")
    public DeviceLifecycleResponse lifecycleClose() throws JposException {
        String url = "/v1/keylock/lifecycle/close";
        log.successAPI("request", 1, url, null, 0);
        keylockManager.closeDevice();
        return keylockManager.getLifecycleStatus();
    }

    @Operation(description = "Get current JPOS lifecycle state")
    @GetMapping("/lifecycle")
    public DeviceLifecycleResponse getLifecycleStatus() {
        String url = "/v1/keylock/lifecycle";
        log.successAPI("request", 1, url, null, 0);
        return keylockManager.getLifecycleStatus();
    }

}
