package com.target.devicemanager.components.linedisplay;

import com.target.devicemanager.common.*;
import com.target.devicemanager.components.linedisplay.simulator.SimulatedJposLineDisplay;
import com.target.devicemanager.configuration.ApplicationConfig;
import jpos.LineDisplay;
import jpos.config.JposEntryRegistry;
import jpos.loader.JposServiceLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile({"local","dev","prod"})
@ConditionalOnProperty(name = "possum.device.linedisplay.enabled", havingValue = "true", matchIfMissing = true)
class LineDisplayConfig {

    private final SimulatedJposLineDisplay simulatedLineDisplay;
    private final ApplicationConfig applicationConfig;
    private final WorkstationConfig workstationConfig;

    @Autowired
    LineDisplayConfig(ApplicationConfig applicationConfig, WorkstationConfig workstationConfig) {
        this.applicationConfig = applicationConfig;
        this.workstationConfig = workstationConfig;
        this.simulatedLineDisplay = new SimulatedJposLineDisplay();
    }

    @Bean
    public LineDisplayManager getLineDisplayManager() {
        DynamicDevice<LineDisplay> dynamicLineDisplay;
        JposEntryRegistry deviceRegistry = JposServiceLoader.getManager().getEntryRegistry();
        WorkstationConfig.DeviceConfig deviceConfig = workstationConfig.getDeviceConfig("linedisplay");

        if (applicationConfig.IsSimulationMode()) {
            dynamicLineDisplay = new DynamicDevice<>(simulatedLineDisplay, new DevicePower(), new DeviceConnector<>(simulatedLineDisplay, deviceRegistry));
        } else {
            LineDisplay lineDisplay = new LineDisplay();
            DeviceConnector<LineDisplay> connector = new DeviceConnector<>(lineDisplay, deviceRegistry);
            if (deviceConfig.hasLogicalName()) {
                connector.setPreferredLogicalName(deviceConfig.getLogicalName());
                connector.setSkipTestCycle(true);
            }
            dynamicLineDisplay = new DynamicDevice<>(lineDisplay, new DevicePower(), connector);
        }

        LineDisplayManager lineDisplayManager = new LineDisplayManager(
                new LineDisplayDevice(dynamicLineDisplay));

        DeviceAvailabilitySingleton.getDeviceAvailabilitySingleton().setLineDisplayManager(lineDisplayManager);
        if (workstationConfig.isManualLifecycle()) {
            lineDisplayManager.setManualMode(true);
        }
        return lineDisplayManager;
    }

    @Bean
    SimulatedJposLineDisplay getSimulatedLineDisplay() {
        return simulatedLineDisplay;
    }
}
