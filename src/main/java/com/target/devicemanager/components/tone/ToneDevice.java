package com.target.devicemanager.components.tone;

import com.target.devicemanager.common.DynamicDevice;
import com.target.devicemanager.common.StructuredEventLogger;
import com.target.devicemanager.common.entities.DeviceException;
import com.target.devicemanager.components.tone.entities.ToneError;
import com.target.devicemanager.components.tone.entities.ToneRequest;
import jpos.JposConst;
import jpos.JposException;
import jpos.ToneIndicator;
import jpos.events.StatusUpdateEvent;
import jpos.events.StatusUpdateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class ToneDevice implements StatusUpdateListener {
    private final DynamicDevice<? extends ToneIndicator> dynamicTone;
    private boolean deviceConnected = false;
    private boolean areListenersAttached = false;
    private final ReentrantLock connectLock;
    private boolean isLocked = false;
    private static final Logger LOGGER = LoggerFactory.getLogger(ToneDevice.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getToneIndicatorServiceName(), "ToneDevice", LOGGER);

    public ToneDevice(DynamicDevice<? extends ToneIndicator> dynamicTone) {
        this(dynamicTone, new ReentrantLock(true));
    }

    public ToneDevice(DynamicDevice<? extends ToneIndicator> dynamicTone, ReentrantLock connectLock) {
        if (dynamicTone == null) {
            IllegalArgumentException ex = new IllegalArgumentException("dynamicTone cannot be null");
            log.failure("ToneDevice Failed in Constructor: dynamicTone cannot be null", 18, ex);
            throw ex;
        }
        this.dynamicTone = dynamicTone;
        this.connectLock = connectLock;
    }

    public void disconnect() {
        if (dynamicTone.isConnected()) {
            if (areListenersAttached) {
                detachEventListeners();
                areListenersAttached = false;
            }
            ToneIndicator tone;
            synchronized (tone = dynamicTone.getDevice()) {
                try {
                    if (tone.getDeviceEnabled()) {
                        tone.setDeviceEnabled(false);
                        dynamicTone.disconnect();
                        deviceConnected = false;
                    }
                } catch (JposException jposException) {
                    log.failure("ToneIndicator Failed to Disconnect", 18, jposException);
                }
            }
        }
    }

    /**
     * Play a custom tone using JPOS ToneIndicator.sound().
     */
    public void playTone(ToneRequest request) throws JposException, DeviceException {
        if (!isConnected()) {
            JposException jposException = new JposException(JposConst.JPOS_E_OFFLINE);
            log.failure("ToneIndicator is not connected", 18, jposException);
            throw jposException;
        }
        ToneIndicator tone;
        synchronized (tone = dynamicTone.getDevice()) {
            log.success("Playing tone: " + request, 1);
            tone.setTone1Duration(request.tone1Duration);
            tone.setTone1Pitch(request.tone1Frequency);
            if (request.tone2Duration > 0) {
                tone.setTone2Duration(request.tone2Duration);
                tone.setTone2Pitch(request.tone2Frequency);
            }
            tone.setInterToneWait(request.interToneWait);
            tone.sound(request.numberOfCycles, request.interToneWait);
        }
    }

    /**
     * Convenience method for a single standard beep (1 cycle, 200ms, 1000Hz).
     */
    public void beep() throws JposException, DeviceException {
        playTone(new ToneRequest(1, 0, 200, 1000, 0, 0));
    }

    private void attachEventListeners() {
        ToneIndicator tone;
        synchronized (tone = dynamicTone.getDevice()) {
            tone.addStatusUpdateListener(this);
        }
    }

    private void detachEventListeners() {
        ToneIndicator tone;
        synchronized (tone = dynamicTone.getDevice()) {
            tone.removeStatusUpdateListener(this);
        }
    }

    @Override
    public void statusUpdateOccurred(StatusUpdateEvent statusUpdateEvent) {
        int status = statusUpdateEvent.getStatus();
        log.success("ToneIndicator statusUpdateOccurred(): " + status, 1);
        switch (status) {
            case JposConst.JPOS_SUE_POWER_OFF:
            case JposConst.JPOS_SUE_POWER_OFF_OFFLINE:
            case JposConst.JPOS_SUE_POWER_OFFLINE:
                log.failure("ToneIndicator Status Update: Power offline", 13, null);
                deviceConnected = false;
                break;
            case JposConst.JPOS_SUE_POWER_ONLINE:
                log.success("ToneIndicator Status Update: Power online", 5);
                deviceConnected = true;
                break;
            default:
                break;
        }
    }

    public String getDeviceName() {
        return dynamicTone.getDeviceName();
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
    public DynamicDevice<? extends ToneIndicator> getDynamicDevice() {
        return dynamicTone;
    }
}
