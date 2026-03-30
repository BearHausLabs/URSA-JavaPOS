package com.target.devicemanager.common;

import jpos.BaseJposControl;
import jpos.JposConst;

public class SimulatedDynamicDevice<T extends BaseJposControl> extends DynamicDevice<T> {
    private final T simulatedDevice;

    public SimulatedDynamicDevice(T baseJposControl, DevicePower devicePower, DeviceConnector<T> deviceConnector) {
        super(baseJposControl, devicePower, deviceConnector);
        simulatedDevice = baseJposControl;
    }

    @Override
    public boolean isConnected() {
        return simulatedDevice.getState() == JposConst.JPOS_S_IDLE;
    }
}
