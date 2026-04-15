package com.target.devicemanager.components.scanner;

import com.target.devicemanager.common.StructuredEventLogger;
import com.target.devicemanager.common.DeviceListener;
import com.target.devicemanager.common.DynamicDevice;
import com.target.devicemanager.components.scanner.entities.Barcode;
import com.target.devicemanager.configuration.ApplicationConfig;
import jpos.JposConst;
import jpos.JposException;
import jpos.Scanner;
import jpos.events.DataEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class ScannerDevice {
    private final DynamicDevice<? extends Scanner> dynamicScanner;
    private final DeviceListener deviceListener;
    private boolean deviceConnected = false;
    private static final Logger LOGGER = LoggerFactory.getLogger(ScannerDevice.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getScannerServiceName(), "ScannerDevice", LOGGER);
    private final ReentrantLock connectLock;
    private boolean isLocked = false;
    private boolean isTest = false;
    ApplicationConfig applicationConfig;

    /**
     * Initializes scanner device.
     * @param deviceListener
     * @param dynamicScanner is the dynamic device.
     */
    public ScannerDevice(DeviceListener deviceListener, DynamicDevice<? extends Scanner> dynamicScanner, ApplicationConfig applicationConfig) {
        this(deviceListener, dynamicScanner, new ReentrantLock(true), applicationConfig);
    }

    public ScannerDevice(DeviceListener deviceListener, DynamicDevice<? extends Scanner> dynamicScanner, ReentrantLock connectLock, ApplicationConfig applicationConfig) {
        if (deviceListener == null) {
            log.failure("Failed in Constructor: deviceListener cannot be null", 17, null);
            throw new IllegalArgumentException("deviceListener cannot be null");
        }
        if (dynamicScanner == null) {
            log.failure("Failed in Constructor: dynamicScanner cannot be null", 17, null);
            throw new IllegalArgumentException("dynamicScanner cannot be null");
        }
        this.dynamicScanner = dynamicScanner;
        this.deviceListener = deviceListener;
        this.connectLock = connectLock;
        this.applicationConfig = applicationConfig;
    }

    public Boolean getDeviceConnected() {
        return this.deviceConnected;
    }

    public void setDeviceConnected(boolean deviceConnected) {
        this.deviceConnected = deviceConnected;
    }

    /**
     * Gets the scanner data from the barcode.
     * @return
     * @throws JposException
     */
    public Barcode getScannerData() throws JposException {
        log.success("getScannerData(in)", 1);
        enable();
        //waitForData can potentially block forever
        try {
            DataEvent dataEvent = deviceListener.waitForData();
            return handleDataEvent(dataEvent);
        } catch (JposException jposException) {
            throw jposException;
        }
    }

    /**
     * Handles the data based on barcode.
     * @param dataEvent instance of data event.
     * @return
     * @throws JposException
     */
    private Barcode handleDataEvent(DataEvent dataEvent) throws JposException {
        if (!(dataEvent.getSource() instanceof Scanner)) {
            log.success("getScannerData(out)", 1);
            JposException jposException = new JposException(JposConst.JPOS_E_FAILURE);
            log.failure("Failed to Handle Data: " + jposException.getMessage(), 17, jposException);
            throw jposException;
        }
        try {
            String data;
            int type;
            Scanner scanner;
            synchronized (scanner = (Scanner) dataEvent.getSource()) {
                data = new String(scanner.getScanDataLabel(), Charset.defaultCharset());
                type = scanner.getScanDataType();
            }
            Barcode barcode = new Barcode(data, type);
            log.success("returning scanned data type: " + barcode.type + " of size " + data.length(), 9);
            log.success("getScannerData(out)", 1);
            return barcode;
        } catch (JposException jposException) {
            log.failure("Failed to Handle Data: " + jposException.getErrorCode() + ", " + jposException.getErrorCodeExtended(), 17, jposException);
            throw jposException;
        }
    }

    /**
     * Disables scanner and cancels scanner data.
     * @return null.
     */
    public Void cancelScannerData() {
        log.success("cancelScannerData(in)", 1);
        try {
            disable();
        } catch (JposException jposException) {
            log.failure("Received exception in cancelScannerData", 1, jposException);
        } finally {
            deviceListener.stopWaitingForData();
        }
        log.success("cancelScannerData(out)", 1);
        return null;
    }

    /**
     * Gets the device name.
     * @return device name.
     */
    public String getDeviceName() {
        return dynamicScanner.getDeviceName();
    }

    /**
     * Makes sure scanner is connected.
     * @return Connection status.
     */
    public boolean isConnected() {
        return dynamicScanner.isConnected();
    }


    public void setIsTest(boolean isTest) {
        this.isTest = isTest;
    }

    /**
     * Makes sure scanner is connected and enabled.
     * @throws JposException
     */
    protected void enable() throws JposException {
        log.success("enable(in)", 1);
        if (!isConnected()) {
            JposException jposException = new JposException(JposConst.JPOS_E_OFFLINE);
            throw jposException;
        }
        attachEventListeners();
        deviceListener.startEventListeners();
        try {
            Scanner scanner;
            synchronized (scanner = dynamicScanner.getDevice()) {
                scanner.setAutoDisable(true);
                scanner.setDecodeData(true);
                scanner.setDataEventEnabled(true);
                scanner.setDeviceEnabled(true);
                if (isTest) { // used to test timeouts in unit testing
                    try {
                        Thread.sleep(1100);
                    } catch (InterruptedException interruptedException) {
                        //ignore
                    }
                }
            }
        } catch (JposException jposException) {
            log.failure("Failed to Enable Device: " + jposException.getErrorCode() + ", " + jposException.getErrorCodeExtended(), 17, jposException);
            throw jposException;
        }
        log.success("scanner enabled", 1);
        log.success("enable(out)", 1);
    }

    /**
     * Disables scanner.
     * @throws JposException
     */
    private void disable() throws JposException {
        log.success("disable(in)", 1);
        try {
            Scanner scanner;
            synchronized (scanner = dynamicScanner.getDevice()) {
                scanner.setDeviceEnabled(false);
            }
        } catch (JposException jposException) {
            if (isConnected()) {
                log.failure("Failed to Disable Device: " + jposException.getErrorCode() + ", " + jposException.getErrorCodeExtended(), 17, jposException);
            } else if (jposException.getErrorCode() != JposConst.JPOS_E_CLOSED) {
                log.failure("Failed to Disable Device: " + jposException.getErrorCode() + ", " + jposException.getErrorCodeExtended(), 17, jposException);
            }
            throw jposException;
        }
        log.success("scanner disabled", 1);
        log.success("disable(out)", 1);
    }

    /**
     * Attaches an event listener and adding it to a new instances.
     */
    private void attachEventListeners() {
        Scanner scanner;
        synchronized (scanner = dynamicScanner.getDevice()) {
            scanner.addErrorListener(deviceListener);
            scanner.addDataListener(deviceListener);
            scanner.addStatusUpdateListener(deviceListener);
        }
    }

    /**
     * Removes error, data, and status update device listeners
     */
    private void detachEventListeners() {
        Scanner scanner;
        synchronized (scanner = dynamicScanner.getDevice()) {
            scanner.removeErrorListener(deviceListener);
            scanner.removeDataListener(deviceListener);
            scanner.removeStatusUpdateListener(deviceListener);
        }
    }

    /**
     * Lock the current resource.
     * @return
     */
    public boolean tryLock() {
        try {
            isLocked = connectLock.tryLock(10, TimeUnit.SECONDS);
            log.success("Lock: " + isLocked, 1);
        } catch (InterruptedException interruptedException) {
            log.failure("Lock Failed: " + interruptedException.getMessage(), 17, interruptedException);
        }
        return isLocked;
    }

    /**
     * unlock the current resource.
     */
    public void unlock() {
        connectLock.unlock();
        isLocked = false;
    }

    /**
     * Exposes the underlying DynamicDevice for lifecycle operations.
     */
    public DynamicDevice<? extends Scanner> getDynamicDevice() {
        return dynamicScanner;
    }
}
