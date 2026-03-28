package com.target.devicemanager.components.tone;

import com.target.devicemanager.common.*;
import com.target.devicemanager.components.tone.simulator.SimulatedJposTone;
import com.target.devicemanager.configuration.ApplicationConfig;
import jpos.ToneIndicator;
import jpos.config.JposEntryRegistry;
import jpos.loader.JposServiceLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.locks.ReentrantLock;

@Configuration
@Profile({"local", "dev", "prod"})
@ConditionalOnProperty(name = "possum.device.tone.enabled", havingValue = "true", matchIfMissing = true)
class ToneConfig {
    private final SimulatedJposTone simulatedTone;
    private final ApplicationConfig applicationConfig;
    private final WorkstationConfig workstationConfig;

    @Autowired
    ToneConfig(ApplicationConfig applicationConfig, WorkstationConfig workstationConfig) {
        this.applicationConfig = applicationConfig;
        this.workstationConfig = workstationConfig;
        this.simulatedTone = new SimulatedJposTone();
    }

    @Bean
    public ToneManager getToneManager() {
        DynamicDevice<? extends ToneIndicator> dynamicTone;
        JposEntryRegistry deviceRegistry = JposServiceLoader.getManager().getEntryRegistry();
        WorkstationConfig.DeviceConfig deviceConfig = workstationConfig.getDeviceConfig("tone");

        if (applicationConfig.IsSimulationMode()) {
            dynamicTone = new SimulatedDynamicDevice<>(simulatedTone, new DevicePower(), new DeviceConnector<>(simulatedTone, deviceRegistry));
        } else {
            ToneIndicator toneIndicator = new ToneIndicator();
            DeviceConnector<ToneIndicator> connector = new DeviceConnector<>(toneIndicator, deviceRegistry);
            if (deviceConfig.hasLogicalName()) {
                connector.setPreferredLogicalName(deviceConfig.getLogicalName());
                connector.setSkipTestCycle(true);
            }
            dynamicTone = new DynamicDevice<>(toneIndicator, new DevicePower(), connector);
        }

        ToneManager toneManager = new ToneManager(
                new ToneDevice(dynamicTone),
                new ReentrantLock());

        DeviceAvailabilitySingleton.getDeviceAvailabilitySingleton().setToneManager(toneManager);
        if (workstationConfig.isManualLifecycle()) {
            toneManager.setManualMode(true);
        }
        return toneManager;
    }

    @Bean
    SimulatedJposTone getSimulatedTone() {
        return simulatedTone;
    }
}
