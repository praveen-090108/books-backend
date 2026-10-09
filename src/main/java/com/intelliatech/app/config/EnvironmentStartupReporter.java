package com.intelliatech.app.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EnvironmentStartupReporter implements ApplicationRunner {
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        String mode = environment.getProperty("app.environment", "sandbox");
        String database = databaseName(environment.getProperty("spring.datasource.url", "unknown"));
        String port = environment.getProperty("server.port", "8080");
        String level = environment.getProperty("logging.level.com.intelliatech.app", "INFO");
        log.info("\n=========================================\n"
                        + " INTELLIATECH BACKEND\n"
                        + "=========================================\n"
                        + " Active Profile : {}\n Server Port    : {}\n Database       : {}\n Logging        : {}\n"
                        + "=========================================",
                mode.toUpperCase(), port, database, level.toUpperCase());
    }

    private String databaseName(String jdbcUrl) {
        int slash = jdbcUrl.lastIndexOf('/');
        if (slash < 0 || slash == jdbcUrl.length() - 1) return "unknown";
        String value = jdbcUrl.substring(slash + 1);
        int query = value.indexOf('?');
        return query >= 0 ? value.substring(0, query) : value;
    }
}
