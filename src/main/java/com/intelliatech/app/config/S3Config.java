package com.intelliatech.app.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({S3StorageProperties.class, StorageProperties.class})
public class S3Config {

    private final S3StorageProperties properties;

    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.region()));
        if (StringUtils.hasText(properties.accessKeyId())
                && StringUtils.hasText(properties.secretAccessKey())) {
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                    properties.accessKeyId().trim(),
                    properties.secretAccessKey().trim()
            )));
        }
        return builder.build();
    }
}
