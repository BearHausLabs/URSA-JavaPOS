package com.target.devicemanager.components.linedisplay;

import com.target.devicemanager.common.DeviceLifecycleResponse;
import com.target.devicemanager.common.DeviceLifecycleState;
import com.target.devicemanager.common.StructuredEventLogger;
import com.target.devicemanager.common.entities.*;
import com.target.devicemanager.common.events.ConnectionEvent;
import com.target.devicemanager.common.events.ConnectionEventListener;
import jpos.JposException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Profile;

import java.util.Objects;

@Profile({"local","dev","prod"})
@EnableCaching
public class LineDisplayManager implements ConnectionEventListener {

    @Autowired
    private CacheManager cacheManager;

    private final LineDisplayDevice lineDisplayDevice;
    private static final Logger LOGGER = LoggerFactory.getLogger(LineDisplayManager.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getLineDisplayServiceName(), "LineDisplayManager", LOGGER);

    public LineDisplayManager(LineDisplayDevice lineDisplayDevice) {
        this(lineDisplayDevice, null);
    }

    public LineDisplayManager(LineDisplayDevice lineDisplayDevice, CacheManager cacheManager) {
        if (lineDisplayDevice == null) {
            throw new IllegalArgumentException("lineDisplayDevice cannot be null");
        }
        this.lineDisplayDevice = lineDisplayDevice;
        this.lineDisplayDevice.addConnectionEventListener(this);

        if(cacheManager != null) {
            this.cacheManager = cacheManager;
        }
    }

    public void reconnectDevice() throws DeviceException {
        if (lineDisplayDevice.tryLock()) {
            try {
                lineDisplayDevice.disconnect();
            } finally {
                lineDisplayDevice.unlock();
            }
        }
        else {
            throw new DeviceException(DeviceError.DEVICE_BUSY);
        }
    }

    public void displayLine(String line1, String line2) throws DeviceException {
        String line1formatted = formatLineText(line1);
        String line2formatted = formatLineText(line2);
        log.success("displayLine(): line1=" + line1formatted + " line2=" + line2formatted, 1);
        try {
            lineDisplayDevice.displayLine(line1formatted, line2formatted);
        } catch (JposException jposException) {
            DeviceException lineDisplayException =  new DeviceException(jposException);
            throw lineDisplayException;
        }
    }

    @Override
    public void connectionEventOccurred(ConnectionEvent connectionEvent) {}

    private String formatLineText(String lineText) {
        //right pad line to 20 characters (-20)
        //trim to 20 characters (.20)
        lineText = lineText == null ? "" : lineText;
        return String.format("%-20.20s", lineText);
    }

    public DeviceHealthResponse getHealth() {
        DeviceHealthResponse deviceHealthResponse;
        if (lineDisplayDevice.isConnected()) {
            deviceHealthResponse = new DeviceHealthResponse(lineDisplayDevice.getDeviceName(), DeviceHealth.READY);
        } else {
            deviceHealthResponse = new DeviceHealthResponse(lineDisplayDevice.getDeviceName(), DeviceHealth.NOTREADY);
        }
        try {
            Objects.requireNonNull(cacheManager.getCache("lineDisplayHealth")).put("health", deviceHealthResponse);
        } catch (Exception exception) {
            log.failure("getCache(lineDisplayHealth) Failed", 17, exception);
        }
        return deviceHealthResponse;
    }

    public DeviceHealthResponse getStatus() {
        try {
            if (cacheManager != null && Objects.requireNonNull(cacheManager.getCache("lineDisplayHealth")).get("health") != null) {
                return (DeviceHealthResponse) Objects.requireNonNull(cacheManager.getCache("lineDisplayHealth")).get("health").get();
            } else {
                log.success("Not able to retrieve from cache, checking getHealth()", 5);
                return getHealth();
            }
        } catch (Exception exception) {
            return getHealth();
        }
    }

    // --- Lifecycle methods ---

    public void openDevice(String logicalName) throws JposException {
        lineDisplayDevice.getDynamicDevice().openDevice(logicalName);
        log.logDeviceEvent("lifecycle_open", "LineDisplay", logicalName);
    }

    public void claimDevice(int timeout) throws JposException {
        lineDisplayDevice.getDynamicDevice().claimDevice(timeout);
        log.logDeviceEvent("lifecycle_claim", "LineDisplay", lineDisplayDevice.getDeviceName());
    }

    public void enableDevice() throws JposException {
        lineDisplayDevice.getDynamicDevice().enableDevice();
        log.logDeviceEvent("lifecycle_enable", "LineDisplay", lineDisplayDevice.getDeviceName());
    }

    public void disableDevice() throws JposException {
        lineDisplayDevice.getDynamicDevice().disableDevice();
        log.logDeviceEvent("lifecycle_disable", "LineDisplay", lineDisplayDevice.getDeviceName());
    }

    public void releaseDevice() throws JposException {
        lineDisplayDevice.getDynamicDevice().releaseDevice();
        log.logDeviceEvent("lifecycle_release", "LineDisplay", lineDisplayDevice.getDeviceName());
    }

    public void closeDevice() throws JposException {
        lineDisplayDevice.getDynamicDevice().closeDevice();
        log.logDeviceEvent("lifecycle_close", "LineDisplay", lineDisplayDevice.getDeviceName());
    }

    public void setAutoMode() {
        // No-op: URSA always owns device lifecycle.
        log.logDeviceEvent("lifecycle_auto_noop", "LineDisplay", lineDisplayDevice.getDeviceName());
    }

    public void setManualMode(boolean manual) {
        // No-op: always in manual mode.
    }

    public DeviceLifecycleResponse getLifecycleStatus() {
        return new DeviceLifecycleResponse(
                lineDisplayDevice.getDynamicDevice().getLifecycleState(),
                lineDisplayDevice.getDeviceName(),
                "LineDisplay"
        );
    }

    public boolean isManualMode() {
        return true;
    }
}
