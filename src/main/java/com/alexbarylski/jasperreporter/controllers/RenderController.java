package com.alexbarylski.jasperreporter.controllers;

import com.alexbarylski.jasperreporter.models.ErrorResponse;
import com.alexbarylski.jasperreporter.models.RenderRequest;
import com.alexbarylski.jasperreporter.services.CacheService;
import com.alexbarylski.jasperreporter.services.RenderService;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RenderController {

    private static final Logger log = LoggerFactory.getLogger(RenderController.class);

    private final RenderService renderService;

    public RenderController(CacheService cacheService) {
        this.renderService = new RenderService(cacheService);
    }

    public void handle(Context ctx) {
        RenderRequest request;
        try {
            request = ctx.bodyAsClass(RenderRequest.class);
        } catch (Exception e) {
            ctx.status(400).json(new ErrorResponse("Invalid request body", e.getMessage()));
            return;
        }

        try {
            byte[] output = renderService.render(request);

            if (request.isDryRun()) {
                ctx.status(200).json(java.util.Map.of(
                        "success", true,
                        "report",  request.getReport(),
                        "format",  request.getFormat()
                ));
                return;
            }

            String format = request.getFormat().toLowerCase(java.util.Locale.ROOT);
            String contentType = renderService.contentTypeFor(format);
            ctx.status(200)
               .contentType(contentType)
               .result(output);

        } catch (IllegalArgumentException | java.io.IOException e) {
            log.warn("Render validation error: {}", e.getMessage());
            ctx.status(400).json(new ErrorResponse("Validation failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Render failed for '{}': {}", request.getReport(), e.getMessage(), e);
            ctx.status(500).json(new ErrorResponse("Render failed", e.getMessage()));
        }
    }
}
