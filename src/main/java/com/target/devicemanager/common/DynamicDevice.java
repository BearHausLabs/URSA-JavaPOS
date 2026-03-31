package com.target.devicemanager.common;

import jpos.BaseJposControl;
import jpos.JposConst;
import jpos.JposException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamicDevice<DEVICE extends BaseJposControl> {
    private final DEVICE device;
    private final DeviceConnector<DEVICE> deviceConnector;
    private final DevicePower devicePower;
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicDevice.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getCommonServiceName(), "DynamicDevice", LOGGER);

    public DynamicDevice(DEVICE device, DevicePower devicePower, DeviceConnector<DEVICE> deviceConnector) {
        if (device == null) {
            throw new IllegalArgumentException("device cannot be null");
        }
        if (devicePower == null) {
            throw new IllegalArgumentException("devicePower cannot be null");
        }
        if (deviceConnector == null) {
            throw new IllegalArgumentException("deviceConnector cannot be null");
        }

        this.device = device;
        this.devicePower = devicePower;
        this.deviceConnector = deviceConnector;
    }

    public void disconnect() {
        synchronized (device) {
            // Skip release if skipClaim is set on the connector
            if (!deviceConnector.isSkipClaim()) {
                try {
                    device.release();
                    log.success(getDeviceName() + " Released", 5);
                } catch (JposException jposException) {
                    log.failure(getDeviceName() + " Release failed " + jposException.getMessage(), 5, jposException);
                }
            }
            try {
                device.close();
                log.success(getDeviceName() + " Closed", 5);
            } catch (JposException jposException) {
                log.failure(getDeviceName() + " Close failed : " + jposException.getMessage(), 17, jposException);
            }
        }
    }

    public boolean isConnected() {
        synchronized (device) {
            int deviceState = device.getState();
            if (deviceState != JposConst.JPOS_S_IDLE && deviceState != JposConst.JPOS_S_BUSY) {
                return false;
            }
            try {
                // If skipClaim, don't check getClaimed()
                if (!deviceConnector.isSkipClaim() && !device.getClaimed()) {
                    return false;
                }
                int powerState = devicePower.getPowerState(device);
                if (powerState != JposConst.JPOS_PS_ONLINE && powerState != JposConst.JPOS_PS_UNKNOWN) {
                    return false;
                }
            } catch (JposException jposException) {
                return false;
            }
        }
        return true;
    }

    public DEVICE getDevice() {
        return device;
    }

    public String getDeviceName() {
        return deviceConnector.getConnectedDeviceName();
    }

    // --- Lifecycle pass-through to DeviceConnector ---

    public DeviceConnector<DEVICE> getDeviceConnector() {
        return deviceConnector;
    }

    public void openDevice(String logicalName) throws JposException {
        deviceConnector.openDevice(logicalName);
    }

    public void claimDevice(int timeout) throws JposException {
        deviceConnector.claimDevice(timeout);
    }

    public void enableDevice() throws JposException {
        deviceConnector.enableDevice();
        devicePower.enablePowerNotification(device);
    }

    public void disableDevice() throws JposException {
        deviceConnector.disableDevice();
    }

    public void releaseDevice() throws JposException {
        deviceConnector.releaseDevice();
    }

    public void closeDevice() throws JposException {
        deviceConnector.closeDevice();
    }

    public DeviceLifecycleState getLifecycleState() {
        return deviceConnector.getLifecycleState();
    }
}
