package com.target.devicemanager.components.keyboard;

import com.target.devicemanager.common.DeviceLifecycleResponse;
import com.target.devicemanager.common.StructuredEventLogger;
import com.target.devicemanager.common.entities.*;
import com.target.devicemanager.components.keyboard.entities.KeyEvent;
import jpos.JposException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;

@EnableScheduling
@EnableCaching
public class KeyboardManager {

    @Autowired
    private CacheManager cacheManager;

    private final KeyboardDevice keyboardDevice;
    private final Lock keyboardLock;
    private ConnectEnum connectStatus = ConnectEnum.FIRST_CONNECT;
    private boolean manualMode = false;
    private final List<SseEmitter> eventSubscribers = new CopyOnWriteArrayList<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(KeyboardManager.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of("keyboard", "KeyboardManager", LOGGER);

    public KeyboardManager(KeyboardDevice keyboardDevice, Lock keyboardLock) {
        this(keyboardDevice, keyboardLock, null);
    }

    public KeyboardManager(KeyboardDevice keyboardDevice, Lock keyboardLock, CacheManager cacheManager) {
        if (keyboardDevice == null) {
            throw new IllegalArgumentException("keyboardDevice cannot be null");
        }
        if (keyboardLock == null) {
            throw new IllegalArgumentException("keyboardLock cannot be null");
        }
        this.keyboardDevice = keyboardDevice;
        this.keyboardLock = keyboardLock;

        if (cacheManager != null) {
            this.cacheManager = cacheManager;
        }

        // Register this manager as the event callback on the device
        this.keyboardDevice.setEventCallback(this::onKeyEvent);
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void connect() {
        if (manualMode) {
            return;
        }

        if (keyboardDevice.tryLock()) {
            try {
                keyboardDevice.connect();
            } finally {
                keyboardDevice.unlock();
            }
        }

        if (connectStatus == ConnectEnum.FIRST_CONNECT) {
            connectStatus = ConnectEnum.CHECK_HEALTH;
        }
    }

    public void reconnectDevice() throws DeviceException {
        if (keyboardDevice.tryLock()) {
            try {
                keyboardDevice.disconnect();
                if (!keyboardDevice.connect()) {
                    throw new DeviceException(DeviceError.DEVICE_OFFLINE);
                }
            } finally {
                keyboardDevice.unlock();
            }
        } else {
            throw new DeviceException(DeviceError.DEVICE_BUSY);
        }
    }

    /**
     * Callback invoked by the device when a key event occurs.
     * Sends the event to all connected SSE subscribers.
     */
    private void onKeyEvent(KeyEvent event) {
        log.success("Key event received: " + event, 1);
        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : eventSubscribers) {
            try {
                emitter.send(event, MediaType.APPLICATION_JSON);
            } catch (IOException ioException) {
                deadEmitters.add(emitter);
            }
        }
        eventSubscribers.removeAll(deadEmitters);
    }

    /**
     * Adds an SSE emitter to the subscriber list for keyboard events.
     */
    public void addEventSubscriber(SseEmitter emitter) {
        emitter.onCompletion(() -> eventSubscribers.remove(emitter));
        emitter.onTimeout(() -> eventSubscribers.remove(emitter));
        eventSubscribers.add(emitter);
        log.success("SSE subscriber added, total: " + eventSubscribers.size(), 5);
    }

    public DeviceHealthResponse getHealth() {
        DeviceHealthResponse deviceHealthResponse;
        if (keyboardDevice.isConnected()) {
            deviceHealthResponse = new DeviceHealthResponse(keyboardDevice.getDeviceName(), DeviceHealth.READY);
        } else {
            deviceHealthResponse = new DeviceHealthResponse(keyboardDevice.getDeviceName(), DeviceHealth.NOTREADY);
        }
        try {
            Objects.requireNonNull(cacheManager.getCache("keyboardHealth")).put("health", deviceHealthResponse);
        } catch (Exception exception) {
            log.failure("getCache(keyboardHealth) Failed", 17, exception);
        }
        return deviceHealthResponse;
    }

    public DeviceHealthResponse getStatus() {
        try {
            if (cacheManager != null && Objects.requireNonNull(cacheManager.getCache("keyboardHealth")).get("health") != null) {
                if (connectStatus == ConnectEnum.CHECK_HEALTH) {
                    connectStatus = ConnectEnum.HEALTH_UPDATED;
                    return getHealth();
                }
                return (DeviceHealthResponse) Objects.requireNonNull(cacheManager.getCache("keyboardHealth")).get("health").get();
            } else {
                log.success("Not able to retrieve from cache, checking getHealth()", 5);
                return getHealth();
            }
        } catch (Exception exception) {
            return getHealth();
        }
    }

    // --- Lifecycle methods ---

    public void openDevice(String logicalName) throws JposException {
        manualMode = true;
        keyboardDevice.getDynamicDevice().openDevice(logicalName);
        log.logDeviceEvent("lifecycle_open", "Keyboard", logicalName);
    }

    public void claimDevice(int timeout) throws JposException {
        manualMode = true;
        keyboardDevice.getDynamicDevice().claimDevice(timeout);
        log.logDeviceEvent("lifecycle_claim", "Keyboard", keyboardDevice.getDeviceName());
    }

    public void enableDevice() throws JposException {
        manualMode = true;
        keyboardDevice.getDynamicDevice().enableDevice();
        log.logDeviceEvent("lifecycle_enable", "Keyboard", keyboardDevice.getDeviceName());
    }

    public void disableDevice() throws JposException {
        manualMode = true;
        keyboardDevice.getDynamicDevice().disableDevice();
        log.logDeviceEvent("lifecycle_disable", "Keyboard", keyboardDevice.getDeviceName());
    }

    public void releaseDevice() throws JposException {
        manualMode = true;
        keyboardDevice.getDynamicDevice().releaseDevice();
        log.logDeviceEvent("lifecycle_release", "Keyboard", keyboardDevice.getDeviceName());
    }

    public void closeDevice() throws JposException {
        manualMode = true;
        keyboardDevice.getDynamicDevice().closeDevice();
        log.logDeviceEvent("lifecycle_close", "Keyboard", keyboardDevice.getDeviceName());
    }

    public void setAutoMode() {
        manualMode = false;
        log.logDeviceEvent("lifecycle_auto", "Keyboard", keyboardDevice.getDeviceName());
    }

    public void setManualMode(boolean manual) {
        manualMode = manual;
    }

    public DeviceLifecycleResponse getLifecycleStatus() {
        return new DeviceLifecycleResponse(
                keyboardDevice.getDynamicDevice().getLifecycleState(),
                keyboardDevice.getDeviceName(),
                manualMode,
                "Keyboard"
        );
    }

    public boolean isManualMode() {
        return manualMode;
    }
}
