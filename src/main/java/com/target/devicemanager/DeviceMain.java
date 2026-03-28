package com.target.devicemanager;

import com.target.devicemanager.common.StructuredEventLogger;
import jpos.config.JposEntryRegistry;
import jpos.config.simple.SimpleEntry;
import jpos.loader.JposServiceLoader;
import jpos.util.JposPropertiesConst;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SpringBootApplication
@EnableScheduling
public class DeviceMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceMain.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getDeviceManagerServiceName(), "DeviceMain", LOGGER);

    public static void main(String[] args) {
        // JCL configuration: let deploy-time jpos/res/jpos.properties control
        // the ServiceManager and RegPopulator classes. This allows vendor-specific
        // configs (e.g. Toshiba FixedManager + SDIPopulator) to work natively.
        //
        // Priority for populator file:
        //   1. POSSUM_DEVCON_PATH env var (explicit override)
        //   2. jpos.properties populator.file.0 / populatorFile (deploy-time config)
        //   3. Fallback: config/devcon.xml relative to working directory
        //
        // The fallback is critical: if jpos/res/jpos.properties defines multi-populator
        // classes (e.g. Toshiba) but omits populator.file.0, SimpleXmlRegPopulator
        // would have no file to load, resulting in an empty registry and no devices.
        String devconPath = System.getenv("POSSUM_DEVCON_PATH");
        if (devconPath != null) {
            LOGGER.info("JCL populator file set from POSSUM_DEVCON_PATH: {}", devconPath);
            System.setProperty(JposPropertiesConst.JPOS_POPULATOR_FILE_PROP_NAME, devconPath);
        } else {
            // Set fallback populator file so SimpleXmlRegPopulator always has a file.
            // If jpos.properties defines populator.file.0, that takes precedence for
            // populator class 0. This fallback covers the single-populator case and
            // any multi-populator config that forgot to specify a file path.
            File fallbackDevcon = new File("config/devcon.xml");
            if (fallbackDevcon.exists()) {
                String fallbackPath = fallbackDevcon.getAbsolutePath().replace('\\', '/');
                LOGGER.info("JCL populator file fallback: {}", fallbackPath);
                System.setProperty(JposPropertiesConst.JPOS_POPULATOR_FILE_PROP_NAME, fallbackPath);
            } else {
                LOGGER.warn("No POSSUM_DEVCON_PATH env var and no config/devcon.xml found. " +
                        "JCL will rely on jpos/res/jpos.properties for populator file paths.");
            }
        }
        System.setProperty("jpos.util.tracing.TurnOnAllNamedTracers", "OFF");
        ConfigurableApplicationContext dmcontext = SpringApplication.run(DeviceMain.class,args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void crashCountInStartup() {
        // Log JCL configuration state for diagnostics
        logJclDiagnostics();

        Boolean isSimulationMode = Boolean.parseBoolean(System.getProperty("useSimulators"));
        if(!isSimulationMode) {
            try {
                String logPath = System.getenv("POSSUM_LOG_PATH");
                if (logPath == null) {
                    log.success("Setting default log path for POSSUM.", 5);
                    logPath = "/var/log/target/possum";
                }

                //get the latest dump file also log the crash count since rebuild
                File latestLogFile = getLatestLogFile(logPath + "/CrashLog");

                if (latestLogFile != null) {
                    String coreDump = getCoreDumpInfo(latestLogFile);
                    if (coreDump != null && coreDump.length() > 0 ) {
                        log.success(coreDump, 9);
                    }
                }

                // count crash file since reboot
                File crashCount = new File(logPath + "/crashCount.log");
                if (crashCount.exists() && crashCount.isFile()) {
                    try {
                        String count = (new BufferedReader(new FileReader(crashCount))).readLine();
                        if (!count.equals("0")) {
                            log.success("Current POSSUM start count after reboot is: " + count, 9);
                        }
                    } catch (IOException ioException) {
                        log.failure("Error reading crash count", 17, ioException);
                    }
                }
            } catch (Exception exception) {
                log.failure("Error getting crash log file path", 17, exception);
            }
        }
    }

    /**
     * Log JCL (JavaPOS Configuration Loader) state at startup for diagnostics.
     * Reports the ServiceManager class, populator file, and number of registry entries.
     * This makes it easy to diagnose "no devices found" issues from logs alone.
     */
    private void logJclDiagnostics() {
        try {
            String svcMgr = System.getProperty("jpos.loader.serviceManagerClass", "(from jpos.properties)");
            String popFile = System.getProperty(JposPropertiesConst.JPOS_POPULATOR_FILE_PROP_NAME, "(not set via system property)");
            log.success("JCL ServiceManager: " + svcMgr, 5);
            log.success("JCL populatorFile system property: " + popFile, 5);

            JposEntryRegistry registry = JposServiceLoader.getManager().getEntryRegistry();
            if (registry != null) {
                int size = registry.getSize();
                log.success("JCL registry loaded: " + size + " entries", 9);
                if (size == 0) {
                    log.failure("JCL registry is EMPTY. Devices will not be discovered. " +
                            "Check that jpos/res/jpos.properties has a valid populator file path " +
                            "or that config/devcon.xml exists.", 17, null);
                } else {
                    // Log first few entries for verification
                    java.util.ArrayList<SimpleEntry> entries = java.util.Collections.list(
                            (java.util.Enumeration<SimpleEntry>) registry.getEntries());
                    int logCount = Math.min(entries.size(), 5);
                    for (int i = 0; i < logCount; i++) {
                        SimpleEntry e = entries.get(i);
                        try {
                            String name = e.getPropertyValue("logicalName").toString();
                            String cat = e.getPropertyValue("deviceCategory").toString();
                            log.success("  JCL entry: " + name + " (" + cat + ")", 5);
                        } catch (Exception ignored) {
                        }
                    }
                    if (entries.size() > 5) {
                        log.success("  ... and " + (entries.size() - 5) + " more entries", 5);
                    }
                }
            } else {
                log.failure("JCL registry is NULL. JposServiceLoader.getManager() returned null registry.", 17, null);
            }
        } catch (Exception e) {
            log.failure("Failed to log JCL diagnostics: " + e.getMessage(), 13, e);
        }
    }

    public File getLatestLogFile(String filePath) {
        //get the latest dump file
        File crashdir = new File(filePath);
        File[] files = crashdir.listFiles();
        File lastModifiedFile = null;
        if (files.length > 0) {
            log.success("Current POSSUM crash count since rebuild is: " + files.length, 9);

            //find the latest log file
            lastModifiedFile = files[0];
            for (int i = 1; i < files.length; i++) {
                if (lastModifiedFile.lastModified() < files[i].lastModified()) {
                    lastModifiedFile = files[i];
                }
            }
        }
        return lastModifiedFile;
    }

    public String getCoreDumpInfo (File logfile){
        String coreDumpInfo = null;
        try {
            BufferedReader reader = new BufferedReader(new FileReader(logfile));
            String probFrame = "";
            String crashTime = "";
            String line;
            while (null != (line = reader.readLine())) {
                if (line.contains("Problematic frame:")) {
                    probFrame = reader.readLine();
                }
                if (line.contains("Time:")){
                    crashTime = line;
                    break;
                }
            }
            reader.close();

            if (crashTime != "") {
                String time =  parseAndFormatCrashTime(crashTime);

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm:ss");
                try {
                    LocalDateTime localTimeObj = LocalDateTime.parse(time, formatter);

                    Duration duration = Duration.between(LocalDateTime.now(), localTimeObj);
                    long diff = Math.abs(duration.toMinutes());
                    if (diff <= 120) {
                        coreDumpInfo = "core dump happened within 2 hours - " + diff + " mins ago - " + logfile.getName() + " : " + probFrame + "; Crash " + crashTime;
                    } else {
                        coreDumpInfo = "core dump happened  - " + diff + " mins ago - " + logfile.getName() + " : " + probFrame + "; Crash " + crashTime;
                    }
                } catch (DateTimeParseException exp) {
                    log.failure("Failed to parsing the crash time: " + exp.getMessage(), 17, exp);
                }
            }
        } catch (IOException ioException) {
            log.failure("Failed reading core dump file" + logfile.getName() + ioException.getMessage(), 17, ioException);
        }
        return coreDumpInfo;
    }

    public String parseAndFormatCrashTime(String crashTime) {
        //parse the crash time
        String yyyy = "";
        String mon = "";
        String dd = "";
        String hhmmss = "";
        String pattern = "Time: \\S{3}\\s+(\\S{3})\\s+(\\d+)\\s+(.*)\\s+(\\d{4})\\s+\\S{3} elapsed";
        Pattern p = Pattern.compile(pattern);
        Matcher m  = p.matcher(crashTime);
        if (m.find( )) {
            mon =  m.group(1);
            dd = m.group(2);
            if (dd.length() == 1) {
                dd = "0"+dd;
            }
            hhmmss = m.group(3);
            yyyy = m.group(4);

        }
        return yyyy + "-" + mon + "-" +dd + " " + hhmmss;
    }
}
