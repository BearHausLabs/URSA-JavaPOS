package com.target.devicemanager.common;

import jpos.BaseJposControl;
import jpos.JposException;
import jpos.config.JposEntryRegistry;
import jpos.config.simple.SimpleEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class DeviceConnector<T extends BaseJposControl> {

    private final T device;
    private final AbstractMap.SimpleEntry<String, String> customFilter;
    private final JposEntryRegistry deviceRegistry;
    private String connectedDeviceName;
    private static final int CLAIM_TIMEOUT_IN_MSEC = 30000;
    private final int RETRY_REGISTRY_LOAD = 5;
    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceConnector.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getCommonServiceName(), "DeviceConnector", LOGGER);

    // Step 1a: preferred logical name — skip auto-discovery if set
    private String preferredLogicalName;

    // Step 1b: skip claim — for shared devices (e.g. Keylock)
    private boolean skipClaim = false;

    // Step 1c: skip test cycle — for devices that don't like enable/disable during discovery
    private boolean skipTestCycle = false;

    // Step 5b: lifecycle state tracking
    private DeviceLifecycleState lifecycleState = DeviceLifecycleState.CLOSED;


    public DeviceConnector(T device, JposEntryRegistry deviceRegistry) {
        this(device, deviceRegistry, null);
    }

    public DeviceConnector(T device, JposEntryRegistry deviceRegistry, AbstractMap.SimpleEntry<String, String> customFilter) {
        if (device == null) {
            throw new IllegalArgumentException("device cannot be null");
        }
        if (deviceRegistry == null) {
            throw new IllegalArgumentException("deviceRegistry cannot be null");
        }
        this.device = device;
        this.customFilter = customFilter;
        this.deviceRegistry = deviceRegistry;
        this.connectedDeviceName = getDefaultDeviceName();
    }

    // --- Step 1a: preferredLogicalName ---

    public void setPreferredLogicalName(String preferredLogicalName) {
        this.preferredLogicalName = preferredLogicalName;
    }

    public String getPreferredLogicalName() {
        return this.preferredLogicalName;
    }

    // --- Step 1b: skipClaim ---

    public void setSkipClaim(boolean skipClaim) {
        this.skipClaim = skipClaim;
    }

    public boolean isSkipClaim() {
        return this.skipClaim;
    }

    // --- Step 1c: skipTestCycle ---

    public void setSkipTestCycle(boolean skipTestCycle) {
        this.skipTestCycle = skipTestCycle;
    }

    public boolean isSkipTestCycle() {
        return this.skipTestCycle;
    }

    // --- Step 5b: lifecycle state ---

    public DeviceLifecycleState getLifecycleState() {
        return this.lifecycleState;
    }

    // --- Discovery (modified for Step 1a) ---

    boolean discoverConnectedDevice() {
        // Step 1a: if a preferred logical name is set, try it first
        if (preferredLogicalName != null && !preferredLogicalName.isEmpty()) {
            log.success("attempting preferred logical name '" + preferredLogicalName + "'", 5);
            clearDeviceCache();
            boolean isConnected = connect(preferredLogicalName);
            if (isConnected) {
                log.success("device found via preferred name '" + connectedDeviceName + "'", 9);
                return true;
            }
            log.failure("preferred logical name '" + preferredLogicalName + "' failed, falling back to auto-discovery", 13, null);
        }

        List<String> configNames = getLogicalNamesForDeviceType();
        for (String configName : configNames) {
            clearDeviceCache(); //this clears any caches that exist (both datalogic and ncr have caches that need to get cleared)
            boolean isConnected = connect(configName);
            if (isConnected) {
                log.success("device found '" + connectedDeviceName + "'", 9);
                return true;
            }
        }
        return false;
    }

    String getConnectedDeviceName() {
        return this.connectedDeviceName;
    }

    private void clearDeviceCache() {
        synchronized (device) {
            try {
                device.setDeviceEnabled(false);
            } catch (Exception exception) {
                log.failure("failed to disable device '" + getDefaultDeviceName() + "'" + exception, 1, exception);
            }
            if (!skipClaim) {
                try {
                    device.release();
                } catch (Exception exception) {
                    log.failure("failed to release device '" + getDefaultDeviceName() + "'" + exception, 1, exception);
                }
            }
            try {
                device.close();
            } catch (Exception exception) {
                log.failure("failed to close device '" + getDefaultDeviceName() + "'" + exception, 1, exception);
            }
        }
        lifecycleState = DeviceLifecycleState.CLOSED;
    }

    private boolean connect(String configName) {
            synchronized (device) {
                try {
                    device.open(configName);
                } catch (JposException jposException){
                    log.failure("failed to open " + configName + " with error " + jposException.getErrorCode(), 17, jposException);
                    return false;
                }
                lifecycleState = DeviceLifecycleState.OPENED;

                // Step 1b: skip claim if configured
                if (!skipClaim) {
                    try {
                        device.claim(CLAIM_TIMEOUT_IN_MSEC);
                    } catch (JposException jposException){
                        log.failure("failed to claim " + configName + " with error " + jposException.getErrorCode(), 17, jposException);
                        return false;
                    }
                    lifecycleState = DeviceLifecycleState.CLAIMED;
                }

                // Step 1c: skip enable/disable test cycle if configured
                if (!skipTestCycle) {
                    //this is a test, some devices wont signal connected status until enabled
                    //then disable to put it back in the same state
                    try {
                        device.setDeviceEnabled(true);
                    } catch (JposException jposException){
                        log.failure("failed to enable " + configName + " with error " + jposException.getErrorCode(), 17, jposException);
                        return false;
                    }
                    try {
                        device.setDeviceEnabled(false);
                    } catch (JposException jposException){
                        log.failure("failed to disable " + configName + " with error " + jposException.getErrorCode(), 17, jposException);
                        return false;
                    }
                }

                this.connectedDeviceName = configName;
                log.success("successfully connected " + configName, 9);
                return true;
            }
    }

    // --- Step 5b: Individual lifecycle methods ---

    /**
     * JPOS open only. Transitions from CLOSED to OPENED.
     */
    public void openDevice(String logicalName) throws JposException {
        synchronized (device) {
            if (lifecycleState != DeviceLifecycleState.CLOSED) {
                throw new JposException(jpos.JposConst.JPOS_E_ILLEGAL, "Device must be CLOSED before open, current state: " + lifecycleState);
            }
            device.open(logicalName);
            this.connectedDeviceName = logicalName;
            lifecycleState = DeviceLifecycleState.OPENED;
            log.success("lifecycle open '" + logicalName + "'", 9);
        }
    }

    /**
     * JPOS claim. Transitions from OPENED to CLAIMED.
     */
    public void claimDevice(int timeout) throws JposException {
        synchronized (device) {
            if (lifecycleState != DeviceLifecycleState.OPENED) {
                throw new JposException(jpos.JposConst.JPOS_E_ILLEGAL, "Device must be OPENED before claim, current state: " + lifecycleState);
            }
            device.claim(timeout);
            lifecycleState = DeviceLifecycleState.CLAIMED;
            log.success("lifecycle claim '" + connectedDeviceName + "'", 9);
        }
    }

    /**
     * JPOS enable. Transitions from CLAIMED to ENABLED.
     */
    public void enableDevice() throws JposException {
        synchronized (device) {
            if (lifecycleState != DeviceLifecycleState.CLAIMED) {
                throw new JposException(jpos.JposConst.JPOS_E_ILLEGAL, "Device must be CLAIMED before enable, current state: " + lifecycleState);
            }
            device.setDeviceEnabled(true);
            lifecycleState = DeviceLifecycleState.ENABLED;
            log.success("lifecycle enable '" + connectedDeviceName + "'", 9);
        }
    }

    /**
     * JPOS disable. Transitions from ENABLED to CLAIMED.
     */
    public void disableDevice() throws JposException {
        synchronized (device) {
            if (lifecycleState != DeviceLifecycleState.ENABLED) {
                throw new JposException(jpos.JposConst.JPOS_E_ILLEGAL, "Device must be ENABLED before disable, current state: " + lifecycleState);
            }
            device.setDeviceEnabled(false);
            lifecycleState = DeviceLifecycleState.CLAIMED;
            log.success("lifecycle disable '" + connectedDeviceName + "'", 9);
        }
    }

    /**
     * JPOS release. Transitions from CLAIMED to OPENED.
     */
    public void releaseDevice() throws JposException {
        synchronized (device) {
            if (lifecycleState != DeviceLifecycleState.CLAIMED) {
                throw new JposException(jpos.JposConst.JPOS_E_ILLEGAL, "Device must be CLAIMED before release, current state: " + lifecycleState);
            }
            device.release();
            lifecycleState = DeviceLifecycleState.OPENED;
            log.success("lifecycle release '" + connectedDeviceName + "'", 9);
        }
    }

    /**
     * JPOS close. Transitions from OPENED to CLOSED.
     */
    public void closeDevice() throws JposException {
        synchronized (device) {
            if (lifecycleState != DeviceLifecycleState.OPENED) {
                throw new JposException(jpos.JposConst.JPOS_E_ILLEGAL, "Device must be OPENED before close, current state: " + lifecycleState);
            }
            device.close();
            lifecycleState = DeviceLifecycleState.CLOSED;
            log.success("lifecycle close '" + connectedDeviceName + "'", 9);
        }
    }

    // --- Existing private helpers ---

    private List<String> getLogicalNamesForDeviceType() {
        for(int i = 0; i < RETRY_REGISTRY_LOAD && deviceRegistry.getSize() == 0; i++) {
            log.success("registry load attempt " + (i + 1) + "/" + RETRY_REGISTRY_LOAD + " (size=" + deviceRegistry.getSize() + ")", 5);
            deviceRegistry.load();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException interruptedException) {
                //ignore
            }
        }

        ArrayList<SimpleEntry> list = Collections.list((Enumeration<SimpleEntry>)deviceRegistry.getEntries());
        String targetCategory = device.getClass().getSimpleName();
        List<String> result = list
                .stream()
                .filter(x -> {
                    String deviceCategory = x.getPropertyValue("deviceCategory").toString();
                    Class<? extends BaseJposControl> deviceClass = device.getClass();
                    return deviceCategory.equals(deviceClass.getSimpleName());
                })
                .filter(x -> {
                    if (customFilter == null) return true;

                    String customFilterValue = x.getPropertyValue(customFilter.getKey()).toString();
                    return customFilter.getValue().equals(customFilterValue);
                })
                .map(x -> x.getPropertyValue("logicalName").toString())
                .collect(Collectors.toCollection(ArrayList::new));

        if (result.isEmpty()) {
            log.failure("no logical names found for " + targetCategory +
                    " (registry has " + list.size() + " total entries)", 13, null);
        } else {
            log.success("found " + result.size() + " logical name(s) for " + targetCategory + ": " + result, 5);
        }

        return result;
    }

    private String getDefaultDeviceName() {
        if(this.customFilter != null) {
            return customFilter.getValue();
        }
        return device.getClass().getSimpleName();
    }
}
