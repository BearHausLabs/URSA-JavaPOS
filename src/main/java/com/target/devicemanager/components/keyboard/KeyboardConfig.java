package com.target.devicemanager.components.keyboard;

import com.target.devicemanager.common.*;
import com.target.devicemanager.components.keyboard.simulator.SimulatedJposKeyboard;
import com.target.devicemanager.configuration.ApplicationConfig;

import jpos.config.JposEntryRegistry;
import jpos.POSKeyboard;
import jpos.loader.JposServiceLoader;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Phaser;
import java.util.concurrent.locks.ReentrantLock;

@Configuration
class KeyboardConfig {
    private final SimulatedJposKeyboard simulatedJposKeyboard;
    private final ApplicationConfig applicationConfig;

    @Autowired
    KeyboardConfig(ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
        this.simulatedJposKeyboard = new SimulatedJposKeyboard();
    }

    @Bean
    public KeyboardManager getKeyboardManager() {
        DynamicDevice<? extends POSKeyboard> dynamicKeyboard;
        JposEntryRegistry deviceRegistry = JposServiceLoader.getManager().getEntryRegistry();

        if (applicationConfig.IsSimulationMode()) {
            dynamicKeyboard = new SimulatedDynamicDevice<>(simulatedJposKeyboard, new DevicePower(), new DeviceConnector<>(simulatedJposKeyboard, deviceRegistry));
        } else {
            POSKeyboard keyboard = new POSKeyboard();
            DeviceConnector<POSKeyboard> connector = new DeviceConnector<>(keyboard, deviceRegistry);
            dynamicKeyboard = new DynamicDevice<>(keyboard, new DevicePower(), connector);
        }

        KeyboardManager keyboardManager = new KeyboardManager(
                new KeyboardDevice(
                        dynamicKeyboard,
                        new KeyboardListener(new EventSynchronizer(new Phaser(1)))),
                new ReentrantLock());

        DeviceAvailabilitySingleton.getDeviceAvailabilitySingleton().setKeyboardManager(keyboardManager);
        return keyboardManager;
    }

    @Bean
    SimulatedJposKeyboard getSimulatedJposKeyboard() {
        return simulatedJposKeyboard;
    }
}
