package dev.likezendesk.devtools.app;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 起動時にデータを投入する。{@code app.seed} にカンマ区切りで {@code demo}（F-D-1）、{@code load}（F-D-2）を指定する。
 *
 * <p>dev / e2e プロファイルでだけ動くので、本番相当の環境には入らない（data-model.md）。{@code app.seed-exit=true} なら投入後に終了する。
 */
@Component
@Profile({"dev", "e2e"})
class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final DemoData demo;
    private final LoadData load;
    private final ConfigurableApplicationContext context;
    private final String seed;
    private final boolean exit;

    SeedRunner(
            DemoData demo,
            LoadData load,
            ConfigurableApplicationContext context,
            @Value("${app.seed:}") String seed,
            @Value("${app.seed-exit:false}") boolean exit) {
        this.demo = demo;
        this.load = load;
        this.context = context;
        this.seed = seed;
        this.exit = exit;
    }

    @Override
    public void run(ApplicationArguments args) {
        var kinds = Arrays.stream(seed.split(",")).map(String::strip).toList();
        if (kinds.contains("demo")) {
            log.info("Demo data: {} tenant(s) created", demo.seed());
        }
        if (kinds.contains("load")) {
            log.info("Load data: {}", load.seed() ? "created" : "already present");
        }
        if (exit) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
