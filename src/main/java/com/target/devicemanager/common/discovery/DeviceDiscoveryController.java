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
 * URSA controls device lifecycle; these endpoints help identify what hardware
 * is available in the JPOS registry.
 *
 * GET  /v1/discovery          -- list all JPOS entries grouped by category
 * GET  /v1/discovery/test/{logicalName} -- test a specific device
 * POST /v1/discovery/scan     -- full discovery: test all entries
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
}
