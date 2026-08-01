package com.alexbarylski.jasperreporter.services;

import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.util.JRLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional in-memory cache for compiled JasperReport objects.
 * On startup it scans the /reports directory and pre-loads every .jasper file.
 */
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    static final String REPORTS_DIR = System.getenv().getOrDefault("REPORTS_DIR", "/reports");

    private final ConcurrentHashMap<String, JasperReport> cache = new ConcurrentHashMap<>();

    /** Scan REPORTS_DIR and load every .jasper file into the cache. */
    public void initialize() {
        Path reportsPath = Paths.get(REPORTS_DIR);
        if (!Files.isDirectory(reportsPath)) {
            log.warn("Reports directory not found at '{}', skipping cache initialization", REPORTS_DIR);
            return;
        }

        try {
            Files.walk(reportsPath)
                    .filter(p -> p.toString().endsWith(".jasper"))
                    .forEach(p -> {
                        String key = reportsPath.relativize(p).toString();
                        try {
                            JasperReport report = (JasperReport) JRLoader.loadObject(p.toFile());
                            cache.put(key, report);
                            log.info("Cached report: {}", key);
                        } catch (Exception e) {
                            log.warn("Failed to cache {}: {}", key, e.getMessage());
                        }
                    });
            log.info("Cache initialized with {} report(s)", cache.size());
        } catch (Exception e) {
            log.error("Error during cache initialization: {}", e.getMessage());
        }
    }

    /** Return a cached report or null if not present. */
    public JasperReport get(String relativePath) {
        return cache.get(relativePath);
    }

    /** Store or refresh a report in the cache. */
    public void put(String relativePath, JasperReport report) {
        cache.put(relativePath, report);
    }

    /** Remove an entry (e.g. after recompilation). */
    public void evict(String relativePath) {
        cache.remove(relativePath);
    }
}
