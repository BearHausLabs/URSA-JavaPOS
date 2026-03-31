package com.target.devicemanager.components.printer;

import com.target.devicemanager.common.DeviceLifecycleResponse;
import com.target.devicemanager.common.DeviceLifecycleState;
import com.target.devicemanager.common.LogPayloadBuilder;
import com.target.devicemanager.common.StructuredEventLogger;
import com.target.devicemanager.common.entities.*;
import com.target.devicemanager.components.printer.entities.PrinterContent;
import com.target.devicemanager.components.printer.entities.PrinterError;
import com.target.devicemanager.components.printer.entities.PrinterException;
import com.target.devicemanager.components.printer.entities.PrinterStationType;
import jpos.JposConst;
import jpos.JposException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;

@EnableCaching
public class PrinterManager {

    @Autowired
    private CacheManager cacheManager;

    private final PrinterDevice printerDevice;
    private final Lock printerLock;
    private static final int PRINTER_TIMEOUT = 10;  // Timeout value for printContent call in seconds
    private static final Logger LOGGER = LoggerFactory.getLogger(PrinterManager.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getPrinterServiceName(), "PrinterManager", LOGGER);

    public PrinterManager(PrinterDevice printerDevice, Lock printerLock) {
        this(printerDevice, printerLock, null, null, false);
    }

    public PrinterManager(PrinterDevice printerDevice, Lock printerLock, CacheManager cacheManager, Future<Void> future, boolean isTest) {
        if (printerDevice == null) {
            throw new IllegalArgumentException("printerDevice cannot be null");
        }
        if (printerLock == null) {
            throw new IllegalArgumentException("printerLock cannot be null");
        }

        this.printerDevice = printerDevice;
        this.printerLock = printerLock;

        if(cacheManager != null) {
            this.cacheManager = cacheManager;
        }
    }

    public void printReceipt(List<PrinterContent> contents) throws DeviceException {
        if (!printerLock.tryLock()) {
            PrinterException printerException = new PrinterException(PrinterError.DEVICE_BUSY);
            throw printerException;
        }

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<?> localFuture = null;

        try {
            Callable<Void> task = () -> {
                printerDevice.printContent(contents, PrinterStationType.RECEIPT_PRINTER.getValue());
                return null;
            };

            localFuture = executorService.submit(task);
            localFuture.get(PRINTER_TIMEOUT, TimeUnit.SECONDS);

        } catch (ExecutionException executionException) {
            Throwable cause = executionException.getCause();
            PrinterException printerException;
            if (cause instanceof PrinterException) {
                printerException = (PrinterException) cause;
            } else if (cause instanceof JposException) {
                printerException = new PrinterException((JposException) cause);
            } else {
                printerException = new PrinterException(new JposException(JposConst.JPOS_E_FAILURE));
            }
            log.failure(printerException.getDeviceError().getDescription(), 17, printerException);
            throw printerException;

        } catch (TimeoutException timeoutException) {
            if (localFuture != null) {
                localFuture.cancel(true);
            }
            executorService.shutdownNow(); // attempt to stop running tasks immediately
            log.failure(PrinterError.PRINTER_TIME_OUT.getDescription(), 17, timeoutException);
            throw new PrinterException(PrinterError.PRINTER_TIME_OUT);
        } catch (InterruptedException interruptedException) {
            // preserve interrupt status and try to stop worker
            if (localFuture != null) {
                localFuture.cancel(true);
            }
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
            PrinterException printerException = new PrinterException(new JposException(JposConst.JPOS_E_FAILURE));
            log.failure(printerException.getDeviceError().getDescription(), 17, printerException);
            throw printerException;
        } finally {
            if (!executorService.isShutdown()) {
                executorService.shutdownNow();
            }
            printerLock.unlock();
        }
    }

    public void frankCheck(List<PrinterContent> contents) throws PrinterException {
        if (!printerLock.tryLock()) {
            PrinterException printerException = new PrinterException(PrinterError.DEVICE_BUSY);
            throw printerException;
        }

        try {
            printerDevice.printContent(contents,PrinterStationType.CHECK_PRINTER.getValue());
        } catch (JposException exception) {
            PrinterException printerException = new PrinterException(exception);
            throw printerException;
        } finally {
            printerLock.unlock();
        }
    }

    public DeviceHealthResponse getHealth() {
        DeviceHealthResponse deviceHealthResponse;
        if (printerDevice.isConnected()) {
            deviceHealthResponse = new DeviceHealthResponse(printerDevice.getDeviceName(), DeviceHealth.READY);
        } else {
            deviceHealthResponse = new DeviceHealthResponse(printerDevice.getDeviceName(), DeviceHealth.NOTREADY);
        }
        try {
            Objects.requireNonNull(cacheManager.getCache("printerHealth")).put("health", deviceHealthResponse);
        } catch (Exception exception) {
            log.failure("getCache(printerHealth) Failed",17, exception);
        }
        return deviceHealthResponse;
    }

    public DeviceHealthResponse getStatus() {
        try {
            if (cacheManager != null && Objects.requireNonNull(cacheManager.getCache("printerHealth")).get("health") != null) {
                return (DeviceHealthResponse) Objects.requireNonNull(cacheManager.getCache("printerHealth")).get("health").get();
            } else {
                log.failure("Not able to retrieve from cache, checking getHealth()", 5, null);
                return getHealth();
            }
        } catch (Exception exception) {
            return getHealth();
        }
    }

    public static int getPrinterTimeoutValue() {
        return PRINTER_TIMEOUT;
    }

    // --- Lifecycle methods ---

    public void openDevice(String logicalName) throws JposException {
        printerDevice.getDynamicDevice().openDevice(logicalName);
        log.logDeviceEvent("lifecycle_open", "Printer", logicalName);
    }

    public void claimDevice(int timeout) throws JposException {
        printerDevice.getDynamicDevice().claimDevice(timeout);
        log.logDeviceEvent("lifecycle_claim", "Printer", printerDevice.getDeviceName());
    }

    public void enableDevice() throws JposException {
        printerDevice.getDynamicDevice().enableDevice();
        log.logDeviceEvent("lifecycle_enable", "Printer", printerDevice.getDeviceName());
    }

    public void disableDevice() throws JposException {
        printerDevice.getDynamicDevice().disableDevice();
        log.logDeviceEvent("lifecycle_disable", "Printer", printerDevice.getDeviceName());
    }

    public void releaseDevice() throws JposException {
        printerDevice.getDynamicDevice().releaseDevice();
        log.logDeviceEvent("lifecycle_release", "Printer", printerDevice.getDeviceName());
    }

    public void closeDevice() throws JposException {
        printerDevice.getDynamicDevice().closeDevice();
        log.logDeviceEvent("lifecycle_close", "Printer", printerDevice.getDeviceName());
    }

    public DeviceLifecycleResponse getLifecycleStatus() {
        return new DeviceLifecycleResponse(
                printerDevice.getDynamicDevice().getLifecycleState(),
                printerDevice.getDeviceName(),
                "Printer"
        );
    }

}
