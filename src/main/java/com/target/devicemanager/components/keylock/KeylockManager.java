package com.target.devicemanager.components.keylock;

import com.target.devicemanager.common.DeviceLifecycleResponse;
import com.target.devicemanager.common.StructuredEventLogger;
import com.target.devicemanager.common.entities.*;
import com.target.devicemanager.components.keylock.entities.KeyPosition;
import jpos.JposException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;

@EnableCaching
public class KeylockManager {

    @Autowired
    private CacheManager cacheManager;

    private final KeylockDevice keylockDevice;
    private final Lock keylockLock;
    private final List<SseEmitter> eventSubscribers = new CopyOnWriteArrayList<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(KeylockManager.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of("keylock", "KeylockManager", LOGGER);

    public KeylockManager(KeylockDevice keylockDevice, Lock keylockLock) {
        this(keylockDevice, keylockLock, null);
    }

    public KeylockManager(KeylockDevice keylockDevice, Lock keylockLock, CacheManager cacheManager) {
        if (keylockDevice == null) {
            throw new IllegalArgumentException("keylockDevice cannot be null");
        }
        if (keylockLock == null) {
            throw new IllegalArgumentException("keylockLock cannot be null");
        }
        this.keylockDevice = keylockDevice;
        this.keylockLock = keylockLock;

        if (cacheManager != null) {
            this.cacheManager = cacheManager;
        }

        // Register callback for real-time position change SSE broadcasting
        this.keylockDevice.setPositionChangeCallback(this::onPositionChange);
    }

    public void reconnectDevice() throws DeviceException {
        if (keylockDevice.tryLock()) {
            try {
                keylockDevice.disconnect();
            } finally {
                keylockDevice.unlock();
            }
        } else {
            throw new DeviceException(DeviceError.DEVICE_BUSY);
        }
    }

    public KeyPosition getKeyPosition() throws DeviceException {
        if (!keylockLock.tryLock()) {
            throw new DeviceException(DeviceError.DEVICE_BUSY);
        }
        try {
            if (!keylockDevice.isConnected()) {
                throw new DeviceException(DeviceError.DEVICE_OFFLINE);
            }
            return keylockDevice.getKeyPosition();
        } finally {
            keylockLock.unlock();
        }
    }

    public DeviceHealthResponse getHealth() {
        DeviceHealthResponse deviceHealthResponse;
        if (keylockDevice.isConnected()) {
            deviceHealthResponse = new DeviceHealthResponse(keylockDevice.getDeviceName(), DeviceHealth.READY);
        } else {
            deviceHealthResponse = new DeviceHealthResponse(keylockDevice.getDeviceName(), DeviceHealth.NOTREADY);
        }
        try {
            Objects.requireNonNull(cacheManager.getCache("keylockHealth")).put("health", deviceHealthResponse);
        } catch (Exception exception) {
            log.failure("getCache(keylockHealth) Failed", 17, exception);
        }
        return deviceHealthResponse;
    }

    public DeviceHealthResponse getStatus() {
        try {
            if (cacheManager != null && Objects.requireNonNull(cacheManager.getCache("keylockHealth")).get("health") != null) {
                return (DeviceHealthResponse) Objects.requireNonNull(cacheManager.getCache("keylockHealth")).get("health").get();
            } else {
                log.success("Not able to retrieve from cache, checking getHealth()", 5);
                return getHealth();
            }
        } catch (Exception exception) {
            return getHealth();
        }
    }

    /**
     * Callback invoked by KeylockDevice when the keylock position changes.
     * Broadcasts the new position to all SSE subscribers.
     */
    private void onPositionChange(KeyPosition position) {
        log.success("Position change event: " + position + ", broadcasting to " + eventSubscribers.size() + " subscribers", 1);
        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : eventSubscribers) {
            try {
                emitter.send(SseEmitter.event()
                        .name("position")
                        .data("{\"position\":\"" + position.name() + "\"}", MediaType.APPLICATION_JSON));
            } catch (IOException ioException) {
                deadEmitters.add(emitter);
            }
        }
        eventSubscribers.removeAll(deadEmitters);
    }

    /**
     * Adds an SSE emitter to the subscriber list for keylock position events.
     * Sends the current position immediately upon subscription.
     */
    public void addEventSubscriber(SseEmitter emitter) {
        emitter.onCompletion(() -> eventSubscribers.remove(emitter));
        emitter.onTimeout(() -> eventSubscribers.remove(emitter));
        eventSubscribers.add(emitter);
        log.success("SSE subscriber added, total: " + eventSubscribers.size(), 5);

        // Send current position immediately so the client gets initial state
        try {
            KeyPosition currentPos = keylockDevice.getKeyPosition();
            emitter.send(SseEmitter.event()
                    .name("position")
                    .data("{\"position\":\"" + currentPos.name() + "\"}", MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            log.failure("Failed to send initial position to SSE subscriber", 5, e);
        }
    }

    // --- Lifecycle methods ---

    public void openDevice(String logicalName) throws JposException {
        keylockDevice.getDynamicDevice().openDevice(logicalName);
        log.logDeviceEvent("lifecycle_open", "Keylock", logicalName);
    }

    public void claimDevice(int timeout) throws JposException {
        keylockDevice.getDynamicDevice().claimDevice(timeout);
        log.logDeviceEvent("lifecycle_claim", "Keylock", keylockDevice.getDeviceName());
    }

    public void enableDevice() throws JposException {
        keylockDevice.getDynamicDevice().enableDevice();
        log.logDeviceEvent("lifecycle_enable", "Keylock", keylockDevice.getDeviceName());
    }

    public void disableDevice() throws JposException {
        keylockDevice.getDynamicDevice().disableDevice();
        log.logDeviceEvent("lifecycle_disable", "Keylock", keylockDevice.getDeviceName());
    }

    public void releaseDevice() throws JposException {
        keylockDevice.getDynamicDevice().releaseDevice();
        log.logDeviceEvent("lifecycle_release", "Keylock", keylockDevice.getDeviceName());
    }

    public void closeDevice() throws JposException {
        keylockDevice.getDynamicDevice().closeDevice();
        log.logDeviceEvent("lifecycle_close", "Keylock", keylockDevice.getDeviceName());
    }

    public void setAutoMode() {
        // No-op: URSA always owns device lifecycle.
        log.logDeviceEvent("lifecycle_auto_noop", "Keylock", keylockDevice.getDeviceName());
    }

    public void setManualMode(boolean manual) {
        // No-op: always in manual mode.
    }

    public DeviceLifecycleResponse getLifecycleStatus() {
        return new DeviceLifecycleResponse(
                keylockDevice.getDynamicDevice().getLifecycleState(),
                keylockDevice.getDeviceName(),
                "Keylock"
        );
    }

    public boolean isManualMode() {
        return true;
    }
}
