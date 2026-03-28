package com.target.devicemanager.components.scanner;

import com.target.devicemanager.common.*;
import com.target.devicemanager.components.scanner.entities.ScannerType;
import com.target.devicemanager.components.scanner.simulator.SimulatedJposScanner;
import com.target.devicemanager.configuration.ApplicationConfig;
import jpos.Scanner;
import jpos.config.JposEntryRegistry;
import jpos.loader.JposServiceLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Phaser;
import java.util.concurrent.locks.ReentrantLock;

@Configuration
@ConditionalOnProperty(name = "possum.device.scanner.enabled", havingValue = "true", matchIfMissing = true)
class ScannerConfig {
    private final ApplicationConfig applicationConfig;
    private final WorkstationConfig workstationConfig;
    private final SimulatedJposScanner simulatedFlatbedScanner;
    private final SimulatedJposScanner simulatedHandheldScanner;

    @Autowired
    ScannerConfig(ApplicationConfig applicationConfig, WorkstationConfig workstationConfig) {
        this.applicationConfig = applicationConfig;
        this.workstationConfig = workstationConfig;
        this.simulatedFlatbedScanner = new SimulatedJposScanner(ScannerType.FLATBED);
        this.simulatedHandheldScanner = new SimulatedJposScanner(ScannerType.HANDHELD);
    }

    List<ScannerDevice> getScanners() {
        List<ScannerDevice> scanners = new ArrayList<>();
        JposEntryRegistry deviceRegistry = JposServiceLoader.getManager().getEntryRegistry();

        // Scanner has sub-devices (flatbed, handheld) so resolve from nested config
        WorkstationConfig.DeviceConfig scannerConfig = workstationConfig.getDeviceConfig("scanner");
        Map<String, WorkstationConfig.DeviceConfig> subDevices = scannerConfig.getSubDevices();

        String flatbedLogicalName = null;
        String handheldLogicalName = null;
        if (subDevices != null) {
            WorkstationConfig.DeviceConfig flatbedConfig = subDevices.get("flatbed");
            if (flatbedConfig != null && flatbedConfig.hasLogicalName()) {
                flatbedLogicalName = flatbedConfig.getLogicalName();
            }
            WorkstationConfig.DeviceConfig handheldConfig = subDevices.get("handheld");
            if (handheldConfig != null && handheldConfig.hasLogicalName()) {
                handheldLogicalName = handheldConfig.getLogicalName();
            }
        }

        if (applicationConfig.IsSimulationMode()) {
            scanners.add(new ScannerDevice(
                    new ScannerDeviceListener(new EventSynchronizer(new Phaser(1))),
                    new SimulatedDynamicDevice<>(
                            simulatedFlatbedScanner,
                            new DevicePower(),
                            new DeviceConnector<>(simulatedFlatbedScanner, deviceRegistry)
                    ),
                    ScannerType.FLATBED,
                    applicationConfig
            ));

            scanners.add(new ScannerDevice(
                    new ScannerDeviceListener(new EventSynchronizer(new Phaser(1))),
                    new SimulatedDynamicDevice<>(
                            simulatedHandheldScanner,
                            new DevicePower(),
                            new DeviceConnector<>(simulatedHandheldScanner, deviceRegistry)
                    ),
                    ScannerType.HANDHELD,
                    applicationConfig
            ));
        } else {
            Scanner flatbedScanner = new Scanner();
            DeviceConnector<Scanner> flatbedConnector = new DeviceConnector<>(flatbedScanner, deviceRegistry, new SimpleEntry<>("deviceType", "Flatbed"));
            if (flatbedLogicalName != null) {
                flatbedConnector.setPreferredLogicalName(flatbedLogicalName);
                flatbedConnector.setSkipTestCycle(true);
            }
            scanners.add(new ScannerDevice(
                    new ScannerDeviceListener(new EventSynchronizer(new Phaser(1))),
                    new DynamicDevice<>(flatbedScanner, new DevicePower(), flatbedConnector),
                    ScannerType.FLATBED, applicationConfig));

            Scanner handScanner = new Scanner();
            DeviceConnector<Scanner> handheldConnector = new DeviceConnector<>(handScanner, deviceRegistry, new SimpleEntry<>("deviceType", "HandScanner"));
            if (handheldLogicalName != null) {
                handheldConnector.setPreferredLogicalName(handheldLogicalName);
                handheldConnector.setSkipTestCycle(true);
            }
            scanners.add(new ScannerDevice(
                    new ScannerDeviceListener(new EventSynchronizer(new Phaser(1))),
                    new DynamicDevice<>(handScanner, new DevicePower(), handheldConnector),
                    ScannerType.HANDHELD, applicationConfig));
        }

        return scanners;
    }

    @Bean
    public ScannerManager getScannerManager() {
        ScannerManager scannerManager = new ScannerManager(getScanners(), new ReentrantLock());

        DeviceAvailabilitySingleton.getDeviceAvailabilitySingleton().setScannerManager(scannerManager);
        if (workstationConfig.isManualLifecycle()) {
            scannerManager.setManualMode(true);
        }
        return scannerManager;
    }

    @Bean(name = "simulatedFlatbedScanner")
    SimulatedJposScanner getSimulatedFlatbedScanner() {
        return simulatedFlatbedScanner;
    }

    @Bean(name = "simulatedHandheldScanner")
    SimulatedJposScanner getSimulatedHandheldScanner() {
        return simulatedHandheldScanner;
    }

}
