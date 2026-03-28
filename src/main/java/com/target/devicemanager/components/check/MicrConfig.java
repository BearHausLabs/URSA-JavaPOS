package com.target.devicemanager.components.check;

import com.target.devicemanager.common.*;
import com.target.devicemanager.components.check.simulator.SimulatedJposMicr;
import com.target.devicemanager.configuration.ApplicationConfig;
import jpos.MICR;
import jpos.config.JposEntryRegistry;
import jpos.loader.JposServiceLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.CopyOnWriteArrayList;

@Configuration
@ConditionalOnProperty(name = "possum.device.micr.enabled", havingValue = "true", matchIfMissing = true)
class MicrConfig {
    private final ApplicationConfig applicationConfig;
    private final WorkstationConfig workstationConfig;
    private final SimulatedJposMicr simulatedMicr;

    @Autowired
    MicrConfig(ApplicationConfig applicationConfig, WorkstationConfig workstationConfig) {
        this.applicationConfig = applicationConfig;
        this.workstationConfig = workstationConfig;
        this.simulatedMicr = new SimulatedJposMicr();
    }

    @Bean
    public MicrManager getMicrManager() {
        DynamicDevice<? extends MICR> dynamicMicr;
        JposEntryRegistry deviceRegistry = JposServiceLoader.getManager().getEntryRegistry();
        WorkstationConfig.DeviceConfig deviceConfig = workstationConfig.getDeviceConfig("micr");

        if (applicationConfig.IsSimulationMode()) {
            dynamicMicr = new DynamicDevice<>(simulatedMicr, new DevicePower(), new DeviceConnector<>(simulatedMicr, deviceRegistry));
        } else {
            MICR micr = new MICR();
            DeviceConnector<MICR> connector = new DeviceConnector<>(micr, deviceRegistry);
            if (deviceConfig.hasLogicalName()) {
                connector.setPreferredLogicalName(deviceConfig.getLogicalName());
                connector.setSkipTestCycle(true);
            }
            dynamicMicr = new DynamicDevice<>(micr, new DevicePower(), connector);
        }

        MicrManager micrManager = new MicrManager(
                new MicrDevice(dynamicMicr,new CopyOnWriteArrayList<>(),new CopyOnWriteArrayList<>()));

        DeviceAvailabilitySingleton.getDeviceAvailabilitySingleton().setMicrManager(micrManager);
        if (workstationConfig.isManualLifecycle()) {
            micrManager.setManualMode(true);
        }
        return micrManager;
    }

    @Bean
    SimulatedJposMicr getSimulatedMicr(){return simulatedMicr;}
}
