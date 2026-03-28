package com.target.devicemanager.components.msr;

import com.target.devicemanager.common.DeviceLifecycleResponse;
import com.target.devicemanager.common.StructuredEventLogger;
import com.target.devicemanager.common.entities.*;
import com.target.devicemanager.components.msr.entities.MsrData;
import com.target.devicemanager.components.msr.entities.MsrError;
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
public class MsrManager {

    @Autowired
    private CacheManager cacheManager;

    private final MsrDevice msrDevice;
    private final Lock msrLock;
    private ConnectEnum connectStatus = ConnectEnum.FIRST_CONNECT;
    private boolean manualMode = false;
    private static final Logger LOGGER = LoggerFactory.getLogger(MsrManager.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getMsrServiceName(), "MsrManager", LOGGER);

    public MsrManager(MsrDevice msrDevice, Lock msrLock) {
        this(msrDevice, msrLock, null);
    }

    public MsrManager(MsrDevice msrDevice, Lock msrLock, CacheManager cacheManager) {
        if (msrDevice == null) {
            throw new IllegalArgumentException("msrDevice cannot be null");
        }
        if (msrLock == null) {
            throw new IllegalArgumentException("msrLock cannot be null");
        }
        this.msrDevice = msrDevice;
        this.msrLock = msrLock;

        if (cacheManager != null) {
            this.cacheManager = cacheManager;
        }
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void connect() {
        if (manualMode) {
            return;
        }

        if (msrDevice.tryLock()) {
            try {
                msrDevice.connect();
            } finally {
                msrDevice.unlock();
            }
        }

        if (connectStatus == ConnectEnum.FIRST_CONNECT) {
            connectStatus = ConnectEnum.CHECK_HEALTH;
        }
    }

    public void reconnectDevice() throws DeviceException {
        if (msrDevice.tryLock()) {
            try {
                msrDevice.disconnect();
                if (!msrDevice.connect()) {
                    throw new DeviceException(DeviceError.DEVICE_OFFLINE);
                }
            } finally {
                msrDevice.unlock();
            }
        } else {
            throw new DeviceException(DeviceError.DEVICE_BUSY);
        }
    }

    public MsrData readCard() throws DeviceException {
        if (!msrLock.tryLock()) {
            throw new DeviceException(MsrError.DEVICE_BUSY);
        }
        try {
            return msrDevice.readCard();
        } catch (JposException jposException) {
            throw new DeviceException(jposException);
        } finally {
            msrLock.unlock();
        }
    }

    public void cancelRead() {
        msrDevice.cancelRead();
    }

    public DeviceHealthResponse getHealth() {
        DeviceHealthResponse deviceHealthResponse;
        if (msrDevice.isConnected()) {
            deviceHealthResponse = new DeviceHealthResponse(msrDevice.getDeviceName(), DeviceHealth.READY);
        } else {
            deviceHealthResponse = new DeviceHealthResponse(msrDevice.getDeviceName(), DeviceHealth.NOTREADY);
        }
        try {
            Objects.requireNonNull(cacheManager.getCache("msrHealth")).put("health", deviceHealthResponse);
        } catch (Exception exception) {
            log.failure("getCache(msrHealth) Failed", 17, exception);
        }
        return deviceHealthResponse;
    }

    public DeviceHealthResponse getStatus() {
        try {
            if (cacheManager != null && Objects.requireNonNull(cacheManager.getCache("msrHealth")).get("health") != null) {
                if (connectStatus == ConnectEnum.CHECK_HEALTH) {
                    connectStatus = ConnectEnum.HEALTH_UPDATED;
                    return getHealth();
                }
                return (DeviceHealthResponse) Objects.requireNonNull(cacheManager.getCache("msrHealth")).get("health").get();
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
        msrDevice.getDynamicDevice().openDevice(logicalName);
        log.logDeviceEvent("lifecycle_open", "MSR", logicalName);
    }

    public void claimDevice(int timeout) throws JposException {
        manualMode = true;
        msrDevice.getDynamicDevice().claimDevice(timeout);
        log.logDeviceEvent("lifecycle_claim", "MSR", msrDevice.getDeviceName());
    }

    public void enableDevice() throws JposException {
        manualMode = true;
        msrDevice.getDynamicDevice().enableDevice();
        log.logDeviceEvent("lifecycle_enable", "MSR", msrDevice.getDeviceName());
    }

    public void disableDevice() throws JposException {
        manualMode = true;
        msrDevice.getDynamicDevice().disableDevice();
        log.logDeviceEvent("lifecycle_disable", "MSR", msrDevice.getDeviceName());
    }

    public void releaseDevice() throws JposException {
        manualMode = true;
        msrDevice.getDynamicDevice().releaseDevice();
        log.logDeviceEvent("lifecycle_release", "MSR", msrDevice.getDeviceName());
    }

    public void closeDevice() throws JposException {
        manualMode = true;
        msrDevice.getDynamicDevice().closeDevice();
        log.logDeviceEvent("lifecycle_close", "MSR", msrDevice.getDeviceName());
    }

    public void setAutoMode() {
        manualMode = false;
        log.logDeviceEvent("lifecycle_auto", "MSR", msrDevice.getDeviceName());
    }

    public void setManualMode(boolean manual) {
        manualMode = manual;
    }

    public DeviceLifecycleResponse getLifecycleStatus() {
        return new DeviceLifecycleResponse(
                msrDevice.getDynamicDevice().getLifecycleState(),
                msrDevice.getDeviceName(),
                manualMode,
                "MSR"
        );
    }

    public boolean isManualMode() {
        return manualMode;
    }
}
