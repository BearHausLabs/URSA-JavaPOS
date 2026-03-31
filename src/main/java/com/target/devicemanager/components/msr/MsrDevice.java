package com.target.devicemanager.components.msr;

import com.target.devicemanager.common.DeviceListener;
import com.target.devicemanager.common.DynamicDevice;
import com.target.devicemanager.common.StructuredEventLogger;
import com.target.devicemanager.common.entities.DeviceError;
import com.target.devicemanager.common.entities.DeviceException;
import com.target.devicemanager.components.msr.entities.MsrData;
import com.target.devicemanager.components.msr.entities.MsrError;
import jpos.JposConst;
import jpos.JposException;
import jpos.MSR;
import jpos.events.DataEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class MsrDevice {
    private final DynamicDevice<? extends MSR> dynamicMsr;
    private final DeviceListener deviceListener;
    private boolean deviceConnected = false;
    private boolean areListenersAttached = false;
    private final ReentrantLock connectLock;
    private boolean isLocked = false;
    private static final Logger LOGGER = LoggerFactory.getLogger(MsrDevice.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getMsrServiceName(), "MsrDevice", LOGGER);

    public MsrDevice(DynamicDevice<? extends MSR> dynamicMsr, DeviceListener deviceListener) {
        this(dynamicMsr, deviceListener, new ReentrantLock(true));
    }

    public MsrDevice(DynamicDevice<? extends MSR> dynamicMsr, DeviceListener deviceListener, ReentrantLock connectLock) {
        if (dynamicMsr == null) {
            IllegalArgumentException ex = new IllegalArgumentException("dynamicMsr cannot be null");
            log.failure("MSR Failed in Constructor: dynamicMsr cannot be null", 18, ex);
            throw ex;
        }
        if (deviceListener == null) {
            IllegalArgumentException ex = new IllegalArgumentException("deviceListener cannot be null");
            log.failure("MSR Failed in Constructor: deviceListener cannot be null", 18, ex);
            throw ex;
        }
        this.dynamicMsr = dynamicMsr;
        this.deviceListener = deviceListener;
        this.connectLock = connectLock;
    }

    public void disconnect() {
        if (areListenersAttached) {
            detachEventListeners();
            areListenersAttached = false;
        }
        if (dynamicMsr.isConnected()) {
            MSR msr;
            synchronized (msr = dynamicMsr.getDevice()) {
                try {
                    if (msr.getDeviceEnabled()) {
                        msr.setDeviceEnabled(false);
                    }
                } catch (JposException jposException) {
                    log.failure("MSR Failed to Disable", 18, jposException);
                }
            }
            dynamicMsr.disconnect();
        }
        deviceConnected = false;
    }

    /**
     * Blocking read that waits for a card swipe event.
     * Uses the same waitForData pattern as Scanner.
     */
    public MsrData readCard() throws JposException, DeviceException {
        log.success("readCard(in)", 1);
        enable();
        try {
            DataEvent dataEvent = deviceListener.waitForData();
            return handleDataEvent(dataEvent);
        } catch (JposException jposException) {
            log.failure("readCard failed: " + jposException.getErrorCode(), 17, jposException);
            throw jposException;
        }
    }

    /**
     * Cancel a pending card read by stopping the event wait.
     */
    public void cancelRead() {
        log.success("cancelRead(in)", 1);
        try {
            disable();
        } catch (JposException jposException) {
            log.failure("Exception in cancelRead", 1, jposException);
        } finally {
            deviceListener.stopWaitingForData();
        }
        log.success("cancelRead(out)", 1);
    }

    private MsrData handleDataEvent(DataEvent dataEvent) throws JposException {
        if (!(dataEvent.getSource() instanceof MSR)) {
            JposException jposException = new JposException(JposConst.JPOS_E_FAILURE);
            log.failure("MSR handleDataEvent: source is not MSR", 17, jposException);
            throw jposException;
        }
        try {
            MSR msr;
            synchronized (msr = (MSR) dataEvent.getSource()) {
                byte[] track1Raw = msr.getTrack1Data();
                byte[] track2Raw = msr.getTrack2Data();
                byte[] track3Raw = msr.getTrack3Data();
                byte[] track4Raw = msr.getTrack4Data();

                String track1 = track1Raw != null ? new String(track1Raw, Charset.defaultCharset()) : null;
                String track2 = track2Raw != null ? new String(track2Raw, Charset.defaultCharset()) : null;
                String track3 = track3Raw != null ? new String(track3Raw, Charset.defaultCharset()) : null;
                String track4 = track4Raw != null ? new String(track4Raw, Charset.defaultCharset()) : null;

                // Re-enable for continuous listening
                msr.setDataEventEnabled(true);

                MsrData msrData = new MsrData(track1, track2, track3, track4);
                log.success("readCard(out) - card data received", 9);
                return msrData;
            }
        } catch (JposException jposException) {
            log.failure("MSR Failed to read track data: " + jposException.getErrorCode(), 17, jposException);
            throw jposException;
        }
    }

    private void enable() throws JposException {
        if (!isConnected()) {
            JposException jposException = new JposException(JposConst.JPOS_E_OFFLINE);
            log.failure("MSR is not connected", 18, jposException);
            throw jposException;
        }
        deviceListener.startEventListeners();
        MSR msr;
        synchronized (msr = dynamicMsr.getDevice()) {
            msr.setAutoDisable(false);
            msr.setDecodeData(true);
            msr.setDataEventEnabled(true);
            msr.setDeviceEnabled(true);
        }
    }

    private void disable() throws JposException {
        MSR msr;
        synchronized (msr = dynamicMsr.getDevice()) {
            msr.setDeviceEnabled(false);
        }
    }

    private void attachEventListeners() {
        MSR msr;
        synchronized (msr = dynamicMsr.getDevice()) {
            msr.addErrorListener(deviceListener);
            msr.addDataListener(deviceListener);
            msr.addStatusUpdateListener(deviceListener);
        }
    }

    private void detachEventListeners() {
        MSR msr;
        synchronized (msr = dynamicMsr.getDevice()) {
            msr.removeErrorListener(deviceListener);
            msr.removeDataListener(deviceListener);
            msr.removeStatusUpdateListener(deviceListener);
        }
    }

    public String getDeviceName() {
        return dynamicMsr.getDeviceName();
    }

    public boolean isConnected() {
        return deviceConnected;
    }

    public void setDeviceConnected(boolean deviceConnected) {
        this.deviceConnected = deviceConnected;
    }

    public boolean tryLock() {
        try {
            isLocked = connectLock.tryLock(10, TimeUnit.SECONDS);
            log.success("Lock: " + isLocked, 1);
        } catch (InterruptedException interruptedException) {
            log.failure("Lock Failed", 17, interruptedException);
        }
        return isLocked;
    }

    public void unlock() {
        connectLock.unlock();
        isLocked = false;
    }

    public boolean getIsLocked() {
        return isLocked;
    }

    /**
     * Exposes the underlying DynamicDevice for lifecycle operations.
     */
    public DynamicDevice<? extends MSR> getDynamicDevice() {
        return dynamicMsr;
    }
}
