package com.alexbarylski.jasperreporter.controllers;

import com.alexbarylski.jasperreporter.models.CompileRequest;
import com.alexbarylski.jasperreporter.models.ErrorResponse;
import com.alexbarylski.jasperreporter.services.CompileService;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class CompileController {

    private static final Logger log = LoggerFactory.getLogger(CompileController.class);

    private final CompileService compileService = new CompileService();

    public void handle(Context ctx) {
        CompileRequest request;
        try {
            request = ctx.bodyAsClass(CompileRequest.class);
        } catch (Exception e) {
            ctx.status(400).json(new ErrorResponse("Invalid request body", e.getMessage()));
            return;
        }

        try {
            compileService.compile(request);

            ctx.status(200).json(Map.of(
                    "success", true,
                    "source",  request.getSource(),
                    "target",  request.getTarget()
            ));
        } catch (IllegalArgumentException | java.io.IOException e) {
            log.warn("Compile validation error: {}", e.getMessage());
            ctx.status(400).json(new ErrorResponse("Validation failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Compilation failed for '{}': {}", request.getSource(), e.getMessage(), e);
            ctx.status(500).json(new ErrorResponse("Compilation failed", e.getMessage()));
        }
    }
}
