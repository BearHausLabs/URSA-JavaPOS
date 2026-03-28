package com.target.devicemanager.components.cashdrawer;

import com.target.devicemanager.common.DeviceLifecycleResponse;
import com.target.devicemanager.common.StructuredEventLogger;
import com.target.devicemanager.common.entities.*;
import com.target.devicemanager.components.cashdrawer.entities.CashDrawerError;
import jpos.JposException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;

@Profile({"local", "dev", "prod"})
@EnableScheduling
@EnableCaching
public class CashDrawerManager {

    @Autowired
    private CacheManager cacheManager;

    private final List<? extends CashDrawerDevice> cashDrawers;
    private final Lock cashDrawerLock;
    private ConnectEnum connectStatus = ConnectEnum.FIRST_CONNECT;
    private boolean manualMode = false;
    private static final Logger LOGGER = LoggerFactory.getLogger(CashDrawerManager.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getCashDrawerServiceName(), "CashDrawerManager", LOGGER);

    public CashDrawerManager(List<? extends CashDrawerDevice> cashDrawers, Lock cashDrawerLock) {
        this(cashDrawers, cashDrawerLock, null);
    }

    public CashDrawerManager(List<? extends CashDrawerDevice> cashDrawers, Lock cashDrawerLock, CacheManager cacheManager) {
        if (cashDrawers == null || cashDrawers.isEmpty()) {
            throw new IllegalArgumentException("cashDrawers cannot be null or empty");
        }
        if (cashDrawerLock == null) {
            throw new IllegalArgumentException("cashDrawerLock cannot be null");
        }
        this.cashDrawers = cashDrawers;
        this.cashDrawerLock = cashDrawerLock;

        if (cacheManager != null) {
            this.cacheManager = cacheManager;
        }
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void connect() {
        if (manualMode) {
            return;
        }

        for (CashDrawerDevice drawer : cashDrawers) {
            if (drawer.tryLock()) {
                try {
                    drawer.connect();
                } finally {
                    drawer.unlock();
                }
            }
        }

        if (connectStatus == ConnectEnum.FIRST_CONNECT) {
            for (CashDrawerDevice drawer : cashDrawers) {
                if (!drawer.isConnected()) {
                    log.failure("Drawer " + drawer.getDrawerId() + " Cash Drawer Failed to Connect", 17, null);
                }
            }
            connectStatus = ConnectEnum.CHECK_HEALTH;
        }
    }

    /**
     * Reconnect all drawers.
     */
    public void reconnectDevice() throws DeviceException {
        for (CashDrawerDevice drawer : cashDrawers) {
            reconnectDrawer(drawer);
        }
    }

    /**
     * Reconnect a specific drawer by ID (1-based).
     */
    public void reconnectDevice(int drawerId) throws DeviceException {
        CashDrawerDevice drawer = findDrawer(drawerId);
        if (drawer == null) {
            throw new DeviceException(DeviceError.DEVICE_OFFLINE);
        }
        reconnectDrawer(drawer);
    }

    private void reconnectDrawer(CashDrawerDevice drawer) throws DeviceException {
        if (drawer.tryLock()) {
            try {
                drawer.disconnect();
                if (!drawer.connect()) {
                    throw new DeviceException(DeviceError.DEVICE_OFFLINE);
                }
            } finally {
                drawer.unlock();
            }
        } else {
            throw new DeviceException(DeviceError.DEVICE_BUSY);
        }
    }

    /**
     * Open drawer 1 (backward compatible -- no args).
     */
    public void openCashDrawer() throws DeviceException {
        openCashDrawer(1);
    }

    /**
     * Open a specific drawer by ID (1-based).
     */
    public void openCashDrawer(int drawerId) throws DeviceException {
        if (!cashDrawerLock.tryLock()) {
            throw new DeviceException(CashDrawerError.DEVICE_BUSY);
        }
        try {
            CashDrawerDevice drawer = findDrawer(drawerId);
            if (drawer == null) {
                throw new DeviceException(DeviceError.DEVICE_OFFLINE);
            }
            drawer.openCashDrawer();
        } catch (JposException jposException) {
            throw new DeviceException(jposException);
        } finally {
            cashDrawerLock.unlock();
        }
    }

    /**
     * Get health of drawer 1 (backward compatible).
     */
    public DeviceHealthResponse getHealth() {
        return getHealth(1);
    }

    /**
     * Get health of a specific drawer by ID.
     */
    public DeviceHealthResponse getHealth(int drawerId) {
        CashDrawerDevice drawer = findDrawer(drawerId);
        DeviceHealthResponse deviceHealthResponse;
        if (drawer != null && drawer.isConnected()) {
            deviceHealthResponse = new DeviceHealthResponse(drawer.getDeviceName(), DeviceHealth.READY);
        } else {
            String name = drawer != null ? drawer.getDeviceName() : "Drawer_" + drawerId + " CashDrawer";
            deviceHealthResponse = new DeviceHealthResponse(name, DeviceHealth.NOTREADY);
        }
        try {
            Objects.requireNonNull(cacheManager.getCache("cashDrawerHealth")).put("health_" + drawerId, deviceHealthResponse);
        } catch (Exception exception) {
            log.failure("getCache(cashDrawerHealth) Failed", 17, exception);
        }
        return deviceHealthResponse;
    }

    /**
     * Get health of ALL drawers as a list.
     */
    public List<DeviceHealthResponse> getAllHealth() {
        List<DeviceHealthResponse> responses = new ArrayList<>();
        for (CashDrawerDevice drawer : cashDrawers) {
            if (drawer.isConnected()) {
                responses.add(new DeviceHealthResponse(drawer.getDeviceName(), DeviceHealth.READY));
            } else {
                responses.add(new DeviceHealthResponse(drawer.getDeviceName(), DeviceHealth.NOTREADY));
            }
        }
        try {
            Objects.requireNonNull(cacheManager.getCache("cashDrawerHealth")).put("health", responses);
        } catch (Exception exception) {
            log.failure("getCache(cashDrawerHealth) Failed", 17, exception);
        }
        return responses;
    }

    public DeviceHealthResponse getStatus() {
        try {
            if (cacheManager != null && Objects.requireNonNull(cacheManager.getCache("cashDrawerHealth")).get("health_1") != null) {
                if (connectStatus == ConnectEnum.CHECK_HEALTH) {
                    connectStatus = ConnectEnum.HEALTH_UPDATED;
                    return getHealth();
                }
                return (DeviceHealthResponse) Objects.requireNonNull(cacheManager.getCache("cashDrawerHealth")).get("health_1").get();
            } else {
                log.success("Not able to retrieve from cache, checking getHealth()", 5);
                return getHealth();
            }
        } catch (Exception exception) {
            return getHealth();
        }
    }

    /**
     * Returns the number of configured drawers.
     */
    public int getDrawerCount() {
        return cashDrawers.size();
    }

    // --- Lifecycle methods ---

    private CashDrawerDevice findDrawer(int drawerId) {
        for (CashDrawerDevice drawer : cashDrawers) {
            if (drawer.getDrawerId() == drawerId) {
                return drawer;
            }
        }
        return null;
    }

    public void openDevice(String logicalName, int drawerId) throws JposException {
        manualMode = true;
        CashDrawerDevice drawer = findDrawer(drawerId);
        if (drawer == null) {
            throw new JposException(jpos.JposConst.JPOS_E_NOEXIST, "Cash drawer not found: drawer " + drawerId);
        }
        drawer.getDynamicDevice().openDevice(logicalName);
        log.logDeviceEvent("lifecycle_open", "CashDrawer/" + drawerId, logicalName);
    }

    public void claimDevice(int timeout, int drawerId) throws JposException {
        manualMode = true;
        CashDrawerDevice drawer = findDrawer(drawerId);
        if (drawer == null) {
            throw new JposException(jpos.JposConst.JPOS_E_NOEXIST, "Cash drawer not found: drawer " + drawerId);
        }
        drawer.getDynamicDevice().claimDevice(timeout);
        log.logDeviceEvent("lifecycle_claim", "CashDrawer/" + drawerId, drawer.getDeviceName());
    }

    public void enableDevice(int drawerId) throws JposException {
        manualMode = true;
        CashDrawerDevice drawer = findDrawer(drawerId);
        if (drawer == null) {
            throw new JposException(jpos.JposConst.JPOS_E_NOEXIST, "Cash drawer not found: drawer " + drawerId);
        }
        drawer.getDynamicDevice().enableDevice();
        log.logDeviceEvent("lifecycle_enable", "CashDrawer/" + drawerId, drawer.getDeviceName());
    }

    public void disableDevice(int drawerId) throws JposException {
        manualMode = true;
        CashDrawerDevice drawer = findDrawer(drawerId);
        if (drawer == null) {
            throw new JposException(jpos.JposConst.JPOS_E_NOEXIST, "Cash drawer not found: drawer " + drawerId);
        }
        drawer.getDynamicDevice().disableDevice();
        log.logDeviceEvent("lifecycle_disable", "CashDrawer/" + drawerId, drawer.getDeviceName());
    }

    public void releaseDevice(int drawerId) throws JposException {
        manualMode = true;
        CashDrawerDevice drawer = findDrawer(drawerId);
        if (drawer == null) {
            throw new JposException(jpos.JposConst.JPOS_E_NOEXIST, "Cash drawer not found: drawer " + drawerId);
        }
        drawer.getDynamicDevice().releaseDevice();
        log.logDeviceEvent("lifecycle_release", "CashDrawer/" + drawerId, drawer.getDeviceName());
    }

    public void closeDevice(int drawerId) throws JposException {
        manualMode = true;
        CashDrawerDevice drawer = findDrawer(drawerId);
        if (drawer == null) {
            throw new JposException(jpos.JposConst.JPOS_E_NOEXIST, "Cash drawer not found: drawer " + drawerId);
        }
        drawer.getDynamicDevice().closeDevice();
        log.logDeviceEvent("lifecycle_close", "CashDrawer/" + drawerId, drawer.getDeviceName());
    }

    public void setAutoMode() {
        manualMode = false;
        log.logDeviceEvent("lifecycle_auto", "CashDrawer", "all");
    }

    public void setManualMode(boolean manual) {
        manualMode = manual;
    }

    public List<DeviceLifecycleResponse> getLifecycleStatus() {
        List<DeviceLifecycleResponse> responses = new ArrayList<>();
        for (CashDrawerDevice drawer : cashDrawers) {
            responses.add(new DeviceLifecycleResponse(
                    drawer.getDynamicDevice().getLifecycleState(),
                    drawer.getDeviceName(),
                    manualMode,
                    "CashDrawer/" + drawer.getDrawerId()
            ));
        }
        return responses;
    }

    public boolean isManualMode() {
        return manualMode;
    }
}
