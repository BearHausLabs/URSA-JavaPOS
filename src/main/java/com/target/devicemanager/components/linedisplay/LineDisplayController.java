package com.target.devicemanager.components.linedisplay;

import com.target.devicemanager.common.DeviceLifecycleResponse;
import com.target.devicemanager.common.StructuredEventLogger;
import com.target.devicemanager.common.entities.DeviceError;
import com.target.devicemanager.common.entities.DeviceException;
import com.target.devicemanager.common.entities.DeviceHealthResponse;
import com.target.devicemanager.components.linedisplay.entities.LineDisplayData;
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
@RequestMapping(value = "/v1/linedisplay")
@Tag(name = "Line Display (2x20)")
@Profile({"local","dev","prod"})
@ConditionalOnProperty(name = "possum.device.linedisplay.enabled", havingValue = "true", matchIfMissing = true)
public class LineDisplayController {

    private final LineDisplayManager lineDisplayManager;
    private static final Logger LOGGER = LoggerFactory.getLogger(LineDisplayController.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getLineDisplayServiceName(), "LineDisplayController", LOGGER);
    @Autowired
    public LineDisplayController(LineDisplayManager lineDisplayManager) {
        if (lineDisplayManager == null) {
            throw new IllegalArgumentException("lineDisplayManager cannot be null");
        }
        this.lineDisplayManager = lineDisplayManager;
    }

    @Operation(description = "Displays text on 2x20.  To clear out a line, omit it from the request.")
    @PostMapping(path = "/display")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "500", description = "UNEXPECTED_ERROR", content = @Content(schema = @Schema(implementation = DeviceError.class))),
            @ApiResponse(responseCode = "404", description = "DEVICE_OFFLINE", content = @Content(schema = @Schema(implementation = DeviceError.class))),
            @ApiResponse(responseCode = "409", description = "DEVICE_BUSY", content = @Content(schema = @Schema(implementation = DeviceError.class)))
    })
    public void displayLines(@RequestBody LineDisplayData data) throws DeviceException {
        String url = "/v1/linedisplay/display";
        log.successAPI("request", 1, url, null, 0);
        try {
            lineDisplayManager.displayLine(data.line1, data.line2);
            log.successAPI("response", 1, url, null, 200);
        } catch (DeviceException deviceException) {
            int statusCode = deviceException.getDeviceError().getStatusCode().value();
            log.failureAPI("response", 13, url, deviceException.getDeviceError().toString(), statusCode, deviceException);
            throw deviceException;
        }
    }

    @Operation(description = "Reports linedisplay health")
    @GetMapping(path = "/health")
    public DeviceHealthResponse getHealth() {
        String url = "/v1/linedisplay/health";
        log.successAPI("request", 1, url, null, 0);
        DeviceHealthResponse response = lineDisplayManager.getHealth();
        log.successAPI("response", 1, url, response.toString(), 200);
        return response;
    }

    @Operation(description = "Reports linedisplay status")
    @GetMapping(path = "/healthstatus")
    public DeviceHealthResponse getStatus() {
        String url = "/v1/linedisplay/healthstatus";
        log.successAPI("request", 1, url, null, 0);
        DeviceHealthResponse response = lineDisplayManager.getStatus();
        log.successAPI("response", 1, url, response.toString(), 200);
        return response;
    }

    @Operation(description = "Reconnects the line display device by releasing, then connecting.")
    @PostMapping(path = "/reconnect")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "DEVICE_OFFLINE", content = @Content(schema = @Schema(implementation = DeviceError.class))),
            @ApiResponse(responseCode = "409", description = "DEVICE_BUSY", content = @Content(schema = @Schema(implementation = DeviceError.class)))
    })
    public void reconnect() throws DeviceException {
        String url = "/v1/linedisplay/reconnect";
        log.successAPI("request", 1, url, null, 0);
        try {
            lineDisplayManager.reconnectDevice();
            log.successAPI("response", 1, url, null, 200);
        } catch (DeviceException deviceException) {
            int statusCode = deviceException.getDeviceError().getStatusCode().value();
            log.failureAPI("response", 13, url, deviceException.getDeviceError().toString(), statusCode, deviceException);
            throw deviceException;
        }
    }

    // --- Step 5e: Lifecycle endpoints ---

    @Operation(description = "JPOS open — establish connection to line display")
    @PostMapping("/lifecycle/open")
    public DeviceLifecycleResponse lifecycleOpen(@RequestParam String logicalName) throws JposException {
        String url = "/v1/linedisplay/lifecycle/open";
        log.successAPI("request", 1, url, logicalName, 0);
        lineDisplayManager.openDevice(logicalName);
        return lineDisplayManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS claim — exclusive access to line display")
    @PostMapping("/lifecycle/claim")
    public DeviceLifecycleResponse lifecycleClaim(@RequestParam(defaultValue = "30000") int timeout) throws JposException {
        String url = "/v1/linedisplay/lifecycle/claim";
        log.successAPI("request", 1, url, null, 0);
        lineDisplayManager.claimDevice(timeout);
        return lineDisplayManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS enable — enable line display for operations")
    @PostMapping("/lifecycle/enable")
    public DeviceLifecycleResponse lifecycleEnable() throws JposException {
        String url = "/v1/linedisplay/lifecycle/enable";
        log.successAPI("request", 1, url, null, 0);
        lineDisplayManager.enableDevice();
        return lineDisplayManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS disable — disable line display")
    @PostMapping("/lifecycle/disable")
    public DeviceLifecycleResponse lifecycleDisable() throws JposException {
        String url = "/v1/linedisplay/lifecycle/disable";
        log.successAPI("request", 1, url, null, 0);
        lineDisplayManager.disableDevice();
        return lineDisplayManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS release — release exclusive access")
    @PostMapping("/lifecycle/release")
    public DeviceLifecycleResponse lifecycleRelease() throws JposException {
        String url = "/v1/linedisplay/lifecycle/release";
        log.successAPI("request", 1, url, null, 0);
        lineDisplayManager.releaseDevice();
        return lineDisplayManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS close — close connection to line display")
    @PostMapping("/lifecycle/close")
    public DeviceLifecycleResponse lifecycleClose() throws JposException {
        String url = "/v1/linedisplay/lifecycle/close";
        log.successAPI("request", 1, url, null, 0);
        lineDisplayManager.closeDevice();
        return lineDisplayManager.getLifecycleStatus();
    }

    @Operation(description = "Get current JPOS lifecycle state")
    @GetMapping("/lifecycle")
    public DeviceLifecycleResponse getLifecycleStatus() {
        String url = "/v1/linedisplay/lifecycle";
        log.successAPI("request", 1, url, null, 0);
        return lineDisplayManager.getLifecycleStatus();
    }

    @Operation(description = "Switch back to automatic reconnect mode")
    @PostMapping("/lifecycle/auto")
    public DeviceLifecycleResponse lifecycleAuto() {
        String url = "/v1/linedisplay/lifecycle/auto";
        log.successAPI("request", 1, url, null, 0);
        lineDisplayManager.setAutoMode();
        return lineDisplayManager.getLifecycleStatus();
    }

}
