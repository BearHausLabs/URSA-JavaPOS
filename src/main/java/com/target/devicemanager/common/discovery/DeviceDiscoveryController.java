package com.target.devicemanager.common.discovery;

import com.target.devicemanager.common.StructuredEventLogger;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Discovery endpoints for first-run hardware configuration.
 *
 * These endpoints expose auto-discovery as a UTILITY for the URSA admin UI.
 * Normal device operation uses config-driven paths; these are for initial setup.
 *
 * GET  /v1/discovery          -- list all JPOS entries grouped by category
 * GET  /v1/discovery/test/{logicalName} -- test a specific device
 * POST /v1/discovery/scan     -- full discovery: test all entries
 * GET  /v1/discovery/config   -- get current device configuration
 * POST /v1/discovery/config   -- save new device configuration
 */
@RestController
@RequestMapping("/v1/discovery")
@Tag(name = "Device Discovery")
public class DeviceDiscoveryController {

    private final DeviceDiscoveryService discoveryService;
    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceDiscoveryController.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getCommonServiceName(), "DeviceDiscoveryController", LOGGER);

    @Autowired
    public DeviceDiscoveryController(DeviceDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    /**
     * Scans devcon.xml/jpos.xml and returns all available JPOS entries grouped by device category.
     *
     * Response example:
     * {
     *   "devices": {
     *     "CashDrawer": ["CashDrawer_NCR", "CashDrawer_ELO"],
     *     "POSPrinter": ["NCR_7199"],
     *     "Scanner": ["Datalogic_8200", "Zebra_DS9308"]
     *   }
     * }
     */
    @Operation(description = "List all available JPOS device entries grouped by category")
    @GetMapping
    public ResponseEntity<DiscoveryResponse> getAvailableDevices() {
        String url = "/v1/discovery";
        log.successAPI("request", 1, url, null, 0);

        Map<String, List<String>> devices = discoveryService.getAvailableDevices();
        DiscoveryResponse response = new DiscoveryResponse(devices);

        log.successAPI("response", 1, url, devices.size() + " categories found", 200);
        return ResponseEntity.ok(response);
    }

    /**
     * Test a specific device: open -> claim -> enable -> disable -> release -> close.
     * Returns detailed results of each lifecycle step.
     */
    @Operation(description = "Test a specific JPOS device by logical name")
    @GetMapping("/test/{logicalName}")
    public ResponseEntity<DeviceTestResult> testDevice(@PathVariable String logicalName) {
        String url = "/v1/discovery/test/" + logicalName;
        log.successAPI("request", 1, url, logicalName, 0);

        DeviceTestResult result = discoveryService.testDevice(logicalName);

        if (result.getStatus() == DeviceTestResult.Status.OK) {
            log.successAPI("response", 1, url, "OK", 200);
        } else {
            log.failureAPI("response", 13, url, result.getError(), 200, null);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Full discovery scan: for each category, test all logical names.
     * This is the "first run" endpoint -- URSA calls this, shows results in admin UI.
     */
    @Operation(description = "Scan and test all available JPOS devices")
    @PostMapping("/scan")
    public ResponseEntity<Map<String, List<DeviceTestResult>>> scanAll() {
        String url = "/v1/discovery/scan";
        log.successAPI("request", 1, url, null, 0);

        Map<String, List<DeviceTestResult>> results = discoveryService.scanAll();

        int totalDevices = results.values().stream().mapToInt(List::size).sum();
        long okCount = results.values().stream()
                .flatMap(List::stream)
                .filter(r -> r.getStatus() == DeviceTestResult.Status.OK)
                .count();

        log.successAPI("response", 1, url, okCount + "/" + totalDevices + " devices responded", 200);
        return ResponseEntity.ok(results);
    }

    /**
     * Returns current device configuration (what's in possum-config.yml / WorkstationConfig).
     */
    @Operation(description = "Get current POSSUM device configuration")
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        String url = "/v1/discovery/config";
        log.successAPI("request", 1, url, null, 0);

        Map<String, Object> config = discoveryService.getCurrentConfig();

        log.successAPI("response", 1, url, null, 200);
        return ResponseEntity.ok(config);
    }

    /**
     * Accept a new device configuration.
     * This is how URSA admin UI pushes config after the admin picks devices.
     *
     * NOTE: In the current implementation, this validates and logs the request.
     * Actual persistence to possum-config.yml requires file I/O which will be
     * implemented when the config file writer is ready.
     */
    @Operation(description = "Save new POSSUM device configuration")
    @PostMapping("/config")
    public ResponseEntity<Map<String, String>> saveConfig(@RequestBody DeviceConfigRequest configRequest) {
        String url = "/v1/discovery/config";
        log.successAPI("request", 1, url, null, 0);

        log.success("Received device config update request (persistence not yet implemented)", 9);

        log.failureAPI("response", 13, url, "Config persistence not yet implemented", 501, null);
        return ResponseEntity.status(501).body(Map.of(
                "error", "Not Implemented",
                "message", "Config persistence to possum-config.yml is not yet implemented. Use manual file editing for now."
        ));
    }
}
