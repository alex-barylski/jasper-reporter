package com.alexbarylski.jasperreporter.controllers;

import com.alexbarylski.jasperreporter.models.ErrorResponse;
import com.alexbarylski.jasperreporter.services.ListService;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class ListController {

    private static final Logger log = LoggerFactory.getLogger(ListController.class);

    private final ListService listService = new ListService();

    public void handle(Context ctx) {
        try {
            List<String> files = listService.list();
            ctx.status(200).json(Map.of(
                    "success", true,
                    "files",   files
            ));
        } catch (Exception e) {
            log.error("List failed: {}", e.getMessage(), e);
            ctx.status(500).json(new ErrorResponse("Unable to scan reports directory", e.getMessage()));
        }
    }
}
