package com.target.devicemanager.components.scanner;

import com.target.devicemanager.common.*;
import com.target.devicemanager.components.scanner.simulator.SimulatedJposScanner;
import com.target.devicemanager.configuration.ApplicationConfig;
import jpos.Scanner;
import jpos.config.JposEntryRegistry;
import jpos.loader.JposServiceLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Phaser;
import java.util.concurrent.locks.ReentrantLock;

@Configuration
class ScannerConfig {
    private final ApplicationConfig applicationConfig;
    private final SimulatedJposScanner simulatedScanner;

    @Autowired
    ScannerConfig(ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
        this.simulatedScanner = new SimulatedJposScanner();
    }

    @Bean
    public ScannerManager getScannerManager() {
        DynamicDevice<? extends Scanner> dynamicScanner;
        JposEntryRegistry deviceRegistry = JposServiceLoader.getManager().getEntryRegistry();

        if (applicationConfig.IsSimulationMode()) {
            dynamicScanner = new SimulatedDynamicDevice<>(
                    simulatedScanner,
                    new DevicePower(),
                    new DeviceConnector<>(simulatedScanner, deviceRegistry)
            );
        } else {
            Scanner scanner = new Scanner();
            DeviceConnector<Scanner> connector = new DeviceConnector<>(scanner, deviceRegistry);
            dynamicScanner = new DynamicDevice<>(scanner, new DevicePower(), connector);
        }

        ScannerManager scannerManager = new ScannerManager(
                new ScannerDevice(
                        new ScannerDeviceListener(new EventSynchronizer(new Phaser(1))),
                        dynamicScanner,
                        applicationConfig
                ),
                new ReentrantLock()
        );

        DeviceAvailabilitySingleton.getDeviceAvailabilitySingleton().setScannerManager(scannerManager);
        return scannerManager;
    }

    @Bean(name = "simulatedScanner")
    SimulatedJposScanner getSimulatedScanner() {
        return simulatedScanner;
    }
}
