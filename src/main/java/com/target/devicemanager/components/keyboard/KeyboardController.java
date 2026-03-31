package com.target.devicemanager.components.keyboard;

import com.target.devicemanager.common.DeviceLifecycleResponse;
import com.target.devicemanager.common.StructuredEventLogger;
import com.target.devicemanager.common.entities.DeviceError;
import com.target.devicemanager.common.entities.DeviceException;
import com.target.devicemanager.common.entities.DeviceHealthResponse;
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
@RequestMapping("/v1/keyboard")
@Tag(name = "Keyboard")
@ConditionalOnProperty(name = "possum.device.keyboard.enabled", havingValue = "true", matchIfMissing = true)
public class KeyboardController {

    private final KeyboardManager keyboardManager;
    private static final Logger LOGGER = LoggerFactory.getLogger(KeyboardController.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of("keyboard", "KeyboardController", LOGGER);

    @Autowired
    public KeyboardController(KeyboardManager keyboardManager) {
        if (keyboardManager == null) {
            throw new IllegalArgumentException("keyboardManager cannot be null");
        }
        this.keyboardManager = keyboardManager;
    }

    @Operation(description = "Subscribe to real-time keyboard events via Server-Sent Events (SSE). " +
            "Events are pushed as JSON objects containing keyCode, eventType (KEY_DOWN/KEY_UP), and timestamp.")
    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE stream opened")
    })
    public SseEmitter subscribeToKeyboardEvents() {
        String url = "/v1/keyboard/events";
        log.successAPI("request", 1, url, null, 0);
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        keyboardManager.addEventSubscriber(emitter);
        log.successAPI("response", 1, url, "SSE stream opened", 200);
        return emitter;
    }

    @Operation(description = "Reports POS keyboard health")
    @GetMapping("/health")
    public DeviceHealthResponse getHealth() {
        String url = "/v1/keyboard/health";
        log.successAPI("request", 1, url, null, 0);
        DeviceHealthResponse response = keyboardManager.getHealth();
        log.successAPI("response", 1, url, response.toString(), 200);
        return response;
    }

    @Operation(description = "Reports POS keyboard status")
    @GetMapping("/healthstatus")
    public DeviceHealthResponse getStatus() {
        String url = "/v1/keyboard/healthstatus";
        log.successAPI("request", 1, url, null, 0);
        DeviceHealthResponse response = keyboardManager.getStatus();
        log.successAPI("response", 1, url, response.toString(), 200);
        return response;
    }

    // --- Lifecycle endpoints ---

    @Operation(description = "JPOS open - establish connection to keyboard")
    @PostMapping("/lifecycle/open")
    public DeviceLifecycleResponse lifecycleOpen(@RequestParam String logicalName) throws JposException {
        String url = "/v1/keyboard/lifecycle/open";
        log.successAPI("request", 1, url, logicalName, 0);
        keyboardManager.openDevice(logicalName);
        return keyboardManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS claim - exclusive access to keyboard")
    @PostMapping("/lifecycle/claim")
    public DeviceLifecycleResponse lifecycleClaim(@RequestParam(defaultValue = "30000") int timeout) throws JposException {
        String url = "/v1/keyboard/lifecycle/claim";
        log.successAPI("request", 1, url, null, 0);
        keyboardManager.claimDevice(timeout);
        return keyboardManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS enable - enable keyboard for operations")
    @PostMapping("/lifecycle/enable")
    public DeviceLifecycleResponse lifecycleEnable() throws JposException {
        String url = "/v1/keyboard/lifecycle/enable";
        log.successAPI("request", 1, url, null, 0);
        keyboardManager.enableDevice();
        return keyboardManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS disable - disable keyboard")
    @PostMapping("/lifecycle/disable")
    public DeviceLifecycleResponse lifecycleDisable() throws JposException {
        String url = "/v1/keyboard/lifecycle/disable";
        log.successAPI("request", 1, url, null, 0);
        keyboardManager.disableDevice();
        return keyboardManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS release - release exclusive access")
    @PostMapping("/lifecycle/release")
    public DeviceLifecycleResponse lifecycleRelease() throws JposException {
        String url = "/v1/keyboard/lifecycle/release";
        log.successAPI("request", 1, url, null, 0);
        keyboardManager.releaseDevice();
        return keyboardManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS close - close connection to keyboard")
    @PostMapping("/lifecycle/close")
    public DeviceLifecycleResponse lifecycleClose() throws JposException {
        String url = "/v1/keyboard/lifecycle/close";
        log.successAPI("request", 1, url, null, 0);
        keyboardManager.closeDevice();
        return keyboardManager.getLifecycleStatus();
    }

    @Operation(description = "Get current JPOS lifecycle state")
    @GetMapping("/lifecycle")
    public DeviceLifecycleResponse getLifecycleStatus() {
        String url = "/v1/keyboard/lifecycle";
        log.successAPI("request", 1, url, null, 0);
        return keyboardManager.getLifecycleStatus();
    }

}
