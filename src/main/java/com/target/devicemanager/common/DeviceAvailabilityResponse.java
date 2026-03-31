package com.target.devicemanager.common;

import java.util.ArrayList;

public class DeviceAvailabilityResponse {
    public String ursaIoVersion;
    public String confirmVersion;
    public ArrayList<DeviceConfigResponse> devicelist;

    public DeviceAvailabilityResponse() {
        devicelist = new ArrayList<>();
    }
}
