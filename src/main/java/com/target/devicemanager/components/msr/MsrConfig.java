package com.target.devicemanager.components.msr;

import com.target.devicemanager.common.*;
import com.target.devicemanager.components.msr.simulator.SimulatedJposMsr;
import com.target.devicemanager.configuration.ApplicationConfig;
import jpos.MSR;
import jpos.config.JposEntryRegistry;
import jpos.loader.JposServiceLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.Phaser;
import java.util.concurrent.locks.ReentrantLock;

@Configuration
@Profile({"local", "dev", "prod"})
@ConditionalOnProperty(name = "possum.device.msr.enabled", havingValue = "true", matchIfMissing = true)
class MsrConfig {
    private final SimulatedJposMsr simulatedMsr;
    private final ApplicationConfig applicationConfig;
    private final WorkstationConfig workstationConfig;

    @Autowired
    MsrConfig(ApplicationConfig applicationConfig, WorkstationConfig workstationConfig) {
        this.applicationConfig = applicationConfig;
        this.workstationConfig = workstationConfig;
        this.simulatedMsr = new SimulatedJposMsr();
    }

    @Bean
    public MsrManager getMsrManager() {
        DynamicDevice<? extends MSR> dynamicMsr;
        JposEntryRegistry deviceRegistry = JposServiceLoader.getManager().getEntryRegistry();
        WorkstationConfig.DeviceConfig deviceConfig = workstationConfig.getDeviceConfig("msr");

        if (applicationConfig.IsSimulationMode()) {
            dynamicMsr = new SimulatedDynamicDevice<>(simulatedMsr, new DevicePower(), new DeviceConnector<>(simulatedMsr, deviceRegistry));
        } else {
            MSR msr = new MSR();
            DeviceConnector<MSR> connector = new DeviceConnector<>(msr, deviceRegistry);
            if (deviceConfig.hasLogicalName()) {
                connector.setPreferredLogicalName(deviceConfig.getLogicalName());
                connector.setSkipTestCycle(true);
            }
            dynamicMsr = new DynamicDevice<>(msr, new DevicePower(), connector);
        }

        MsrManager msrManager = new MsrManager(
                new MsrDevice(
                        dynamicMsr,
                        new MsrDeviceListener(new EventSynchronizer(new Phaser(1)))),
                new ReentrantLock());

        DeviceAvailabilitySingleton.getDeviceAvailabilitySingleton().setMsrManager(msrManager);
        if (workstationConfig.isManualLifecycle()) {
            msrManager.setManualMode(true);
        }
        return msrManager;
    }

    @Bean
    SimulatedJposMsr getSimulatedMsr() {
        return simulatedMsr;
    }
}
