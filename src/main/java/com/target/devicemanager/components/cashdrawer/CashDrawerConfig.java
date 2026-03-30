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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Phaser;
import java.util.concurrent.locks.ReentrantLock;

@Configuration
@Profile({"local", "dev", "prod"})
class CashDrawerConfig {
    private final ApplicationConfig applicationConfig;
    private static final Logger LOGGER = LoggerFactory.getLogger(CashDrawerConfig.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getCashDrawerServiceName(), "CashDrawerConfig", LOGGER);

    // Hold simulated drawers for bean exposure
    private final List<SimulatedJposCashDrawer> simulatedDrawers = new ArrayList<>();

    @Autowired
    CashDrawerConfig(ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
    }

    List<CashDrawerDevice> getCashDrawers() {
        List<CashDrawerDevice> drawers = new ArrayList<>();
        JposEntryRegistry deviceRegistry = JposServiceLoader.getManager().getEntryRegistry();

        // Create a single drawer instance. URSA will call lifecycle endpoints
        // to open/claim/enable the specific logical name it wants.
        if (applicationConfig.IsSimulationMode()) {
            SimulatedJposCashDrawer simDrawer = new SimulatedJposCashDrawer(1);
            simulatedDrawers.add(simDrawer);
            DeviceConnector<SimulatedJposCashDrawer> connector = new DeviceConnector<>(simDrawer, deviceRegistry);
            drawers.add(new CashDrawerDevice(
                    new SimulatedDynamicDevice<>(simDrawer, new DevicePower(), connector),
                    new CashDrawerDeviceListener(new EventSynchronizer(new Phaser(1))),
                    1,
                    ""
            ));
        } else {
            CashDrawer cashDrawer = new CashDrawer();
            DeviceConnector<CashDrawer> connector = new DeviceConnector<>(cashDrawer, deviceRegistry);
            drawers.add(new CashDrawerDevice(
                    new DynamicDevice<>(cashDrawer, new DevicePower(), connector),
                    new CashDrawerDeviceListener(new EventSynchronizer(new Phaser(1))),
                    1,
                    ""
            ));
        }

        log.success("Configured " + drawers.size() + " cash drawer(s)", 9);
        return drawers;
    }

    @Bean
    public CashDrawerManager getCashDrawerManager() {
        CashDrawerManager cashDrawerManager = new CashDrawerManager(getCashDrawers(), new ReentrantLock());

        DeviceAvailabilitySingleton.getDeviceAvailabilitySingleton().setCashDrawerManager(cashDrawerManager);
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
