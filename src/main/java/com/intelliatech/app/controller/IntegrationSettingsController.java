package com.intelliatech.app.controller;

import com.intelliatech.app.service.IntegrationSettingsService;
import com.intelliatech.app.service.IntegrationSettingsService.EmailConfig;
import com.intelliatech.app.service.IntegrationSettingsService.S3Config;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

@RestController
@RequestMapping("/api/settings/integrations")
@RequiredArgsConstructor
public class IntegrationSettingsController {
    private final IntegrationSettingsService settings;

    public record EmailRequest(boolean enabled, @NotBlank String host, @Min(1) @Max(65535) int port,
                               String username, String password, boolean auth, boolean startTls,
                               @NotBlank @Email String fromAddress) {}
    public record EmailResponse(boolean enabled, String host, int port, String username,
                                boolean passwordConfigured, boolean auth, boolean startTls, String fromAddress) {}
    public record S3Request(boolean enabled, String accessKeyId, String secretAccessKey,
                           @NotBlank String bucket, @NotBlank String region, String keyPrefix, String publicBaseUrl) {}
    public record S3Response(boolean enabled, String maskedAccessKeyId, boolean accessKeyConfigured,
                             boolean secretAccessKeyConfigured, String bucket, String region,
                             String keyPrefix, String publicBaseUrl) {}
    public record MessageResponse(String message) {}

    @PreAuthorize("hasRole('PLATFORM_SUPER_ADMIN')")
    @GetMapping("/email") public EmailResponse email() { return emailResponse(settings.email()); }
    @PreAuthorize("hasRole('PLATFORM_SUPER_ADMIN')")
    @PutMapping("/email") public EmailResponse saveEmail(@Valid @RequestBody EmailRequest request) {
        settings.saveEmail(new EmailConfig(request.enabled(), request.host(), request.port(), request.username(), request.password(), request.auth(), request.startTls(), request.fromAddress()));
        return emailResponse(settings.email());
    }
    @PreAuthorize("hasRole('PLATFORM_SUPER_ADMIN')")
    @PostMapping("/email/test") public MessageResponse testEmail() throws Exception {
        EmailConfig config = settings.email();
        if (!config.enabled()) throw new IllegalStateException("Enable email delivery before testing SMTP.");
        String recipient = config.username() != null && config.username().contains("@") ? config.username() : config.fromAddress();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(settings.effectiveMailFrom(config)); message.setTo(recipient);
        message.setSubject("IRAL email configuration test");
        message.setText("Your IRAL platform email configuration is working. Organization verification emails can now be delivered.");
        try {
            settings.mailSender().send(message);
        } catch (MailAuthenticationException exception) {
            throw new IllegalArgumentException("The SMTP provider rejected the saved username or password. For Gmail, enable 2-Step Verification and use a Google App Password instead of the normal account password.");
        } catch (MailException exception) {
            throw new IllegalArgumentException("The SMTP test email could not be sent. Check the SMTP host, port, STARTTLS setting, sender address and credentials.");
        }
        return new MessageResponse("A test email was sent successfully to the configured mailbox.");
    }

    @PreAuthorize("hasAnyRole('ADMIN','PLATFORM_SUPER_ADMIN')")
    @GetMapping("/s3") public S3Response s3() { return s3Response(settings.s3()); }
    @PreAuthorize("hasAnyRole('ADMIN','PLATFORM_SUPER_ADMIN')")
    @PutMapping("/s3") public S3Response saveS3(@Valid @RequestBody S3Request request) {
        settings.saveS3(new S3Config(request.enabled(), request.accessKeyId(), request.secretAccessKey(), request.bucket(), request.region(), request.keyPrefix(), request.publicBaseUrl()));
        return s3Response(settings.s3());
    }
    @PreAuthorize("hasAnyRole('ADMIN','PLATFORM_SUPER_ADMIN')")
    @PostMapping("/s3/test") public MessageResponse testS3() {
        S3Config config = settings.s3();
        try (var client = settings.s3Client()) { client.headBucket(HeadBucketRequest.builder().bucket(config.bucket()).build()); }
        return new MessageResponse("S3 connection and bucket access completed successfully.");
    }

    private EmailResponse emailResponse(EmailConfig value) {
        return new EmailResponse(value.enabled(), value.host(), value.port(), value.username(),
                value.password() != null && !value.password().isBlank(), value.auth(), value.startTls(), value.fromAddress());
    }
    private S3Response s3Response(S3Config value) {
        boolean access = value.accessKeyId() != null && !value.accessKeyId().isBlank();
        return new S3Response(value.enabled(), access ? mask(value.accessKeyId()) : "", access,
                value.secretAccessKey() != null && !value.secretAccessKey().isBlank(), value.bucket(), value.region(), value.keyPrefix(), value.publicBaseUrl());
    }
    private String mask(String value) { return value.length() <= 8 ? "••••••••" : value.substring(0, 4) + "••••••••" + value.substring(value.length() - 4); }
}
