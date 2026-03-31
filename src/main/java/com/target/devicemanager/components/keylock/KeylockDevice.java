package com.target.devicemanager.components.keylock;

import com.target.devicemanager.common.DynamicDevice;
import com.target.devicemanager.common.StructuredEventLogger;
import com.target.devicemanager.common.DeviceListener;
import com.target.devicemanager.components.keylock.entities.KeyPosition;
import jpos.Keylock;
import jpos.KeylockConst;
import jpos.JposConst;
import jpos.JposException;
import jpos.events.StatusUpdateEvent;
import jpos.events.StatusUpdateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class KeylockDevice implements StatusUpdateListener {
    private final DynamicDevice<? extends Keylock> dynamicKeylock;
    private final DeviceListener deviceListener;
    private boolean deviceConnected = false;
    private int currentPosition = KeylockConst.LOCK_KP_LOCK;
    private boolean areListenersAttached;
    private final ReentrantLock connectLock;
    private boolean isLocked = false;
    private Consumer<KeyPosition> positionChangeCallback;
    private static final Logger LOGGER = LoggerFactory.getLogger(KeylockDevice.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of("keylock", "KeylockDevice", LOGGER);

    /**
     * Initializes the Keylock Device.
     * @param dynamicKeylock the dynamic device wrapper.
     * @param deviceListener listens for device events.
     */
    public KeylockDevice(DynamicDevice<? extends Keylock> dynamicKeylock, DeviceListener deviceListener) {
        this(dynamicKeylock, deviceListener, new ReentrantLock(true));
    }

    public KeylockDevice(DynamicDevice<? extends Keylock> dynamicKeylock, DeviceListener deviceListener, ReentrantLock connectLock) {
        if (dynamicKeylock == null) {
            IllegalArgumentException illegalArgumentException = new IllegalArgumentException("dynamicKeylock cannot be null");
            log.failure("Keylock Failed in Constructor: dynamicKeylock cannot be null", 18,
                    illegalArgumentException);
            throw illegalArgumentException;
        }
        if (deviceListener == null) {
            IllegalArgumentException illegalArgumentException = new IllegalArgumentException("deviceListener cannot be null");
            log.failure("Keylock Failed in Constructor: deviceListener cannot be null", 18,
                    illegalArgumentException);
            throw illegalArgumentException;
        }
        this.dynamicKeylock = dynamicKeylock;
        this.deviceListener = deviceListener;
        this.connectLock = connectLock;
    }

    /**
     * This method is only used to set 'areListenersAttached' for unit testing
     * @param areListenersAttached
     */
    public void setAreListenersAttached(boolean areListenersAttached) {
        this.areListenersAttached = areListenersAttached;
    }

    /**
     * This method is only used to get 'areListenersAttached' for unit testing
     * @return
     */
    public boolean getAreListenersAttached() {
        return areListenersAttached;
    }

    /**
     * This method is only used to set 'deviceConnected' for unit testing
     * @param deviceConnected
     */
    public void setDeviceConnected(boolean deviceConnected) {
        this.deviceConnected = deviceConnected;
    }

    /**
     * This method is only used to set 'currentPosition' for unit testing
     * @param currentPosition
     */
    public void setCurrentPosition(int currentPosition) {
        this.currentPosition = currentPosition;
    }

    /**
     * This method is only used to get 'currentPosition' for unit testing
     * @return
     */
    public int getCurrentPosition() {
        return currentPosition;
    }

    /**
     * Sets a callback to be invoked whenever the keylock position changes.
     * Used by KeylockManager to broadcast SSE events.
     */
    public void setPositionChangeCallback(Consumer<KeyPosition> callback) {
        this.positionChangeCallback = callback;
    }

    /**
     * Disconnects the keylock device.
     */
    public void disconnect() {
        if (areListenersAttached) {
            detachEventListeners();
            areListenersAttached = false;
        }
        if (dynamicKeylock.isConnected()) {
            Keylock keylock;
            synchronized (keylock = dynamicKeylock.getDevice()) {
                try {
                    if (keylock.getDeviceEnabled()) {
                        keylock.setDeviceEnabled(false);
                    }
                } catch (JposException jposException) {
                    log.failure("Keylock Failed to Disable", 18, jposException);
                }
            }
            dynamicKeylock.disconnect();
        }
        deviceConnected = false;
    }

    /**
     * Returns the current key position as a KeyPosition enum.
     * Queries the device directly for the live position.
     */
    public KeyPosition getKeyPosition() {
        int livePosition = currentPosition; // fallback to cached value

        if (dynamicKeylock.isConnected()) {
            Keylock keylock;
            synchronized (keylock = dynamicKeylock.getDevice()) {
                try {
                    livePosition = keylock.getKeyPosition();
                    currentPosition = livePosition;
                } catch (JposException jposException) {
                    log.failure("Failed to read live key position, using cached value", 17, jposException);
                }
            }
        }

        return KeyPosition.fromJposValue(livePosition);
    }

    /**
     * Gets the device name.
     * @return Device name.
     */
    public String getDeviceName() {
        return dynamicKeylock.getDeviceName();
    }

    /**
     * Shows if the device is connected.
     */
    public boolean isConnected() {
        return deviceConnected;
    }

    /**
     * Attaches status update listener to the keylock device.
     */
    private void attachEventListeners() {
        Keylock keylock;
        synchronized (keylock = dynamicKeylock.getDevice()) {
            keylock.addStatusUpdateListener(this);
        }
    }

    /**
     * Removes status update listener from the keylock device.
     */
    private void detachEventListeners() {
        Keylock keylock;
        synchronized (keylock = dynamicKeylock.getDevice()) {
            keylock.removeStatusUpdateListener(this);
        }
    }

    /**
     * Handles keylock status update events: power state changes and key position changes.
     * Key position changes are forwarded to SSE subscribers via the positionChangeCallback.
     * @param statusUpdateEvent
     */
    @Override
    public void statusUpdateOccurred(StatusUpdateEvent statusUpdateEvent) {
        int status = statusUpdateEvent.getStatus();
        log.success("Keylock statusUpdateOccurred(): " + status, 1);
        KeyPosition newPosition = null;
        switch (status) {
            case JposConst.JPOS_SUE_POWER_OFF:
            case JposConst.JPOS_SUE_POWER_OFF_OFFLINE:
            case JposConst.JPOS_SUE_POWER_OFFLINE:
                log.failure("Keylock Status Update: Power offline", 13, null);
                deviceConnected = false;
                break;
            case JposConst.JPOS_SUE_POWER_ONLINE:
                log.success("Status Update: Power online", 5);
                deviceConnected = true;
                break;
            case KeylockConst.LOCK_KP_LOCK:
                log.success("Key position: LOCK", 1);
                currentPosition = KeylockConst.LOCK_KP_LOCK;
                newPosition = KeyPosition.LOCK;
                break;
            case KeylockConst.LOCK_KP_NORM:
                log.success("Key position: NORMAL", 1);
                currentPosition = KeylockConst.LOCK_KP_NORM;
                newPosition = KeyPosition.NORMAL;
                break;
            case KeylockConst.LOCK_KP_SUPR:
                log.success("Key position: SUPERVISOR", 1);
                currentPosition = KeylockConst.LOCK_KP_SUPR;
                newPosition = KeyPosition.SUPERVISOR;
                break;
            default:
                log.success("Keylock unhandled status: " + status + " (0x" + Integer.toHexString(status) + ")", 1);
                break;
        }
        // Notify SSE subscribers of position change
        if (newPosition != null && positionChangeCallback != null) {
            try {
                positionChangeCallback.accept(newPosition);
            } catch (Exception e) {
                log.failure("Failed to notify position change callback", 5, e);
            }
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
            log.failure("Lock Failed", 17, interruptedException);
        }
        return isLocked;
    }

    /**
     * Unlock the current resource.
     */
    public void unlock() {
        connectLock.unlock();
        isLocked = false;
    }

    /**
     * This method is only used to get "isLocked" for unit testing
     * @return
     */
    public boolean getIsLocked() {
        return isLocked;
    }

    /**
     * Exposes the underlying DynamicDevice for lifecycle operations.
     */
    public DynamicDevice<? extends Keylock> getDynamicDevice() {
        return dynamicKeylock;
    }
}
