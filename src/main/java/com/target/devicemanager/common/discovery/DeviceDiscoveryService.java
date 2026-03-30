package com.target.devicemanager.common.discovery;

import com.target.devicemanager.common.StructuredEventLogger;
import jpos.BaseJposControl;
import jpos.JposException;
import jpos.config.JposEntryRegistry;
import jpos.config.simple.SimpleEntry;
import jpos.loader.JposServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service that performs JPOS device discovery by reading devcon.xml/jpos.xml entries
 * and optionally testing them via the open/claim/enable lifecycle.
 *
 * Used by URSA admin UI for first-run hardware configuration.
 */
@Service
public class DeviceDiscoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceDiscoveryService.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getCommonServiceName(), "DeviceDiscoveryService", LOGGER);

    private static final int CLAIM_TIMEOUT_MS = 10000;
    private static final int REGISTRY_LOAD_RETRIES = 5;

    public DeviceDiscoveryService() {
    }

    /**
     * Read devcon.xml/jpos.xml and return all available JPOS entries grouped by device category.
     *
     * @return Map of category name -> list of logical names
     */
    public Map<String, List<String>> getAvailableDevices() {
        JposEntryRegistry registry = getRegistry();
        if (registry == null || registry.getSize() == 0) {
            log.failure("JPOS registry is empty or unavailable", 13, null);
            return Collections.emptyMap();
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        ArrayList<SimpleEntry> entries = Collections.list((Enumeration<SimpleEntry>) registry.getEntries());

        for (SimpleEntry entry : entries) {
            try {
                String category = entry.getPropertyValue("deviceCategory").toString();
                String logicalName = entry.getPropertyValue("logicalName").toString();
                result.computeIfAbsent(category, k -> new ArrayList<>()).add(logicalName);
            } catch (Exception e) {
                log.failure("Failed to read JPOS entry properties", 13, e);
            }
        }

        log.success("Discovery found " + entries.size() + " entries across " + result.size() + " categories", 9);
        return result;
    }

    /**
     * Test a specific device by logical name: open -> claim -> enable -> disable -> release -> close.
     *
     * @param logicalName The JPOS logical name to test
     * @return Test result with status OK or FAILED
     */
    public DeviceTestResult testDevice(String logicalName) {
        JposEntryRegistry registry = getRegistry();
        if (registry == null) {
            return DeviceTestResult.failed(logicalName, "unknown", "JPOS registry unavailable");
        }

        // Find the category for this logical name
        String category = findCategory(registry, logicalName);
        if (category == null) {
            return DeviceTestResult.failed(logicalName, "unknown", "Logical name not found in JPOS registry");
        }

        // Create a generic device control for testing
        BaseJposControl testControl = createControlForCategory(category);
        if (testControl == null) {
            return DeviceTestResult.failed(logicalName, category, "No test control available for category: " + category);
        }

        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("category", category);

        try {
            testControl.open(logicalName);
            properties.put("open", "OK");

            try {
                testControl.claim(CLAIM_TIMEOUT_MS);
                properties.put("claim", "OK");

                try {
                    testControl.setDeviceEnabled(true);
                    properties.put("enable", "OK");

                    try {
                        testControl.setDeviceEnabled(false);
                        properties.put("disable", "OK");
                    } catch (JposException e) {
                        properties.put("disable", "FAILED: " + e.getMessage());
                    }
                } catch (JposException e) {
                    properties.put("enable", "FAILED: " + e.getMessage());
                }

                try {
                    testControl.release();
                    properties.put("release", "OK");
                } catch (JposException e) {
                    properties.put("release", "FAILED: " + e.getMessage());
                }
            } catch (JposException e) {
                properties.put("claim", "FAILED: " + e.getMessage());
            }

            try {
                testControl.close();
                properties.put("close", "OK");
            } catch (JposException e) {
                properties.put("close", "FAILED: " + e.getMessage());
            }

            log.success("Device test OK: " + logicalName, 9);
            return DeviceTestResult.ok(logicalName, category, properties);

        } catch (JposException e) {
            properties.put("open", "FAILED: " + e.getMessage());
            log.failure("Device test FAILED: " + logicalName + " - " + e.getMessage(), 13, e);
            return DeviceTestResult.failed(logicalName, category, "Open failed: " + e.getMessage());
        }
    }

    /**
     * Full discovery scan: for each category, try all logical names and return results.
     *
     * @return Map of category -> list of test results
     */
    public Map<String, List<DeviceTestResult>> scanAll() {
        Map<String, List<String>> available = getAvailableDevices();
        Map<String, List<DeviceTestResult>> results = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> entry : available.entrySet()) {
            String category = entry.getKey();
            List<DeviceTestResult> categoryResults = new ArrayList<>();

            for (String logicalName : entry.getValue()) {
                log.success("Scanning " + category + "/" + logicalName + "...", 5);
                categoryResults.add(testDevice(logicalName));
            }

            results.put(category, categoryResults);
        }

        return results;
    }

    // --- Private helpers ---

    private JposEntryRegistry getRegistry() {
        try {
            JposEntryRegistry registry = JposServiceLoader.getManager().getEntryRegistry();
            for (int i = 0; i < REGISTRY_LOAD_RETRIES && (registry == null || registry.getSize() == 0); i++) {
                if (registry != null) {
                    registry.load();
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
            }
            return registry;
        } catch (Exception e) {
            log.failure("Failed to get JPOS entry registry", 17, e);
            return null;
        }
    }

    private String findCategory(JposEntryRegistry registry, String logicalName) {
        ArrayList<SimpleEntry> entries = Collections.list((Enumeration<SimpleEntry>) registry.getEntries());
        for (SimpleEntry entry : entries) {
            try {
                String name = entry.getPropertyValue("logicalName").toString();
                if (logicalName.equals(name)) {
                    return entry.getPropertyValue("deviceCategory").toString();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Create a JPOS control object for the given category.
     * We use specific control types so the JPOS service loader can find the right service.
     */
    private BaseJposControl createControlForCategory(String category) {
        try {
            return switch (category) {
                case "CashDrawer" -> new jpos.CashDrawer();
                case "POSPrinter" -> new jpos.POSPrinter();
                case "Scanner" -> new jpos.Scanner();
                case "MSR" -> new jpos.MSR();
                case "LineDisplay" -> new jpos.LineDisplay();
                case "Scale" -> new jpos.Scale();
                case "Keylock" -> new jpos.Keylock();
                case "POSKeyboard" -> new jpos.POSKeyboard();
                case "ToneIndicator" -> new jpos.ToneIndicator();
                case "MICR" -> new jpos.MICR();
                default -> {
                    log.failure("Unknown JPOS category for test control: " + category, 13, null);
                    yield null;
                }
            };
        } catch (Exception e) {
            log.failure("Failed to create control for category: " + category, 13, e);
            return null;
        }
    }
}
