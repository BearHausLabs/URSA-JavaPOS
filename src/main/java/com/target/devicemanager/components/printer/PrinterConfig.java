package com.target.devicemanager.components.printer;

import com.target.devicemanager.common.*;
import com.target.devicemanager.components.printer.simulator.SimulatedJposPrinter;
import com.target.devicemanager.configuration.ApplicationConfig;
import jpos.POSPrinter;
import jpos.config.JposEntryRegistry;
import jpos.loader.JposServiceLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Phaser;
import java.util.concurrent.locks.ReentrantLock;

@Configuration
@ConditionalOnProperty(name = "possum.device.printer.enabled", havingValue = "true", matchIfMissing = true)
class PrinterConfig {
    private final SimulatedJposPrinter simulatedPrinter;
    private final ApplicationConfig applicationConfig;
    private final WorkstationConfig workstationConfig;

    @Autowired
    PrinterConfig(ApplicationConfig applicationConfig, WorkstationConfig workstationConfig) {

        this.simulatedPrinter = new SimulatedJposPrinter();
        this.applicationConfig = applicationConfig;
        this.workstationConfig = workstationConfig;
    }

    @Bean
    public PrinterManager getReceiptPrinterManager() {
        DynamicDevice<? extends POSPrinter> dynamicPrinter;
        JposEntryRegistry deviceRegistry = JposServiceLoader.getManager().getEntryRegistry();
        WorkstationConfig.DeviceConfig deviceConfig = workstationConfig.getDeviceConfig("printer");

        if (applicationConfig.IsSimulationMode()) {
            dynamicPrinter = new SimulatedDynamicDevice<>(simulatedPrinter, new DevicePower(), new DeviceConnector<>(simulatedPrinter, deviceRegistry));

        } else {
            POSPrinter posPrinter = new POSPrinter();
            DeviceConnector<POSPrinter> connector = new DeviceConnector<>(posPrinter, deviceRegistry);
            if (deviceConfig.hasLogicalName()) {
                connector.setPreferredLogicalName(deviceConfig.getLogicalName());
                connector.setSkipTestCycle(true);
            }
            dynamicPrinter = new DynamicDevice<>(posPrinter, new DevicePower(), connector);
        }

        PrinterManager printerManager = new PrinterManager(
                new PrinterDevice(dynamicPrinter, new PrinterDeviceListener(new EventSynchronizer(new Phaser(1)))),
                new ReentrantLock());

        DeviceAvailabilitySingleton.getDeviceAvailabilitySingleton().setPrinterManager(printerManager);
        if (workstationConfig.isManualLifecycle()) {
            printerManager.setManualMode(true);
        }
        return printerManager;
    }

    @Bean
    SimulatedJposPrinter getMyPrinter() {
        return simulatedPrinter;
    }
}
