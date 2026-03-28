package com.target.devicemanager.components.scale;

import com.target.devicemanager.common.*;
import com.target.devicemanager.components.scale.simulator.SimulatedJposScale;
import com.target.devicemanager.configuration.ApplicationConfig;

import jpos.config.JposEntryRegistry;
import jpos.Scale;
import jpos.loader.JposServiceLoader;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.CopyOnWriteArrayList;

@Configuration
@ConditionalOnProperty(name = "possum.device.scale.enabled", havingValue = "true", matchIfMissing = true)
class ScaleConfig {
    private final SimulatedJposScale simulatedJposScale;
    private final ApplicationConfig applicationConfig;
    private final WorkstationConfig workstationConfig;

    @Autowired
    ScaleConfig(ApplicationConfig applicationConfig, WorkstationConfig workstationConfig) {
        this.applicationConfig = applicationConfig;
        this.workstationConfig = workstationConfig;
        this.simulatedJposScale = new SimulatedJposScale();
    }

    @Bean
    public ScaleManager getScaleManager() {
        DynamicDevice<Scale> dynamicScale;
        JposEntryRegistry deviceRegistry = JposServiceLoader.getManager().getEntryRegistry();
        WorkstationConfig.DeviceConfig deviceConfig = workstationConfig.getDeviceConfig("scale");

        if (applicationConfig.IsSimulationMode()) {
            dynamicScale = new SimulatedDynamicDevice<>(simulatedJposScale, new DevicePower(), new DeviceConnector<>(simulatedJposScale, deviceRegistry));
        } else {
            Scale scale = new Scale();
            DeviceConnector<Scale> connector = new DeviceConnector<>(scale, deviceRegistry);
            if (deviceConfig.hasLogicalName()) {
                connector.setPreferredLogicalName(deviceConfig.getLogicalName());
                connector.setSkipTestCycle(true);
            }
            dynamicScale = new DynamicDevice<>(scale, new DevicePower(), connector);
        }

        ScaleManager scaleManager = new ScaleManager(
                new ScaleDevice(dynamicScale, new CopyOnWriteArrayList<>(), new CopyOnWriteArrayList<>()),
                new CopyOnWriteArrayList<>(),
                new CopyOnWriteArrayList<>());

        DeviceAvailabilitySingleton.getDeviceAvailabilitySingleton().setScaleManager(scaleManager);
        if (workstationConfig.isManualLifecycle()) {
            scaleManager.setManualMode(true);
        }
        return scaleManager;
    }

    @Bean
    SimulatedJposScale getSimulatedJposScale() {
        return simulatedJposScale;
    }

}
