package com.target.devicemanager.components.keylock;

import com.target.devicemanager.common.*;
import com.target.devicemanager.components.keylock.simulator.SimulatedJposKeylock;
import com.target.devicemanager.configuration.ApplicationConfig;

import jpos.config.JposEntryRegistry;
import jpos.Keylock;
import jpos.loader.JposServiceLoader;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Phaser;
import java.util.concurrent.locks.ReentrantLock;

@Configuration
@ConditionalOnProperty(name = "possum.device.keylock.enabled", havingValue = "true", matchIfMissing = true)
class KeylockConfig {
    private final SimulatedJposKeylock simulatedJposKeylock;
    private final ApplicationConfig applicationConfig;
    private final WorkstationConfig workstationConfig;

    @Autowired
    KeylockConfig(ApplicationConfig applicationConfig, WorkstationConfig workstationConfig) {
        this.applicationConfig = applicationConfig;
        this.workstationConfig = workstationConfig;
        this.simulatedJposKeylock = new SimulatedJposKeylock();
    }

    @Bean
    public KeylockManager getKeylockManager() {
        DynamicDevice<? extends Keylock> dynamicKeylock;
        JposEntryRegistry deviceRegistry = JposServiceLoader.getManager().getEntryRegistry();
        WorkstationConfig.DeviceConfig deviceConfig = workstationConfig.getDeviceConfig("keylock");

        if (applicationConfig.IsSimulationMode()) {
            dynamicKeylock = new SimulatedDynamicDevice<>(simulatedJposKeylock, new DevicePower(), new DeviceConnector<>(simulatedJposKeylock, deviceRegistry));
        } else {
            Keylock keylock = new Keylock();
            DeviceConnector<Keylock> connector = new DeviceConnector<>(keylock, deviceRegistry);
            if (deviceConfig.hasLogicalName()) {
                connector.setPreferredLogicalName(deviceConfig.getLogicalName());
                connector.setSkipTestCycle(true);
            }
            // Keylocks are shared devices -- skip exclusive claim
            connector.setSkipClaim(true);
            dynamicKeylock = new DynamicDevice<>(keylock, new DevicePower(), connector);
        }

        KeylockManager keylockManager = new KeylockManager(
                new KeylockDevice(
                        dynamicKeylock,
                        new KeylockListener(new EventSynchronizer(new Phaser(1)))),
                new ReentrantLock());

        DeviceAvailabilitySingleton.getDeviceAvailabilitySingleton().setKeylockManager(keylockManager);
        if (workstationConfig.isManualLifecycle()) {
            keylockManager.setManualMode(true);
        }
        return keylockManager;
    }

    @Bean
    SimulatedJposKeylock getSimulatedJposKeylock() {
        return simulatedJposKeylock;
    }
}
