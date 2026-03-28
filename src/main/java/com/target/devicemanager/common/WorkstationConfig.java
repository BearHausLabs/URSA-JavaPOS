package com.target.devicemanager.common;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Binds possum.* properties from application.properties/yml.
 *
 * Example:
 *   possum.mode=config
 *   possum.device.printer.enabled=true
 *   possum.device.printer.logicalName=EpsonTMT88VI
 *   possum.device.cashdrawer.drawers[0].logicalName=CashDrawer_NCR
 */
@Configuration
@ConfigurationProperties(prefix = "possum")
public class WorkstationConfig {

    /** Discovery mode: 'config' (default) uses logical names; 'auto' falls back to auto-discovery. */
    private String mode = "config";

    /**
     * Lifecycle mode: 'manual' (default) — POSSUM only discovers/configures devices,
     * the POS application (URSA) controls open/claim/enable via lifecycle endpoints.
     * 'auto' — POSSUM auto-connects devices on startup (legacy Target behavior).
     */
    private String lifecycle = "manual";

    private Map<String, DeviceConfig> device = new HashMap<>();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(String lifecycle) {
        this.lifecycle = lifecycle;
    }

    public boolean isManualLifecycle() {
        return !"auto".equalsIgnoreCase(lifecycle);
    }

    public Map<String, DeviceConfig> getDevice() {
        return device;
    }

    public void setDevice(Map<String, DeviceConfig> device) {
        this.device = device;
    }

    /**
     * Get config for a specific device key, or return defaults if not configured.
     */
    public DeviceConfig getDeviceConfig(String deviceKey) {
        return device.getOrDefault(deviceKey, new DeviceConfig());
    }

    public static class DeviceConfig {
        private boolean enabled = true;
        private String logicalName;
        private Map<String, DeviceConfig> subDevices;

        /** Dynamic list of drawer entries (used by cashdrawer). */
        private List<DrawerEntry> drawers;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getLogicalName() {
            return logicalName;
        }

        public void setLogicalName(String logicalName) {
            this.logicalName = logicalName;
        }

        public Map<String, DeviceConfig> getSubDevices() {
            return subDevices;
        }

        public void setSubDevices(Map<String, DeviceConfig> subDevices) {
            this.subDevices = subDevices;
        }

        public List<DrawerEntry> getDrawers() {
            return drawers;
        }

        public void setDrawers(List<DrawerEntry> drawers) {
            this.drawers = drawers;
        }

        /**
         * Returns true if logicalName is set and non-empty.
         */
        public boolean hasLogicalName() {
            return logicalName != null && !logicalName.isEmpty();
        }
    }

    /**
     * A single drawer entry in the dynamic drawers list.
     */
    public static class DrawerEntry {
        private String logicalName;
        private boolean enabled = true;

        public DrawerEntry() {}

        public DrawerEntry(String logicalName, boolean enabled) {
            this.logicalName = logicalName;
            this.enabled = enabled;
        }

        public String getLogicalName() {
            return logicalName;
        }

        public void setLogicalName(String logicalName) {
            this.logicalName = logicalName;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean hasLogicalName() {
            return logicalName != null && !logicalName.isEmpty();
        }
    }
}
