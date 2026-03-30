package com.target.devicemanager.components.msr;

import com.target.devicemanager.common.*;
import com.target.devicemanager.components.msr.simulator.SimulatedJposMsr;
import com.target.devicemanager.configuration.ApplicationConfig;
import jpos.MSR;
import jpos.config.JposEntryRegistry;
import jpos.loader.JposServiceLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.Phaser;
import java.util.concurrent.locks.ReentrantLock;

@Configuration
@Profile({"local", "dev", "prod"})
class MsrConfig {
    private final SimulatedJposMsr simulatedMsr;
    private final ApplicationConfig applicationConfig;

    @Autowired
    MsrConfig(ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
        this.simulatedMsr = new SimulatedJposMsr();
    }

    @Bean
    public MsrManager getMsrManager() {
        DynamicDevice<? extends MSR> dynamicMsr;
        JposEntryRegistry deviceRegistry = JposServiceLoader.getManager().getEntryRegistry();

        if (applicationConfig.IsSimulationMode()) {
            dynamicMsr = new SimulatedDynamicDevice<>(simulatedMsr, new DevicePower(), new DeviceConnector<>(simulatedMsr, deviceRegistry));
        } else {
            MSR msr = new MSR();
            DeviceConnector<MSR> connector = new DeviceConnector<>(msr, deviceRegistry);
            dynamicMsr = new DynamicDevice<>(msr, new DevicePower(), connector);
        }

        MsrManager msrManager = new MsrManager(
                new MsrDevice(
                        dynamicMsr,
                        new MsrDeviceListener(new EventSynchronizer(new Phaser(1)))),
                new ReentrantLock());

        DeviceAvailabilitySingleton.getDeviceAvailabilitySingleton().setMsrManager(msrManager);
        return msrManager;
    }

    @Bean
    SimulatedJposMsr getSimulatedMsr() {
        return simulatedMsr;
    }
}
