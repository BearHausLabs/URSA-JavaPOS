package com.target.devicemanager.components.cashdrawer;

import com.target.devicemanager.common.*;
import com.target.devicemanager.components.cashdrawer.simulator.SimulatedJposCashDrawer;
import com.target.devicemanager.configuration.ApplicationConfig;
import jpos.CashDrawer;
import jpos.config.JposEntryRegistry;
import jpos.loader.JposServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Phaser;
import java.util.concurrent.locks.ReentrantLock;

@Configuration
@Profile({"local", "dev", "prod"})
@ConditionalOnProperty(name = "possum.device.cashdrawer.enabled", havingValue = "true", matchIfMissing = true)
class CashDrawerConfig {
    private final ApplicationConfig applicationConfig;
    private final WorkstationConfig workstationConfig;
    private static final Logger LOGGER = LoggerFactory.getLogger(CashDrawerConfig.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getCashDrawerServiceName(), "CashDrawerConfig", LOGGER);

    // Hold simulated drawers for bean exposure
    private final List<SimulatedJposCashDrawer> simulatedDrawers = new ArrayList<>();

    @Autowired
    CashDrawerConfig(ApplicationConfig applicationConfig, WorkstationConfig workstationConfig) {
        this.applicationConfig = applicationConfig;
        this.workstationConfig = workstationConfig;
    }

    List<CashDrawerDevice> getCashDrawers() {
        List<CashDrawerDevice> drawers = new ArrayList<>();
        JposEntryRegistry deviceRegistry = JposServiceLoader.getManager().getEntryRegistry();

        WorkstationConfig.DeviceConfig cashdrawerConfig = workstationConfig.getDeviceConfig("cashdrawer");
        List<WorkstationConfig.DrawerEntry> drawerEntries = cashdrawerConfig.getDrawers();

        // If no drawers list is configured, fall back to a single drawer with auto-discovery
        if (drawerEntries == null || drawerEntries.isEmpty()) {
            log.success("No drawers configured -- creating single auto-discovery drawer", 9);
            drawerEntries = new ArrayList<>();
            drawerEntries.add(new WorkstationConfig.DrawerEntry("", true));
        }

        int drawerId = 1;
        for (WorkstationConfig.DrawerEntry entry : drawerEntries) {
            if (!entry.isEnabled()) {
                log.success("Drawer " + drawerId + " is disabled, skipping", 5);
                drawerId++;
                continue;
            }

            if (applicationConfig.IsSimulationMode()) {
                SimulatedJposCashDrawer simDrawer = new SimulatedJposCashDrawer(drawerId);
                simulatedDrawers.add(simDrawer);
                DeviceConnector<SimulatedJposCashDrawer> connector = new DeviceConnector<>(simDrawer, deviceRegistry);
                if (entry.hasLogicalName()) {
                    connector.setPreferredLogicalName(entry.getLogicalName());
                }
                drawers.add(new CashDrawerDevice(
                        new SimulatedDynamicDevice<>(simDrawer, new DevicePower(), connector),
                        new CashDrawerDeviceListener(new EventSynchronizer(new Phaser(1))),
                        drawerId,
                        entry.getLogicalName()
                ));
            } else {
                CashDrawer cashDrawer = new CashDrawer();
                DeviceConnector<CashDrawer> connector = new DeviceConnector<>(cashDrawer, deviceRegistry);
                if (entry.hasLogicalName()) {
                    connector.setPreferredLogicalName(entry.getLogicalName());
                    connector.setSkipTestCycle(true);
                }
                drawers.add(new CashDrawerDevice(
                        new DynamicDevice<>(cashDrawer, new DevicePower(), connector),
                        new CashDrawerDeviceListener(new EventSynchronizer(new Phaser(1))),
                        drawerId,
                        entry.getLogicalName()
                ));
            }

            log.success("Configured drawer " + drawerId +
                    (entry.hasLogicalName() ? " with logicalName '" + entry.getLogicalName() + "'" : " with auto-discovery"), 9);
            drawerId++;
        }

        return drawers;
    }

    @Bean
    public CashDrawerManager getCashDrawerManager() {
        CashDrawerManager cashDrawerManager = new CashDrawerManager(getCashDrawers(), new ReentrantLock());

        DeviceAvailabilitySingleton.getDeviceAvailabilitySingleton().setCashDrawerManager(cashDrawerManager);
        if (workstationConfig.isManualLifecycle()) {
            cashDrawerManager.setManualMode(true);
        }
        return cashDrawerManager;
    }

    @Bean(name = "simulatedCashDrawers")
    List<SimulatedJposCashDrawer> getSimulatedCashDrawers() {
        // Ensure getCashDrawers() has been called so simulatedDrawers is populated
        if (simulatedDrawers.isEmpty() && applicationConfig.IsSimulationMode()) {
            getCashDrawers();
        }
        return simulatedDrawers;
    }
}
