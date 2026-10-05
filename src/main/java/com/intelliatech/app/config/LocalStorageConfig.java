package com.intelliatech.app.config;

import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(LocalStorageProperties.class)
public class LocalStorageConfig implements WebMvcConfigurer {

    private final LocalStorageProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (!properties.enabled()) {
            return;
        }
        Path storageRoot = Path.of(properties.directory()).toAbsolutePath().normalize();
        String resourceLocation = storageRoot.toUri().toString();
        // Spring appends the path matched by /uploads/** to this location.
        // A directory resource location must end in a slash; otherwise the
        // first path segment is concatenated to the directory name itself.
        if (!resourceLocation.endsWith("/")) {
            resourceLocation += "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(resourceLocation);
    }
}
