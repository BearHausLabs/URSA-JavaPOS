package com.target.devicemanager.components.printer;

import com.target.devicemanager.common.DeviceLifecycleResponse;
import com.target.devicemanager.common.StructuredEventLogger;
import com.target.devicemanager.common.entities.DeviceError;
import com.target.devicemanager.common.entities.DeviceException;
import com.target.devicemanager.common.entities.DeviceHealthResponse;
import com.target.devicemanager.components.printer.entities.PrinterContent;
import com.target.devicemanager.components.printer.entities.PrinterError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jpos.JposException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/v1")
@Tag(name = "Printer")
@ConditionalOnProperty(name = "possum.device.printer.enabled", havingValue = "true", matchIfMissing = true)
public class PrinterController {

    private final PrinterManager printerManager;
    private final int PRINT_CONTENT_SIZE = 64; // Assumes maximum allowed array size for the printer is 64
    private static final Logger LOGGER = LoggerFactory.getLogger(PrinterController.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getPrinterServiceName(), "PrinterController", LOGGER);

    @Autowired
    public PrinterController(PrinterManager printerManager) {
        if (printerManager == null) {
            throw new IllegalArgumentException("printerManager cannot be null");
        }
        this.printerManager = printerManager;
    }

    @Operation(description = "Sends entities to attached printer")
    @PostMapping(value = "/print")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COVER_OPEN, OUT_OF_PAPER, INVALID_FORMAT",
                    content = @Content(schema = @Schema(implementation = PrinterError.class))),
            @ApiResponse(responseCode = "408", description = "PRINTER_TIME_OUT",
                    content = @Content(schema = @Schema(implementation = PrinterError.class))),
            @ApiResponse(responseCode = "500", description = "UNEXPECTED_ERROR",
                    content = @Content(schema = @Schema(implementation = DeviceError.class))),
            @ApiResponse(responseCode = "404", description = "DEVICE_OFFLINE",
                    content = @Content(schema = @Schema(implementation = DeviceError.class))),
            @ApiResponse(responseCode = "409", description = "DEVICE_BUSY",
                    content = @Content(schema = @Schema(implementation = DeviceError.class)))
    })
    public void print(@Parameter(description = "Receipt entities")
                      @Valid @RequestBody List<PrinterContent> contents) throws DeviceException {
        String url = "/v1/print";
        log.successAPI("API Request Received", 1, url, null, 0);
        try {
            if (contents.size() < PRINT_CONTENT_SIZE) {
                printerManager.printReceipt(contents);
                log.successAPI("API Request Completed Successfully", 1, url, "OK", 200);
            } else {
                log.failure("Printer print content more than expected limit", 13, null);
                throw new DeviceException(PrinterError.INVALID_FORMAT);
            }
        } catch (DeviceException deviceException) {
            log.failureAPI("API Request Failed with DeviceException", 13, url, deviceException.getDeviceError().toString(), deviceException.getDeviceError().getStatusCode().value(), null);
            throw deviceException;
        }
    }

    @Operation(description = "Reports printer health")
    @GetMapping(path = "/printer/health")
    public DeviceHealthResponse getHealth() {
        String url = "/v1/printer/health";
        log.successAPI("API Request Received", 1,url, null, 0);
        DeviceHealthResponse response = printerManager.getHealth();
        log.successAPI("API Request Completed Successfully", 1, url, response.toString(), 200);
        return response;
    }

    @Operation(description = "Reports printer status")
    @GetMapping(path = "/printer/healthstatus")
    public DeviceHealthResponse getStatus() {
        String url = "/v1/printer/healthstatus";
        log.successAPI("API Request Received", 1, url, null, 0);
        DeviceHealthResponse response = printerManager.getStatus();
        log.successAPI("API Request Completed Successfully", 1, url, response.toString(), 200);
        return response;
    }

    @ExceptionHandler(value = HttpMessageNotReadableException.class)
    public ResponseEntity<DeviceError> handleInvalidFormat(HttpMessageNotReadableException originalException) {
        DeviceException printerException = new DeviceException(PrinterError.INVALID_FORMAT);
        return new ResponseEntity<>(printerException.getDeviceError(),
                printerException.getDeviceError().getStatusCode());
    }

    // --- Step 5e: Lifecycle endpoints ---

    @Operation(description = "JPOS open — establish connection to printer")
    @PostMapping("/printer/lifecycle/open")
    public DeviceLifecycleResponse lifecycleOpen(@RequestParam String logicalName) throws JposException {
        String url = "/v1/printer/lifecycle/open";
        log.successAPI("request", 1, url, logicalName, 0);
        printerManager.openDevice(logicalName);
        return printerManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS claim — exclusive access to printer")
    @PostMapping("/printer/lifecycle/claim")
    public DeviceLifecycleResponse lifecycleClaim(@RequestParam(defaultValue = "30000") int timeout) throws JposException {
        String url = "/v1/printer/lifecycle/claim";
        log.successAPI("request", 1, url, null, 0);
        printerManager.claimDevice(timeout);
        return printerManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS enable — enable printer for operations")
    @PostMapping("/printer/lifecycle/enable")
    public DeviceLifecycleResponse lifecycleEnable() throws JposException {
        String url = "/v1/printer/lifecycle/enable";
        log.successAPI("request", 1, url, null, 0);
        printerManager.enableDevice();
        return printerManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS disable — disable printer")
    @PostMapping("/printer/lifecycle/disable")
    public DeviceLifecycleResponse lifecycleDisable() throws JposException {
        String url = "/v1/printer/lifecycle/disable";
        log.successAPI("request", 1, url, null, 0);
        printerManager.disableDevice();
        return printerManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS release — release exclusive access")
    @PostMapping("/printer/lifecycle/release")
    public DeviceLifecycleResponse lifecycleRelease() throws JposException {
        String url = "/v1/printer/lifecycle/release";
        log.successAPI("request", 1, url, null, 0);
        printerManager.releaseDevice();
        return printerManager.getLifecycleStatus();
    }

    @Operation(description = "JPOS close — close connection to printer")
    @PostMapping("/printer/lifecycle/close")
    public DeviceLifecycleResponse lifecycleClose() throws JposException {
        String url = "/v1/printer/lifecycle/close";
        log.successAPI("request", 1, url, null, 0);
        printerManager.closeDevice();
        return printerManager.getLifecycleStatus();
    }

    @Operation(description = "Get current JPOS lifecycle state")
    @GetMapping("/printer/lifecycle")
    public DeviceLifecycleResponse getLifecycleStatus() {
        String url = "/v1/printer/lifecycle";
        log.successAPI("request", 1, url, null, 0);
        return printerManager.getLifecycleStatus();
    }

}
