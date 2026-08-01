package com.alexbarylski.jasperreporter.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ListService {

    private static final Logger log = LoggerFactory.getLogger(ListService.class);
    private static final String REPORTS_DIR = CacheService.REPORTS_DIR;

    /**
     * Return all .jrxml and .jasper file paths relative to the reports directory.
     *
     * @return sorted list of relative paths
     * @throws IOException if the reports directory cannot be scanned
     */
    public List<String> list() throws IOException {
        Path reportsPath = Paths.get(REPORTS_DIR).toAbsolutePath().normalize();

        if (!Files.isDirectory(reportsPath)) {
            throw new IOException("Reports directory not found: " + REPORTS_DIR);
        }

        List<String> files = new ArrayList<>();
        Files.walk(reportsPath)
                .filter(p -> {
                    String name = p.toString();
                    return name.endsWith(".jrxml") || name.endsWith(".jasper");
                })
                .forEach(p -> files.add(reportsPath.relativize(p).toString()));

        Collections.sort(files);
        log.debug("Found {} report file(s)", files.size());
        return files;
    }
}
