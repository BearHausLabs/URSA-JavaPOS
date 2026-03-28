package com.target.devicemanager.components.keyboard;

import com.target.devicemanager.common.*;
import com.target.devicemanager.components.keyboard.simulator.SimulatedJposKeyboard;
import com.target.devicemanager.configuration.ApplicationConfig;

import jpos.config.JposEntryRegistry;
import jpos.POSKeyboard;
import jpos.loader.JposServiceLoader;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Phaser;
import java.util.concurrent.locks.ReentrantLock;

@Configuration
@ConditionalOnProperty(name = "possum.device.keyboard.enabled", havingValue = "true", matchIfMissing = true)
class KeyboardConfig {
    private final SimulatedJposKeyboard simulatedJposKeyboard;
    private final ApplicationConfig applicationConfig;
    private final WorkstationConfig workstationConfig;

    @Autowired
    KeyboardConfig(ApplicationConfig applicationConfig, WorkstationConfig workstationConfig) {
        this.applicationConfig = applicationConfig;
        this.workstationConfig = workstationConfig;
        this.simulatedJposKeyboard = new SimulatedJposKeyboard();
    }

    @Bean
    public KeyboardManager getKeyboardManager() {
        DynamicDevice<? extends POSKeyboard> dynamicKeyboard;
        JposEntryRegistry deviceRegistry = JposServiceLoader.getManager().getEntryRegistry();
        WorkstationConfig.DeviceConfig deviceConfig = workstationConfig.getDeviceConfig("keyboard");

        if (applicationConfig.IsSimulationMode()) {
            dynamicKeyboard = new SimulatedDynamicDevice<>(simulatedJposKeyboard, new DevicePower(), new DeviceConnector<>(simulatedJposKeyboard, deviceRegistry));
        } else {
            POSKeyboard keyboard = new POSKeyboard();
            DeviceConnector<POSKeyboard> connector = new DeviceConnector<>(keyboard, deviceRegistry);
            if (deviceConfig.hasLogicalName()) {
                connector.setPreferredLogicalName(deviceConfig.getLogicalName());
            }
            // POS keyboards don't like enable/disable during discovery
            connector.setSkipTestCycle(true);
            dynamicKeyboard = new DynamicDevice<>(keyboard, new DevicePower(), connector);
        }

        KeyboardManager keyboardManager = new KeyboardManager(
                new KeyboardDevice(
                        dynamicKeyboard,
                        new KeyboardListener(new EventSynchronizer(new Phaser(1)))),
                new ReentrantLock());

        DeviceAvailabilitySingleton.getDeviceAvailabilitySingleton().setKeyboardManager(keyboardManager);
        if (workstationConfig.isManualLifecycle()) {
            keyboardManager.setManualMode(true);
        }
        return keyboardManager;
    }

    @Bean
    SimulatedJposKeyboard getSimulatedJposKeyboard() {
        return simulatedJposKeyboard;
    }
}
