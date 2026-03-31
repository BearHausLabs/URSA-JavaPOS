package com.target.devicemanager.components.scanner;

import com.target.devicemanager.common.DeviceLifecycleResponse;
import com.target.devicemanager.common.StructuredEventLogger;
import com.target.devicemanager.common.entities.*;
import com.target.devicemanager.components.scanner.entities.Barcode;
import com.target.devicemanager.components.scanner.entities.ScannerError;
import com.target.devicemanager.components.scanner.entities.ScannerException;
import jpos.JposException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;

import java.util.Objects;
import java.util.concurrent.locks.Lock;

@EnableCaching
public class ScannerManager {

    @Autowired
    private CacheManager cacheManager;

    private final ScannerDevice scanner;
    private final Lock scannerLock;
    private static final Logger LOGGER = LoggerFactory.getLogger(ScannerManager.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getScannerServiceName(), "ScannerManager", LOGGER);

    public ScannerManager(ScannerDevice scanner, Lock scannerLock) {
        this(scanner, scannerLock, null);
    }

    public ScannerManager(ScannerDevice scanner, Lock scannerLock, CacheManager cacheManager) {
        if (scanner == null) {
            throw new IllegalArgumentException("scanner cannot be null");
        }
        if (scannerLock == null) {
            throw new IllegalArgumentException("scannerLock cannot be null");
        }
        this.scanner = scanner;
        this.scannerLock = scannerLock;

        if (cacheManager != null) {
            this.cacheManager = cacheManager;
        }
    }

    Barcode getData() throws ScannerException {
        log.success("getData(in)", 1);
        if (!scannerLock.tryLock()) {
            ScannerException scannerException = new ScannerException(ScannerError.DEVICE_BUSY);
            log.success("getData(out) - device busy", 1);
            throw scannerException;
        }
        try {
            return scanner.getScannerData();
        } catch (Exception exception) {
            ScannerException scannerException;
            if (exception instanceof ScannerException) {
                throw (ScannerException) exception;
            } else if (exception instanceof jpos.JposException) {
                scannerException = new ScannerException((jpos.JposException) exception);
            } else {
                log.failure("Exception occurred in getData: " + exception.getMessage(), 17, exception);
                scannerException = new ScannerException(ScannerError.UNEXPECTED_ERROR);
            }
            throw scannerException;
        } finally {
            scannerLock.unlock();
            log.success("getData(out)", 1);
        }
    }

    void cancelScanRequest() throws ScannerException {
        log.success("cancelScanRequest(in)", 1);
        //This makes sure no new scan data requests come in while we are cancelling
        if (scannerLock.tryLock()) {
            //Nothing to disable
            try {
                ScannerException scannerException = new ScannerException(ScannerError.ALREADY_DISABLED);
                log.success("cancelScanRequest(out) - already disabled", 1);
                throw scannerException;
            } finally {
                scannerLock.unlock();
            }
        }
        try {
            scanner.cancelScannerData();
        } catch (Exception exception) {
            log.failure("Error in cancelScanRequest: " + exception.getMessage(), 17, exception);
        }
        log.success("cancelScanRequest(out)", 1);
    }

    public DeviceHealthResponse getHealth() {
        log.success("getHealth(in)", 1);
        DeviceHealthResponse response;
        if (scanner.isConnected()) {
            response = new DeviceHealthResponse(scanner.getDeviceName(), DeviceHealth.READY);
        } else {
            response = new DeviceHealthResponse(scanner.getDeviceName(), DeviceHealth.NOTREADY);
        }
        try {
            Objects.requireNonNull(cacheManager.getCache("scannerHealth")).put("health", response);
        } catch (Exception exception) {
            log.failure("getCache(scannerHealth) Failed: " + exception.getMessage(), 17, exception);
        }
        log.success("getHealth(out)", 1);
        return response;
    }

    public DeviceHealthResponse getStatus() {
        try {
            if (cacheManager != null && Objects.requireNonNull(cacheManager.getCache("scannerHealth")).get("health") != null) {
                return (DeviceHealthResponse) Objects.requireNonNull(cacheManager.getCache("scannerHealth")).get("health").get();
            } else {
                log.success("Not able to retrieve from cache, checking getHealth()", 6);
                return getHealth();
            }
        } catch (Exception exception) {
            return getHealth();
        }
    }

    // --- Lifecycle methods ---

    public void openDevice(String logicalName) throws JposException {
        scanner.getDynamicDevice().openDevice(logicalName);
        log.logDeviceEvent("lifecycle_open", "Scanner", logicalName);
    }

    public void claimDevice(int timeout) throws JposException {
        scanner.getDynamicDevice().claimDevice(timeout);
        log.logDeviceEvent("lifecycle_claim", "Scanner", scanner.getDeviceName());
    }

    public void enableDevice() throws JposException {
        scanner.getDynamicDevice().enableDevice();
        log.logDeviceEvent("lifecycle_enable", "Scanner", scanner.getDeviceName());
    }

    public void disableDevice() throws JposException {
        scanner.getDynamicDevice().disableDevice();
        log.logDeviceEvent("lifecycle_disable", "Scanner", scanner.getDeviceName());
    }

    public void releaseDevice() throws JposException {
        scanner.getDynamicDevice().releaseDevice();
        log.logDeviceEvent("lifecycle_release", "Scanner", scanner.getDeviceName());
    }

    public void closeDevice() throws JposException {
        scanner.getDynamicDevice().closeDevice();
        log.logDeviceEvent("lifecycle_close", "Scanner", scanner.getDeviceName());
    }

    public DeviceLifecycleResponse getLifecycleStatus() {
        return new DeviceLifecycleResponse(
                scanner.getDynamicDevice().getLifecycleState(),
                scanner.getDeviceName(),
                "Scanner"
        );
    }
}
