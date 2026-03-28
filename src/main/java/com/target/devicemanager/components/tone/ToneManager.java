package com.target.devicemanager.components.tone;

import com.target.devicemanager.common.DeviceLifecycleResponse;
import com.target.devicemanager.common.StructuredEventLogger;
import com.target.devicemanager.common.entities.*;
import com.target.devicemanager.components.tone.entities.ToneRequest;
import jpos.JposException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;
import java.util.concurrent.locks.Lock;

@EnableScheduling
@EnableCaching
public class ToneManager {

    @Autowired
    private CacheManager cacheManager;

    private final ToneDevice toneDevice;
    private final Lock toneLock;
    private ConnectEnum connectStatus = ConnectEnum.FIRST_CONNECT;
    private boolean manualMode = false;
    private static final Logger LOGGER = LoggerFactory.getLogger(ToneManager.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getToneIndicatorServiceName(), "ToneManager", LOGGER);

    public ToneManager(ToneDevice toneDevice, Lock toneLock) {
        this(toneDevice, toneLock, null);
    }

    public ToneManager(ToneDevice toneDevice, Lock toneLock, CacheManager cacheManager) {
        if (toneDevice == null) {
            throw new IllegalArgumentException("toneDevice cannot be null");
        }
        if (toneLock == null) {
            throw new IllegalArgumentException("toneLock cannot be null");
        }
        this.toneDevice = toneDevice;
        this.toneLock = toneLock;

        if (cacheManager != null) {
            this.cacheManager = cacheManager;
        }
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void connect() {
        if (manualMode) {
            return;
        }

        if (toneDevice.tryLock()) {
            try {
                toneDevice.connect();
            } finally {
                toneDevice.unlock();
            }
        }

        if (connectStatus == ConnectEnum.FIRST_CONNECT) {
            connectStatus = ConnectEnum.CHECK_HEALTH;
        }
    }

    public void reconnectDevice() throws DeviceException {
        if (toneDevice.tryLock()) {
            try {
                toneDevice.disconnect();
                if (!toneDevice.connect()) {
                    throw new DeviceException(DeviceError.DEVICE_OFFLINE);
                }
            } finally {
                toneDevice.unlock();
            }
        } else {
            throw new DeviceException(DeviceError.DEVICE_BUSY);
        }
    }

    public void playTone(ToneRequest request) throws DeviceException {
        if (!toneLock.tryLock()) {
            throw new DeviceException(DeviceError.DEVICE_BUSY);
        }
        try {
            toneDevice.playTone(request);
        } catch (JposException jposException) {
            throw new DeviceException(jposException);
        } finally {
            toneLock.unlock();
        }
    }

    public void beep() throws DeviceException {
        if (!toneLock.tryLock()) {
            throw new DeviceException(DeviceError.DEVICE_BUSY);
        }
        try {
            toneDevice.beep();
        } catch (JposException jposException) {
            throw new DeviceException(jposException);
        } finally {
            toneLock.unlock();
        }
    }

    public DeviceHealthResponse getHealth() {
        DeviceHealthResponse deviceHealthResponse;
        if (toneDevice.isConnected()) {
            deviceHealthResponse = new DeviceHealthResponse(toneDevice.getDeviceName(), DeviceHealth.READY);
        } else {
            deviceHealthResponse = new DeviceHealthResponse(toneDevice.getDeviceName(), DeviceHealth.NOTREADY);
        }
        try {
            Objects.requireNonNull(cacheManager.getCache("toneHealth")).put("health", deviceHealthResponse);
        } catch (Exception exception) {
            log.failure("getCache(toneHealth) Failed", 17, exception);
        }
        return deviceHealthResponse;
    }

    public DeviceHealthResponse getStatus() {
        try {
            if (cacheManager != null && Objects.requireNonNull(cacheManager.getCache("toneHealth")).get("health") != null) {
                if (connectStatus == ConnectEnum.CHECK_HEALTH) {
                    connectStatus = ConnectEnum.HEALTH_UPDATED;
                    return getHealth();
                }
                return (DeviceHealthResponse) Objects.requireNonNull(cacheManager.getCache("toneHealth")).get("health").get();
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
        toneDevice.getDynamicDevice().openDevice(logicalName);
        log.logDeviceEvent("lifecycle_open", "ToneIndicator", logicalName);
    }

    public void claimDevice(int timeout) throws JposException {
        manualMode = true;
        toneDevice.getDynamicDevice().claimDevice(timeout);
        log.logDeviceEvent("lifecycle_claim", "ToneIndicator", toneDevice.getDeviceName());
    }

    public void enableDevice() throws JposException {
        manualMode = true;
        toneDevice.getDynamicDevice().enableDevice();
        log.logDeviceEvent("lifecycle_enable", "ToneIndicator", toneDevice.getDeviceName());
    }

    public void disableDevice() throws JposException {
        manualMode = true;
        toneDevice.getDynamicDevice().disableDevice();
        log.logDeviceEvent("lifecycle_disable", "ToneIndicator", toneDevice.getDeviceName());
    }

    public void releaseDevice() throws JposException {
        manualMode = true;
        toneDevice.getDynamicDevice().releaseDevice();
        log.logDeviceEvent("lifecycle_release", "ToneIndicator", toneDevice.getDeviceName());
    }

    public void closeDevice() throws JposException {
        manualMode = true;
        toneDevice.getDynamicDevice().closeDevice();
        log.logDeviceEvent("lifecycle_close", "ToneIndicator", toneDevice.getDeviceName());
    }

    public void setAutoMode() {
        manualMode = false;
        log.logDeviceEvent("lifecycle_auto", "ToneIndicator", toneDevice.getDeviceName());
    }

    public void setManualMode(boolean manual) {
        manualMode = manual;
    }

    public DeviceLifecycleResponse getLifecycleStatus() {
        return new DeviceLifecycleResponse(
                toneDevice.getDynamicDevice().getLifecycleState(),
                toneDevice.getDeviceName(),
                manualMode,
                "ToneIndicator"
        );
    }

    public boolean isManualMode() {
        return manualMode;
    }
}
