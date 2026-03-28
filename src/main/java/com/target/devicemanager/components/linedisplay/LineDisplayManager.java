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
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;

@Profile({"local","dev","prod"})
@EnableScheduling
@EnableCaching
public class LineDisplayManager implements ConnectionEventListener {

    @Autowired
    private CacheManager cacheManager;

    private final LineDisplayDevice lineDisplayDevice;
    private ConnectEnum connectStatus = ConnectEnum.FIRST_CONNECT;
    private boolean manualMode = false;
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

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void connect() {
        if (manualMode) {
            return;
        }

        if (lineDisplayDevice.tryLock()) {
            try {
                lineDisplayDevice.connect();
            } finally {
                lineDisplayDevice.unlock();
            }
        }

        if (connectStatus == ConnectEnum.FIRST_CONNECT) {
            connectStatus = ConnectEnum.CHECK_HEALTH;
        }
    }

    public void reconnectDevice() throws DeviceException {
        if (lineDisplayDevice.tryLock()) {
            try {
                lineDisplayDevice.disconnect();
                if (!lineDisplayDevice.connect()) {
                    throw new DeviceException(DeviceError.DEVICE_OFFLINE);
                }
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
                if (connectStatus == ConnectEnum.CHECK_HEALTH) {
                    connectStatus = ConnectEnum.HEALTH_UPDATED;
                    return getHealth();
                }
                return (DeviceHealthResponse) Objects.requireNonNull(cacheManager.getCache("lineDisplayHealth")).get("health").get();
            } else {
                log.success("Not able to retrieve from cache, checking getHealth()", 5);
                return getHealth();
            }
        } catch (Exception exception) {
            return getHealth();
        }
    }

    // --- Step 5: Lifecycle methods ---

    public void openDevice(String logicalName) throws JposException {
        manualMode = true;
        lineDisplayDevice.getDynamicDevice().openDevice(logicalName);
        log.logDeviceEvent("lifecycle_open", "LineDisplay", logicalName);
    }

    public void claimDevice(int timeout) throws JposException {
        manualMode = true;
        lineDisplayDevice.getDynamicDevice().claimDevice(timeout);
        log.logDeviceEvent("lifecycle_claim", "LineDisplay", lineDisplayDevice.getDeviceName());
    }

    public void enableDevice() throws JposException {
        manualMode = true;
        lineDisplayDevice.getDynamicDevice().enableDevice();
        log.logDeviceEvent("lifecycle_enable", "LineDisplay", lineDisplayDevice.getDeviceName());
    }

    public void disableDevice() throws JposException {
        manualMode = true;
        lineDisplayDevice.getDynamicDevice().disableDevice();
        log.logDeviceEvent("lifecycle_disable", "LineDisplay", lineDisplayDevice.getDeviceName());
    }

    public void releaseDevice() throws JposException {
        manualMode = true;
        lineDisplayDevice.getDynamicDevice().releaseDevice();
        log.logDeviceEvent("lifecycle_release", "LineDisplay", lineDisplayDevice.getDeviceName());
    }

    public void closeDevice() throws JposException {
        manualMode = true;
        lineDisplayDevice.getDynamicDevice().closeDevice();
        log.logDeviceEvent("lifecycle_close", "LineDisplay", lineDisplayDevice.getDeviceName());
    }

    public void setAutoMode() {
        manualMode = false;
        log.logDeviceEvent("lifecycle_auto", "LineDisplay", lineDisplayDevice.getDeviceName());
    }

    public void setManualMode(boolean manual) {
        manualMode = manual;
    }

    public DeviceLifecycleResponse getLifecycleStatus() {
        return new DeviceLifecycleResponse(
                lineDisplayDevice.getDynamicDevice().getLifecycleState(),
                lineDisplayDevice.getDeviceName(),
                manualMode,
                "LineDisplay"
        );
    }

    public boolean isManualMode() {
        return manualMode;
    }
}
