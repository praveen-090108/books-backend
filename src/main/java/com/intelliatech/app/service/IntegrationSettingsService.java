package com.intelliatech.app.service;

import com.intelliatech.app.config.S3StorageProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.Properties;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Service
@RequiredArgsConstructor
public class IntegrationSettingsService {
    private static final String EMAIL_PREFIX = "EMAIL_";
    private static final String S3_PREFIX = "S3_";
    private final JdbcTemplate jdbc;
    private final CredentialEncryptionService encryption;
    private final S3StorageProperties s3Defaults;

    @Value("${spring.mail.host:localhost}") private String defaultMailHost;
    @Value("${spring.mail.port:25}") private int defaultMailPort;
    @Value("${spring.mail.username:}") private String defaultMailUsername;
    @Value("${spring.mail.password:}") private String defaultMailPassword;
    @Value("${spring.mail.properties.mail.smtp.auth:false}") private boolean defaultMailAuth;
    @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}") private boolean defaultMailStartTls;
    @Value("${app.mail.from:no-reply@iral.app}") private String defaultMailFrom;

    public record EmailConfig(boolean enabled, String host, int port, String username, String password,
                              boolean auth, boolean startTls, String fromAddress) {}
    public record S3Config(boolean enabled, String accessKeyId, String secretAccessKey, String bucket,
                           String region, String keyPrefix, String publicBaseUrl) {}

    public EmailConfig email() {
        Map<String,String> values = load(EMAIL_PREFIX);
        int port = integer(values, "EMAIL_PORT", defaultMailPort);
        return new EmailConfig(bool(values, "EMAIL_ENABLED", false),
                value(values, "EMAIL_HOST", defaultMailHost), port,
                value(values, "EMAIL_USERNAME", defaultMailUsername), value(values, "EMAIL_PASSWORD", defaultMailPassword),
                bool(values, "EMAIL_AUTH", defaultMailAuth), bool(values, "EMAIL_STARTTLS", defaultMailStartTls) || port == 587,
                value(values, "EMAIL_FROM", defaultMailFrom));
    }

    public S3Config s3() {
        Map<String,String> values = load(S3_PREFIX);
        return new S3Config(bool(values, "S3_ENABLED", s3Defaults.active()),
                value(values, "S3_ACCESS_KEY_ID", s3Defaults.accessKeyId()),
                value(values, "S3_SECRET_ACCESS_KEY", s3Defaults.secretAccessKey()),
                value(values, "S3_BUCKET", s3Defaults.bucket()), value(values, "S3_REGION", s3Defaults.region()),
                value(values, "S3_KEY_PREFIX", s3Defaults.keyPrefix()), value(values, "S3_PUBLIC_BASE_URL", s3Defaults.publicBaseUrl()));
    }

    public JavaMailSenderImpl mailSender() {
        EmailConfig config = email();
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.host()); sender.setPort(config.port());
        sender.setUsername(config.username()); sender.setPassword(config.password());
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", String.valueOf(config.auth()));
        properties.put("mail.smtp.starttls.enable", String.valueOf(config.startTls() || config.port() == 587));
        properties.put("mail.smtp.starttls.required", String.valueOf(config.port() == 587));
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        return sender;
    }

    public String effectiveMailFrom(EmailConfig config) {
        if (config.host() != null && config.host().toLowerCase().contains("gmail.com") && StringUtils.hasText(config.username())) {
            return config.username().trim();
        }
        return config.fromAddress();
    }

    public S3Client s3Client() {
        S3Config config = s3();
        var builder = S3Client.builder().region(Region.of(config.region()));
        if (StringUtils.hasText(config.accessKeyId()) && StringUtils.hasText(config.secretAccessKey())) {
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(config.accessKeyId(), config.secretAccessKey())));
        } else builder.credentialsProvider(DefaultCredentialsProvider.create());
        return builder.build();
    }

    @Transactional
    public void saveEmail(EmailConfig request) {
        EmailConfig current = email();
        String password = StringUtils.hasText(request.password()) ? request.password() : current.password();
        require(request.host(), "SMTP host"); require(request.fromAddress(), "From address");
        if (request.port() < 1 || request.port() > 65535) throw new IllegalArgumentException("SMTP port must be between 1 and 65535.");
        boolean startTls = request.startTls() || request.port() == 587;
        save(Map.of("EMAIL_ENABLED", String.valueOf(request.enabled()), "EMAIL_HOST", request.host().trim(),
                "EMAIL_PORT", String.valueOf(request.port()), "EMAIL_USERNAME", clean(request.username()),
                "EMAIL_PASSWORD", clean(password), "EMAIL_AUTH", String.valueOf(request.auth()),
                "EMAIL_STARTTLS", String.valueOf(startTls), "EMAIL_FROM", request.fromAddress().trim()));
    }

    @Transactional
    public void saveS3(S3Config request) {
        S3Config current = s3();
        String access = StringUtils.hasText(request.accessKeyId()) ? request.accessKeyId() : current.accessKeyId();
        String secret = StringUtils.hasText(request.secretAccessKey()) ? request.secretAccessKey() : current.secretAccessKey();
        require(request.bucket(), "S3 bucket"); require(request.region(), "AWS region");
        save(Map.of("S3_ENABLED", String.valueOf(request.enabled()), "S3_ACCESS_KEY_ID", clean(access),
                "S3_SECRET_ACCESS_KEY", clean(secret), "S3_BUCKET", request.bucket().trim(),
                "S3_REGION", request.region().trim(), "S3_KEY_PREFIX", clean(request.keyPrefix()),
                "S3_PUBLIC_BASE_URL", clean(request.publicBaseUrl())));
    }

    private Map<String,String> load(String prefix) {
        Map<String,String> result = new HashMap<>();
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT setting_key,encrypted_value FROM integration_settings WHERE setting_key LIKE ?", prefix + "%");
        for (Map<String,Object> row : rows) result.put(String.valueOf(row.get("setting_key")), encryption.decrypt(String.valueOf(row.get("encrypted_value"))));
        return result;
    }
    private void save(Map<String,String> values) {
        values.forEach((key, value) -> {
            if (!StringUtils.hasText(value)) {
                // Optional integration fields are represented by the absence of a
                // row. Never pass an empty value to credential encryption, which
                // deliberately rejects empty plaintext.
                jdbc.update("DELETE FROM integration_settings WHERE setting_key=?", key);
                return;
            }
            jdbc.update("INSERT INTO integration_settings(setting_key,encrypted_value) VALUES (?,?) ON DUPLICATE KEY UPDATE encrypted_value=VALUES(encrypted_value),updated_at=CURRENT_TIMESTAMP(6)",
                    key, encryption.encrypt(value));
        });
    }
    private String value(Map<String,String> values, String key, String fallback) { return values.getOrDefault(key, fallback == null ? "" : fallback); }
    private boolean bool(Map<String,String> values, String key, boolean fallback) { return Boolean.parseBoolean(value(values, key, String.valueOf(fallback))); }
    private int integer(Map<String,String> values, String key, int fallback) { try { return Integer.parseInt(value(values, key, String.valueOf(fallback))); } catch (NumberFormatException ignored) { return fallback; } }
    private String clean(String value) { return value == null ? "" : value.trim(); }
    private void require(String value, String label) { if (!StringUtils.hasText(value)) throw new IllegalArgumentException(label + " is required."); }
}
