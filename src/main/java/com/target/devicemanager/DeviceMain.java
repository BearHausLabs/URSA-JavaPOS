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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SpringBootApplication
@EnableScheduling
public class DeviceMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceMain.class);
    private static final StructuredEventLogger log = StructuredEventLogger.of(StructuredEventLogger.getDeviceManagerServiceName(), "DeviceMain", LOGGER);

    /** Known locations for possum-config.yml (in priority order). */
    private static final String[] CONFIG_LOCATIONS = {
            "config/possum-config.yml",
            "possum-config.yml"
    };

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
            System.setProperty("jpos.config.populator.file.0", devconPath);  // Required for multi-populator JCL (Toshiba)
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
                System.setProperty("jpos.config.populator.file.0", fallbackPath);  // Required for multi-populator JCL (Toshiba)
            } else {
                LOGGER.warn("No POSSUM_DEVCON_PATH env var and no config/devcon.xml found. " +
                        "JCL will rely on jpos/res/jpos.properties for populator file paths.");
            }
        }
        // Vendor jpos.xml merge into devcon.xml.
        // Canonical merge is done by configure-javapos.ps1 at deploy time.
        // This Java-side merge is a fallback for environments where the PS1
        // script hasn't run (e.g. manual dev setups). Gated behind env var
        // to prevent dual-merge drift with the PowerShell version.
        if ("true".equalsIgnoreCase(System.getenv("URSA_MERGE_VENDOR"))) {
            mergeVendorJposEntries();
        } else {
            LOGGER.info("Vendor jpos.xml merge skipped (URSA_MERGE_VENDOR not set). " +
                    "Using devcon.xml as-is from configure-javapos.ps1.");
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

    // -------------------------------------------------------------------------
    // Vendor jpos.xml merge — runs before Spring Boot starts
    // -------------------------------------------------------------------------

    /**
     * Reads vendor jpos.xml paths from possum-config.yml (or POSSUM_VENDOR_JPOS_PATHS
     * env var), parses JposEntry elements from each, and merges them into config/devcon.xml.
     * Scanner entries without a deviceType property get one injected automatically.
     * Duplicates (by logicalName) are skipped — existing devcon.xml entries win.
     *
     * This runs in main() before Spring Boot starts, so the JCL populator reads
     * a complete device registry on first load.
     */
    private static void mergeVendorJposEntries() {
        File devconFile = new File("config/devcon.xml");
        if (!devconFile.exists()) {
            LOGGER.info("config/devcon.xml not found — skipping vendor merge");
            return;
        }

        List<String> vendorPaths = getVendorJposPaths();
        if (vendorPaths.isEmpty()) {
            LOGGER.info("No vendor jpos.xml paths configured — skipping vendor merge");
            return;
        }

        try {
            // Parse existing devcon.xml entries (keyed by logicalName)
            String devconContent = Files.readString(devconFile.toPath(), StandardCharsets.UTF_8);
            LinkedHashMap<String, String> entries = parseJposEntries(devconContent);
            int originalCount = entries.size();

            // Parse and merge each vendor file
            int vendorEntriesAdded = 0;
            for (String vendorPath : vendorPaths) {
                File vendorFile = new File(vendorPath);
                if (!vendorFile.exists()) {
                    LOGGER.debug("Vendor jpos.xml not found (skipped): {}", vendorPath);
                    continue;
                }

                String vendorContent = Files.readString(vendorFile.toPath(), StandardCharsets.UTF_8);
                LinkedHashMap<String, String> vendorEntries = parseJposEntries(vendorContent);
                LOGGER.info("Vendor jpos.xml {} has {} entries", vendorPath, vendorEntries.size());

                for (Map.Entry<String, String> ve : vendorEntries.entrySet()) {
                    if (!entries.containsKey(ve.getKey())) {
                        entries.put(ve.getKey(), ve.getValue());
                        vendorEntriesAdded++;
                        LOGGER.info("  Merged vendor entry: {}", ve.getKey());
                    } else {
                        LOGGER.debug("  Skipped duplicate: {}", ve.getKey());
                    }
                }
            }

            if (vendorEntriesAdded == 0) {
                LOGGER.info("No new vendor entries to merge (all {} already in devcon.xml)", originalCount);
                return;
            }

            // Inject deviceType for Scanner entries that lack it
            injectScannerDeviceTypes(entries);

            // Write merged devcon.xml
            writeDevconXml(devconFile, entries);
            LOGGER.info("Merged devcon.xml: {} entries ({} original + {} from vendors)",
                    entries.size(), originalCount, vendorEntriesAdded);

        } catch (Exception e) {
            LOGGER.error("Failed to merge vendor jpos.xml entries into devcon.xml: {}", e.getMessage(), e);
        }
    }

    /**
     * Read vendor jpos.xml paths from config. Priority:
     * 1. POSSUM_VENDOR_JPOS_PATHS env var (semicolon-delimited)
     * 2. possum.vendor-jpos-paths in possum-config.yml
     */
    private static List<String> getVendorJposPaths() {
        // Check env var first
        String envPaths = System.getenv("POSSUM_VENDOR_JPOS_PATHS");
        if (envPaths != null && !envPaths.isBlank()) {
            List<String> paths = new ArrayList<>();
            for (String p : envPaths.split(";")) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty()) paths.add(trimmed);
            }
            LOGGER.info("Vendor jpos.xml paths from POSSUM_VENDOR_JPOS_PATHS: {}", paths);
            return paths;
        }

        // Parse from possum-config.yml (simple YAML list extraction)
        for (String configLoc : CONFIG_LOCATIONS) {
            File configFile = new File(configLoc);
            if (!configFile.exists()) continue;

            try {
                List<String> lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
                List<String> paths = new ArrayList<>();
                boolean inVendorPaths = false;

                for (String line : lines) {
                    String trimmed = line.trim();
                    // Detect the vendor-jpos-paths key
                    if (trimmed.startsWith("vendor-jpos-paths:")) {
                        inVendorPaths = true;
                        continue;
                    }
                    if (inVendorPaths) {
                        if (trimmed.startsWith("- ")) {
                            // YAML list item
                            String path = trimmed.substring(2).trim();
                            // Strip quotes if present
                            if ((path.startsWith("\"") && path.endsWith("\"")) ||
                                    (path.startsWith("'") && path.endsWith("'"))) {
                                path = path.substring(1, path.length() - 1);
                            }
                            if (!path.isEmpty()) paths.add(path);
                        } else if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                            // Non-list, non-comment line — we've left the list
                            break;
                        }
                    }
                }

                if (!paths.isEmpty()) {
                    LOGGER.info("Vendor jpos.xml paths from {}: {}", configLoc, paths);
                    return paths;
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to read {}: {}", configLoc, e.getMessage());
            }
        }

        return Collections.emptyList();
    }

    /**
     * Parse JposEntry elements from XML content. Returns a map of logicalName -> raw XML block.
     */
    private static LinkedHashMap<String, String> parseJposEntries(String xml) {
        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        // Match <JposEntry ...>...</JposEntry> blocks (multiline)
        Pattern entryPattern = Pattern.compile("<JposEntry\\s[^>]*>.*?</JposEntry>", Pattern.DOTALL);
        Pattern namePattern = Pattern.compile("logicalName=\"([^\"]+)\"");

        Matcher entryMatcher = entryPattern.matcher(xml);
        while (entryMatcher.find()) {
            String entry = entryMatcher.group();
            Matcher nameMatcher = namePattern.matcher(entry);
            if (nameMatcher.find()) {
                entries.put(nameMatcher.group(1), entry);
            }
        }
        return entries;
    }

    /**
     * Inject deviceType property into Scanner entries that lack it.
     * Flatbed vs HandScanner is inferred from the logical name pattern.
     */
    private static void injectScannerDeviceTypes(LinkedHashMap<String, String> entries) {
        List<String> keys = new ArrayList<>(entries.keySet());
        for (String key : keys) {
            String entry = entries.get(key);
            if (entry.contains("category=\"Scanner\"") && !entry.contains("name=\"deviceType\"")) {
                String type = key.matches("(?i).*(TableTop|Flatbed|MP7|8200|8400|8500|Magellan).*")
                        ? "Flatbed" : "HandScanner";
                // Insert deviceType prop before closing </JposEntry>
                entry = entry.replace("</JposEntry>",
                        "        <prop name=\"deviceType\" type=\"String\" value=\"" + type + "\"/>\n    </JposEntry>");
                entries.put(key, entry);
                LOGGER.info("  Injected deviceType={} into {}", type, key);
            }
        }
    }

    /**
     * Write merged entries to devcon.xml.
     */
    private static void writeDevconXml(File devconFile, LinkedHashMap<String, String> entries) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE JposEntries PUBLIC \"-//JavaPOS//DTD//EN\"\n");
        sb.append("                             \"jpos/res/jcl.dtd\">\n");
        sb.append("<JposEntries>\n");
        sb.append("<!--Auto-merged by URSA JavaPOS on startup-->\n\n");

        for (String entry : entries.values()) {
            sb.append("    ").append(entry).append("\n\n");
        }

        sb.append("</JposEntries>\n");
        Files.writeString(devconFile.toPath(), sb.toString(), StandardCharsets.UTF_8);
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
