package com.target.devicemanager.common;

import com.target.devicemanager.components.cashdrawer.CashDrawerManager;
import com.target.devicemanager.components.check.MicrManager;
import com.target.devicemanager.components.keyboard.KeyboardManager;
import com.target.devicemanager.components.keylock.KeylockManager;
import com.target.devicemanager.components.linedisplay.LineDisplayManager;
import com.target.devicemanager.components.msr.MsrManager;
import com.target.devicemanager.components.printer.PrinterManager;
import com.target.devicemanager.components.scale.ScaleManager;
import com.target.devicemanager.components.scanner.ScannerManager;
import com.target.devicemanager.components.tone.ToneManager;

public class DeviceAvailabilitySingleton {

    /**
     * This class holds the single objects created by the device config files to be used when the devices need to be reconnected.
     */
    private static final DeviceAvailabilitySingleton deviceAvailabilitySingleton = new DeviceAvailabilitySingleton();
    private CashDrawerManager cashDrawerManager = null;
    private KeyboardManager keyboardManager = null;
    private KeylockManager keylockManager = null;
    private MicrManager micrManager = null;
    private LineDisplayManager lineDisplayManager = null;
    private PrinterManager printerManager = null;
    private ScaleManager scaleManager = null;
    private ScannerManager scannerManager = null;
    private MsrManager msrManager = null;
    private ToneManager toneManager = null;

    private DeviceAvailabilitySingleton() {
        // do nothing at the moment
    }

    public static DeviceAvailabilitySingleton getDeviceAvailabilitySingleton() {
        return deviceAvailabilitySingleton;
    }

    public CashDrawerManager getCashDrawerManager() {
        return cashDrawerManager;
    }

    public void setCashDrawerManager(CashDrawerManager cashDrawerManager) { this.cashDrawerManager = cashDrawerManager; }

    public KeyboardManager getKeyboardManager() {
        return keyboardManager;
    }

    public void setKeyboardManager(KeyboardManager keyboardManager) { this.keyboardManager = keyboardManager; }

    public KeylockManager getKeylockManager() {
        return keylockManager;
    }

    public void setKeylockManager(KeylockManager keylockManager) { this.keylockManager = keylockManager; }


    public MicrManager getMicrManager() {
        return micrManager;
    }

    public void setMicrManager(MicrManager micrManager) {
        this.micrManager = micrManager;
    }

    public LineDisplayManager getLineDisplayManager() {
        return lineDisplayManager;
    }

    public void setLineDisplayManager(LineDisplayManager lineDisplayManager) { this.lineDisplayManager = lineDisplayManager; }

    public PrinterManager getPrinterManager() {
        return printerManager;
    }

    public void setPrinterManager(PrinterManager printerManager) {
        this.printerManager = printerManager;
    }

    public ScaleManager getScaleManager() {
        return scaleManager;
    }

    public void setScaleManager(ScaleManager scaleManager) {
        this.scaleManager = scaleManager;
    }

    public ScannerManager getScannerManager() {
        return scannerManager;
    }

    public void setScannerManager(ScannerManager scannerManager) {
        this.scannerManager = scannerManager;
    }

    public MsrManager getMsrManager() {
        return msrManager;
    }

    public void setMsrManager(MsrManager msrManager) {
        this.msrManager = msrManager;
    }

    public ToneManager getToneManager() {
        return toneManager;
    }

    public void setToneManager(ToneManager toneManager) {
        this.toneManager = toneManager;
    }
}