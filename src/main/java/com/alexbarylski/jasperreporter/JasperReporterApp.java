package com.alexbarylski.jasperreporter;

import com.alexbarylski.jasperreporter.controllers.CompileController;
import com.alexbarylski.jasperreporter.controllers.ListController;
import com.alexbarylski.jasperreporter.controllers.RenderController;
import com.alexbarylski.jasperreporter.services.CacheService;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JasperReporterApp {

    private static final Logger log = LoggerFactory.getLogger(JasperReporterApp.class);

    public static void main(String[] args) {
        CacheService cacheService = new CacheService();
        cacheService.initialize();

        CompileController compileController = new CompileController();
        RenderController renderController = new RenderController(cacheService);
        ListController listController = new ListController();

        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
        });

        app.post("/compile", compileController::handle);
        app.post("/render", renderController::handle);
        app.get("/list", listController::handle);

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        app.start(port);
        log.info("Jasper Reporter started on port {}", port);
    }
}
