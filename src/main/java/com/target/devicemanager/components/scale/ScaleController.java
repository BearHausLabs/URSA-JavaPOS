package com.target.devicemanager.components.scale;

import com.target.devicemanager.common.DeviceLifecycleResponse;
import com.target.devicemanager.common.StructuredEventLogger;
import com.target.devicemanager.common.entities.DeviceError;
import com.target.devicemanager.common.entities.DeviceException;
import com.target.devicemanager.common.entities.DeviceHealthResponse;
import com.target.devicemanager.components.scale.entities.FormattedWeight;
import com.target.devicemanager.components.scale.entities.ScaleError;
import com.target.devicemanager.components.scale.entities.ScaleException;
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

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping(value = "/v1")
@Tag(name = "Scale")
@ConditionalOnProperty(name = "possum.device.scale.enabled", havingValue = "true", matchIfMissing = true)
public class ScaleController {

    private final ScaleManager scaleManager;
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleController.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getScaleServiceName(), "ScaleController", LOGGER);

    @Autowired
    public ScaleController(ScaleManager scaleManager) {
        if(scaleManager == null) {
            throw new IllegalArgumentException("scaleManager cannot be null");
        }
        this.scaleManager = scaleManager;
    }

    @Operation(description = "Retrieves current weight from scale.  For informational purposes only - DO NOT use for selling.")
    @GetMapping(path = "/liveweight", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "NEEDS_ZEROING, WEIGHT_UNDER_ZERO",
                    content = @Content(schema = @Schema(implementation = ScaleError.class))),
            @ApiResponse(responseCode = "500", description = "UNEXPECTED_ERROR",
                    content = @Content(schema = @Schema(implementation = DeviceError.class))),
            @ApiResponse(responseCode = "404", description = "DEVICE_OFFLINE",
                    content = @Content(schema = @Schema(implementation = DeviceError.class))),
            @ApiResponse(responseCode = "409", description = "DEVICE_BUSY",
                    content = @Content(schema = @Schema(implementation = DeviceError.class)))
    })
    public SseEmitter getLiveWeight() throws IOException {
        String url = "/v1/scale/liveweight";
        log.successAPI("request", 1, url, null, 0);
        SseEmitter sseEmitter = new SseEmitter(Long.MAX_VALUE);
        try {
            scaleManager.subscribeToLiveWeight(sseEmitter);
            log.successAPI("response", 1, url, null, 200);
            return sseEmitter;
        } catch (IOException ioException) {
            log.failureAPI("response", 13, url, ioException.getMessage(), 0, ioException);
            throw ioException;
        }
    }

    @Operation(description = "Retrieves stable weight from scale.  Use for selling weighted items.")
    @GetMapping(path = "/stableweight")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "408", description = "TIMEOUT",
                    content = @Content(schema = @Schema(implementation = ScaleError.class))),
            @ApiResponse(responseCode = "400", description = "NEEDS_ZEROING, WEIGHT_UNDER_ZERO",
                    content = @Content(schema = @Schema(implementation = ScaleError.class))),
            @ApiResponse(responseCode = "500", description = "UNEXPECTED_ERROR",
                    content = @Content(schema = @Schema(implementation = DeviceError.class))),
            @ApiResponse(responseCode = "404", description = "DEVICE_OFFLINE",
                    content = @Content(schema = @Schema(implementation = DeviceError.class))),
            @ApiResponse(responseCode = "409", description = "DEVICE_BUSY",
                    content = @Content(schema = @Schema(implementation = DeviceError.class)))
    })
    public FormattedWeight getStableWeight() throws ScaleException {
        long randomWithTS = System.currentTimeMillis();
        String url = "/v1/scale/stableweight";
        log.successAPI("request " + randomWithTS, 1, url, null, 0);
        CompletableFuture<FormattedWeight> completableFuture = new CompletableFuture<>();
        try {
            FormattedWeight weight = scaleManager.getStableWeight(completableFuture);
            log.successAPI("response " + randomWithTS, 1, url, null, 200);
            return weight;
        } catch (ScaleException scaleException) {
            int statusCode = scaleException.getDeviceError() == null ? 0 : scaleException.getDeviceError().getStatusCode().value();
            String body = scaleException.getDeviceError() == null ? null : scaleException.getDeviceError().toString();
            log.failureAPI("response " + randomWithTS, 13, url, body, statusCode, scaleException);
            throw scaleException;
        }
    }

    @Operation(description = "Reports scale health")
    @GetMapping(path = "/scale/health")
    public DeviceHealthResponse getHealth() {
        String url = "/v1/scale/health";
        log.successAPI("request", 1, url, null, 0);
        DeviceHealthResponse response = scaleManager.getHealth();
        log.successAPI("response", 1, url, response.toString(), 200);
        return response;
    }

    @Operation(description = "Reports scale status")
    @GetMapping(path = "/scale/healthstatus")
    public DeviceHealthResponse getStatus() {
        String url = "/v1/scale/healthstatus";
        log.successAPI("request", 1, url, null, 0);
        DeviceHealthResponse response = scaleManager.getStatus();
        log.successAPI("response", 1, url, response.toString(), 200);
        return response;
    }

    @Operation(description = "Reconnects scanner by disconnecting, then connecting")
    @PostMapping(path = "/scale/reconnect")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "DEVICE_OFFLINE",
                    content = @Content(schema = @Schema(implementation = DeviceError.class))),
            @ApiResponse(responseCode = "409", description = "DEVICE_BUSY",
                    content = @Content(schema = @Schema(implementation = DeviceError.class)))
    })
    public void reconnect() throws DeviceException {
        String url = "/v1/scale/reconnect";
        log.successAPI("request", 1, url, null, 0);
        try {
            scaleManager.reconnectDevice();
            log.successAPI("response", 1, url, null, 200);
        } catch (DeviceException deviceException) {
            int statusCode = deviceException.getDeviceError() == null ? 0 : deviceException.getDeviceError().getStatusCode().value();
            String body = deviceException.getDeviceError() == null ? null : deviceException.getDeviceError().toString();
            log.failureAPI("response", 13, url, body, statusCode, deviceException);
            throw deviceException;
        }
    }

    // --- Step 5e: Lifecycle endpoints ---

    @Operation(description = "JPOS open — establish connection to scale")
    @PostMapping("/scale/lifecycle/open")
    public DeviceLifecycleResponse lifecycleOpen(@RequestParam String logicalName) throws JposException {
        String url = "/v1/scale/lifecycle/open";
        log.successAPI("request", 1, url, logicalName, 0);
        scaleManager.openDevice(logicalName);
        return scaleManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS claim — exclusive access to scale")
    @PostMapping("/scale/lifecycle/claim")
    public DeviceLifecycleResponse lifecycleClaim(@RequestParam(defaultValue = "30000") int timeout) throws JposException {
        String url = "/v1/scale/lifecycle/claim";
        log.successAPI("request", 1, url, null, 0);
        scaleManager.claimDevice(timeout);
        return scaleManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS enable — enable scale for operations")
    @PostMapping("/scale/lifecycle/enable")
    public DeviceLifecycleResponse lifecycleEnable() throws JposException {
        String url = "/v1/scale/lifecycle/enable";
        log.successAPI("request", 1, url, null, 0);
        scaleManager.enableDevice();
        return scaleManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS disable — disable scale")
    @PostMapping("/scale/lifecycle/disable")
    public DeviceLifecycleResponse lifecycleDisable() throws JposException {
        String url = "/v1/scale/lifecycle/disable";
        log.successAPI("request", 1, url, null, 0);
        scaleManager.disableDevice();
        return scaleManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS release — release exclusive access")
    @PostMapping("/scale/lifecycle/release")
    public DeviceLifecycleResponse lifecycleRelease() throws JposException {
        String url = "/v1/scale/lifecycle/release";
        log.successAPI("request", 1, url, null, 0);
        scaleManager.releaseDevice();
        return scaleManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS close — close connection to scale")
    @PostMapping("/scale/lifecycle/close")
    public DeviceLifecycleResponse lifecycleClose() throws JposException {
        String url = "/v1/scale/lifecycle/close";
        log.successAPI("request", 1, url, null, 0);
        scaleManager.closeDevice();
        return scaleManager.getLifecycleStatus();
    }

    @Operation(description = "Get current JPOS lifecycle state")
    @GetMapping("/scale/lifecycle")
    public DeviceLifecycleResponse getLifecycleStatus() {
        String url = "/v1/scale/lifecycle";
        log.successAPI("request", 1, url, null, 0);
        return scaleManager.getLifecycleStatus();
    }

    @Operation(description = "Switch back to automatic reconnect mode")
    @PostMapping("/scale/lifecycle/auto")
    public DeviceLifecycleResponse lifecycleAuto() {
        String url = "/v1/scale/lifecycle/auto";
        log.successAPI("request", 1, url, null, 0);
        scaleManager.setAutoMode();
        return scaleManager.getLifecycleStatus();
    }
}
