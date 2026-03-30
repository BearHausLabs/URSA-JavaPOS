package com.target.devicemanager.components.tone;

import com.target.devicemanager.common.*;
import com.target.devicemanager.components.tone.simulator.SimulatedJposTone;
import com.target.devicemanager.configuration.ApplicationConfig;
import jpos.ToneIndicator;
import jpos.config.JposEntryRegistry;
import jpos.loader.JposServiceLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.locks.ReentrantLock;

@Configuration
@Profile({"local", "dev", "prod"})
class ToneConfig {
    private final SimulatedJposTone simulatedTone;
    private final ApplicationConfig applicationConfig;

    @Autowired
    ToneConfig(ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
        this.simulatedTone = new SimulatedJposTone();
    }

    @Bean
    public ToneManager getToneManager() {
        DynamicDevice<? extends ToneIndicator> dynamicTone;
        JposEntryRegistry deviceRegistry = JposServiceLoader.getManager().getEntryRegistry();

        if (applicationConfig.IsSimulationMode()) {
            dynamicTone = new SimulatedDynamicDevice<>(simulatedTone, new DevicePower(), new DeviceConnector<>(simulatedTone, deviceRegistry));
        } else {
            ToneIndicator toneIndicator = new ToneIndicator();
            DeviceConnector<ToneIndicator> connector = new DeviceConnector<>(toneIndicator, deviceRegistry);
            dynamicTone = new DynamicDevice<>(toneIndicator, new DevicePower(), connector);
        }

        ToneManager toneManager = new ToneManager(
                new ToneDevice(dynamicTone),
                new ReentrantLock());

        DeviceAvailabilitySingleton.getDeviceAvailabilitySingleton().setToneManager(toneManager);
        return toneManager;
    }

    @Bean
    SimulatedJposTone getSimulatedTone() {
        return simulatedTone;
    }
}
