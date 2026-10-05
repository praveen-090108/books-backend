package com.intelliatech.app.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI intelliaTechBooksOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("IntelliaTech Books API")
                        .version("v1")
                        .description("REST APIs for accounting, invoices, purchases, customers, and reports."));
    }
}
