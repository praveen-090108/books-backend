package com.intelliatech.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import com.intelliatech.app.entity.UserInvitation;
import com.intelliatech.app.repository.UserInvitationRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

@Service @RequiredArgsConstructor @Slf4j
public class VerificationEmailService {
    private final IntegrationSettingsService integrationSettings;
    private final UserInvitationRepository invitations;
    @Value("${app.frontend-url}") private String frontendUrl;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(VerificationEmailRequested event) {
        String link = UriComponentsBuilder.fromUriString(frontendUrl).path("/verify-email")
                .queryParam("token", event.token()).build().toUriString();
        SimpleMailMessage message = new SimpleMailMessage();
        IntegrationSettingsService.EmailConfig config = integrationSettings.email();
        if (!config.enabled()) { log.error("IRAL verification email delivery skipped because SMTP integration is disabled"); return; }
        message.setFrom(integrationSettings.effectiveMailFrom(config));
        message.setTo(event.recipient());
        message.setSubject("Verify your IRAL organization");
        message.setText("Hello " + event.recipientName() + ",\n\nVerify your email to activate your IRAL organization:\n"
                + link + "\n\nThis link expires in 30 minutes and can be used once.\n\nIRAL");
        try {
            integrationSettings.mailSender().send(message);
            log.info("IRAL verification email accepted for delivery recipientDomain={}", domain(event.recipient()));
        } catch (RuntimeException exception) {
            // The account stays pending and can use resend after SMTP is restored. Never log the token or address.
            log.error("IRAL verification email delivery failed recipientDomain={} exception={}",
                    domain(event.recipient()), exception.getClass().getSimpleName());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendPasswordReset(PasswordResetEmailRequested event) {
        String link = UriComponentsBuilder.fromUriString(frontendUrl).path("/reset-password")
                .queryParam("token", event.token()).build().toUriString();
        SimpleMailMessage message = new SimpleMailMessage();
        IntegrationSettingsService.EmailConfig config = integrationSettings.email();
        if (!config.enabled()) { log.error("IRAL password reset email delivery skipped because SMTP integration is disabled"); return; }
        message.setFrom(integrationSettings.effectiveMailFrom(config)); message.setTo(event.recipient()); message.setSubject("Reset your IRAL password");
        message.setText("Hello " + event.recipientName() + ",\n\nReset your IRAL password using this secure link:\n"
                + link + "\n\nThis link expires in 30 minutes and can be used once.\n\nIRAL");
        try { integrationSettings.mailSender().send(message); log.info("IRAL password reset email accepted for delivery recipientDomain={}", domain(event.recipient())); }
        catch (RuntimeException exception) { log.error("IRAL password reset email delivery failed recipientDomain={} exception={}", domain(event.recipient()), exception.getClass().getSimpleName()); }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendInvitation(UserInvitationEmailRequested event) {
        UserInvitation invitation=invitations.findById(event.invitationId()).orElse(null);
        if(invitation==null)return;
        IntegrationSettingsService.EmailConfig config=integrationSettings.email();
        if(!config.enabled()){markFailed(invitation,"SMTP integration is disabled");return;}
        String link=UriComponentsBuilder.fromUriString(frontendUrl).path("/accept-invitation").queryParam("token",event.token()).build().toUriString();
        try {
            var message=integrationSettings.mailSender().createMimeMessage();
            var helper=new MimeMessageHelper(message,true,"UTF-8");
            helper.setFrom(integrationSettings.effectiveMailFrom(config));helper.setTo(event.recipient());helper.setSubject("You're Invited to Join IRAL");
            String html="<div style='font-family:Arial,sans-serif;max-width:620px;margin:auto;color:#101b46'><h1 style='color:#2563eb'>IRAL</h1><h2>You're invited</h2><p>Hello "+escape(event.recipientName())+",</p><p>You have been invited to access IRAL for <b>"+escape(event.organizationName())+"</b>.</p><p><b>Username:</b> "+escape(event.recipient())+"<br><b>Assigned role:</b> "+escape(event.role())+"</p><p><a style='display:inline-block;background:linear-gradient(90deg,#2563eb,#7c3aed);color:white;padding:12px 22px;border-radius:8px;text-decoration:none;font-weight:bold' href='"+link+"'>Accept Invitation</a></p><p>This secure invitation expires in 48 hours and can be used once.</p><p>If you were not expecting this invitation, contact your organization administrator.</p><p>Regards,<br>IRAL Team</p></div>";
            helper.setText("Hello "+event.recipientName()+",\n\nYou have been invited to IRAL for "+event.organizationName()+".\nUsername: "+event.recipient()+"\nRole: "+event.role()+"\nActivate your account: "+link+"\n\nThis invitation expires in 48 hours.\n\nIRAL Team",html);
            integrationSettings.mailSender().send(message);markSent(invitation);log.info("IRAL invitation accepted for delivery invitationId={} organizationId={} recipientDomain={}",invitation.getId(),invitation.getOrganization().getId(),domain(event.recipient()));
        } catch(Exception exception){markFailed(invitation,exception.getClass().getSimpleName());log.error("IRAL invitation delivery failed invitationId={} organizationId={} recipientDomain={} exception={}",invitation.getId(),invitation.getOrganization().getId(),domain(event.recipient()),exception.getClass().getSimpleName());}
    }

    @Transactional protected void markSent(UserInvitation invitation){invitation.setDeliveryStatus(UserInvitation.DeliveryStatus.SENT);invitation.setSentAt(java.time.LocalDateTime.now());invitation.setLastDeliveryError(null);invitations.save(invitation);}
    @Transactional protected void markFailed(UserInvitation invitation,String reason){invitation.setDeliveryStatus(UserInvitation.DeliveryStatus.FAILED);invitation.setLastDeliveryError(reason);invitations.save(invitation);}
    private String escape(String value){return value==null?"":value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}

    private String domain(String email) {
        int at = email == null ? -1 : email.lastIndexOf('@');
        return at < 0 ? "invalid" : email.substring(at + 1).toLowerCase();
    }
}
