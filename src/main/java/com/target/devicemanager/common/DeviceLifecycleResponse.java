package com.target.devicemanager.common;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Response DTO for device lifecycle status queries.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DeviceLifecycleResponse {
    private final DeviceLifecycleState state;
    private final String logicalName;
    private final String deviceType;

    public DeviceLifecycleResponse(DeviceLifecycleState state, String logicalName, String deviceType) {
        this.state = state;
        this.logicalName = logicalName;
        this.deviceType = deviceType;
    }

    public DeviceLifecycleState getState() {
        return state;
    }

    public String getLogicalName() {
        return logicalName;
    }

    public String getDeviceType() {
        return deviceType;
    }

    @Override
    public String toString() {
        return "DeviceLifecycleResponse{" +
                "state=" + state +
                ", logicalName='" + logicalName + '\'' +
                ", deviceType='" + deviceType + '\'' +
                '}';
    }
}
