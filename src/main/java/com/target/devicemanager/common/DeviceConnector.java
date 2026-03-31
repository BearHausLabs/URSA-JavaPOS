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

    // Skip claim -- for shared devices (e.g. Keylock)
    private boolean skipClaim = false;

    // Lifecycle state tracking
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

    // --- skipClaim ---

    public void setSkipClaim(boolean skipClaim) {
        this.skipClaim = skipClaim;
    }

    public boolean isSkipClaim() {
        return this.skipClaim;
    }

    // --- Lifecycle state ---

    public DeviceLifecycleState getLifecycleState() {
        return this.lifecycleState;
    }

    String getConnectedDeviceName() {
        return this.connectedDeviceName;
    }

    // --- Individual lifecycle methods ---

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

    // --- Registry helpers ---

    /**
     * Returns all logical names from the JCL registry matching this device's category
     * (and optional custom filter). Used by discovery endpoints and URSA to know
     * what devices are available to open.
     */
    public List<String> getLogicalNamesForDeviceType() {
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
                    Object categoryObj = x.getPropertyValue("deviceCategory");
                    if (categoryObj == null) return false;
                    String deviceCategory = categoryObj.toString();
                    Class<? extends BaseJposControl> deviceClass = device.getClass();
                    return deviceCategory.equals(deviceClass.getSimpleName());
                })
                .filter(x -> {
                    if (customFilter == null) return true;

                    String filterKey = customFilter.getKey();
                    String filterValue = customFilter.getValue();
                    if (filterKey == null || filterValue == null) return true;

                    Object filterObj = x.getPropertyValue(filterKey);
                    if (filterObj == null) {
                        // Log a warning for Scanner entries missing deviceType -- common
                        // when vendor jpos.xml wasn't merged through configure-io.ps1
                        String entryName = x.getLogicalName();
                        log.failure("entry '" + entryName + "' missing property '" + filterKey +
                                "' -- skipped (vendor jpos.xml may need deviceType injection)", 9, null);
                        return false;
                    }
                    return filterValue.equals(filterObj.toString());
                })
                .map(SimpleEntry::getLogicalName)
                .filter(Objects::nonNull)
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
        // Return the first actual logical name from devcon.xml, not the
        // category label or custom filter value. The lifecycle endpoint
        // uses this as the reported logical_name, and callers (Electron
        // splash) pass it back to /lifecycle/open -- it must be a real
        // JCL registry entry name (e.g., "POSPrinter1" not "POSPrinter").
        List<String> names = getLogicalNamesForDeviceType();
        if (!names.isEmpty()) {
            return names.get(0);
        }
        // Fallback: class name (e.g., "POSPrinter") -- may not match devcon.xml
        return device.getClass().getSimpleName();
    }
}
