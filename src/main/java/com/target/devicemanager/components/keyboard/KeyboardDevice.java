package com.target.devicemanager.components.keyboard;

import com.target.devicemanager.common.DynamicDevice;
import com.target.devicemanager.common.StructuredEventLogger;
import com.target.devicemanager.common.DeviceListener;
import com.target.devicemanager.components.keyboard.entities.KeyEvent;
import jpos.POSKeyboard;
import jpos.POSKeyboardConst;
import jpos.JposConst;
import jpos.JposException;
import jpos.events.DataEvent;
import jpos.events.DataListener;
import jpos.events.StatusUpdateEvent;
import jpos.events.StatusUpdateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class KeyboardDevice implements DataListener, StatusUpdateListener {
    private final DynamicDevice<? extends POSKeyboard> dynamicKeyboard;
    private final DeviceListener deviceListener;
    private boolean deviceConnected = false;
    private boolean areListenersAttached;
    private final ReentrantLock connectLock;
    private boolean isLocked = false;
    private Consumer<KeyEvent> eventCallback;
    private KeyEvent lastKeyEvent;
    private static final Logger LOGGER = LoggerFactory.getLogger(KeyboardDevice.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of("keyboard", "KeyboardDevice", LOGGER);

    /**
     * Initializes the POS Keyboard Device.
     * @param dynamicKeyboard the dynamic device wrapper.
     * @param deviceListener listens for device events.
     */
    public KeyboardDevice(DynamicDevice<? extends POSKeyboard> dynamicKeyboard, DeviceListener deviceListener) {
        this(dynamicKeyboard, deviceListener, new ReentrantLock(true));
    }

    public KeyboardDevice(DynamicDevice<? extends POSKeyboard> dynamicKeyboard, DeviceListener deviceListener, ReentrantLock connectLock) {
        if (dynamicKeyboard == null) {
            IllegalArgumentException illegalArgumentException = new IllegalArgumentException("dynamicKeyboard cannot be null");
            log.failure("POSKeyboard Failed in Constructor: dynamicKeyboard cannot be null", 18,
                    illegalArgumentException);
            throw illegalArgumentException;
        }
        if (deviceListener == null) {
            IllegalArgumentException illegalArgumentException = new IllegalArgumentException("deviceListener cannot be null");
            log.failure("POSKeyboard Failed in Constructor: deviceListener cannot be null", 18,
                    illegalArgumentException);
            throw illegalArgumentException;
        }
        this.dynamicKeyboard = dynamicKeyboard;
        this.deviceListener = deviceListener;
        this.connectLock = connectLock;
    }

    /**
     * Connects to the POS keyboard device, attaches listeners, and enables it.
     */
    public boolean connect() {
        if (dynamicKeyboard.connect() == DynamicDevice.ConnectionResult.NOT_CONNECTED) {
            return false;
        }
        if (!areListenersAttached) {
            attachEventListeners();
            areListenersAttached = true;
        }

        POSKeyboard keyboard;
        synchronized (keyboard = dynamicKeyboard.getDevice()) {
            try {
                if (!keyboard.getDeviceEnabled()) {
                    keyboard.setDeviceEnabled(true);
                    try {
                        keyboard.setDataEventEnabled(true);
                    } catch (JposException jposException) {
                        log.failure("Failed to enable data events", 17, jposException);
                    }
                }
                deviceConnected = true;
            } catch (JposException jposException) {
                deviceConnected = false;
                return false;
            }
        }
        return true;
    }

    /**
     * This method is only used to set 'areListenersAttached' for unit testing
     */
    public void setAreListenersAttached(boolean areListenersAttached) {
        this.areListenersAttached = areListenersAttached;
    }

    /**
     * This method is only used to get 'areListenersAttached' for unit testing
     */
    public boolean getAreListenersAttached() {
        return areListenersAttached;
    }

    /**
     * This method is only used to set 'deviceConnected' for unit testing
     */
    public void setDeviceConnected(boolean deviceConnected) {
        this.deviceConnected = deviceConnected;
    }

    /**
     * Disconnects the POS keyboard device.
     */
    public void disconnect() {
        if (areListenersAttached) {
            detachEventListeners();
            areListenersAttached = false;
        }
        if (dynamicKeyboard.isConnected()) {
            POSKeyboard keyboard;
            synchronized (keyboard = dynamicKeyboard.getDevice()) {
                try {
                    if (keyboard.getDeviceEnabled()) {
                        keyboard.setDataEventEnabled(false);
                        keyboard.setDeviceEnabled(false);
                    }
                } catch (JposException jposException) {
                    log.failure("POSKeyboard Failed to Disable", 18, jposException);
                }
            }
            dynamicKeyboard.disconnect();
        }
        deviceConnected = false;
    }

    /**
     * Gets the device name.
     */
    public String getDeviceName() {
        return dynamicKeyboard.getDeviceName();
    }

    /**
     * Shows if the device is connected.
     */
    public boolean isConnected() {
        return deviceConnected;
    }

    /**
     * Returns the most recent key event, or null if none has occurred yet.
     */
    public KeyEvent getLastKeyEvent() {
        return lastKeyEvent;
    }

    /**
     * Sets the callback for keyboard events. The manager registers itself here.
     */
    public void setEventCallback(Consumer<KeyEvent> callback) {
        this.eventCallback = callback;
    }

    /**
     * Attaches both data and status update listeners to the device.
     */
    private void attachEventListeners() {
        POSKeyboard keyboard;
        synchronized (keyboard = dynamicKeyboard.getDevice()) {
            keyboard.addDataListener(this);
            keyboard.addStatusUpdateListener(this);
        }
    }

    /**
     * Removes data and status update listeners from the device.
     */
    private void detachEventListeners() {
        POSKeyboard keyboard;
        synchronized (keyboard = dynamicKeyboard.getDevice()) {
            keyboard.removeDataListener(this);
            keyboard.removeStatusUpdateListener(this);
        }
    }

    /**
     * JavaPOS callback fired when a key is pressed or released.
     * Captures the key data, creates a KeyEvent, notifies the manager
     * via the callback, and re-enables data events for the next key press.
     */
    @Override
    public void dataOccurred(DataEvent dataEvent) {
        log.success("POSKeyboard dataOccurred(): " + dataEvent.getStatus(), 1);
        try {
            POSKeyboard keyboard;
            synchronized (keyboard = dynamicKeyboard.getDevice()) {
                int keyData = keyboard.getPOSKeyData();
                int eventType = keyboard.getPOSKeyEventType();

                String eventTypeName = (eventType == POSKeyboardConst.KBD_KET_KEYDOWN)
                        ? "KEY_DOWN" : "KEY_UP";

                KeyEvent event = new KeyEvent(
                        keyData,
                        eventTypeName,
                        System.currentTimeMillis()
                );
                lastKeyEvent = event;

                log.success("Key event: code=" + keyData + " type=" + eventTypeName, 1);

                if (eventCallback != null) {
                    eventCallback.accept(event);
                }

                keyboard.setDataEventEnabled(true);
            }
        } catch (JposException jposException) {
            log.failure("Error processing keyboard data event", 17, jposException);
            try {
                POSKeyboard keyboard;
                synchronized (keyboard = dynamicKeyboard.getDevice()) {
                    keyboard.setDataEventEnabled(true);
                }
            } catch (JposException e) {
                log.failure("Failed to re-enable data events after error", 17, e);
            }
        }
    }

    /**
     * JavaPOS callback for device status changes like power and connectivity.
     */
    @Override
    public void statusUpdateOccurred(StatusUpdateEvent statusUpdateEvent) {
        int status = statusUpdateEvent.getStatus();
        log.success("POSKeyboard statusUpdateOccurred(): " + status, 1);
        switch (status) {
            case JposConst.JPOS_SUE_POWER_OFF:
            case JposConst.JPOS_SUE_POWER_OFF_OFFLINE:
            case JposConst.JPOS_SUE_POWER_OFFLINE:
                log.failure("POSKeyboard Status Update: Power offline", 13, null);
                deviceConnected = false;
                break;
            case JposConst.JPOS_SUE_POWER_ONLINE:
                log.success("Status Update: Power online", 5);
                deviceConnected = true;
                break;
            default:
                break;
        }
    }

    /**
     * Lock the current resource.
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
     */
    public boolean getIsLocked() {
        return isLocked;
    }

    /**
     * Exposes the underlying DynamicDevice for lifecycle operations.
     */
    public DynamicDevice<? extends POSKeyboard> getDynamicDevice() {
        return dynamicKeyboard;
    }
}
