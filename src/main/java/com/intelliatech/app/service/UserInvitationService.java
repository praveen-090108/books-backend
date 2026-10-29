package com.intelliatech.app.service;

import com.intelliatech.app.dto.request.AcceptInvitationRequest;
import com.intelliatech.app.dto.request.UserRequest;
import com.intelliatech.app.dto.response.InvitationDetailsResponse;
import com.intelliatech.app.dto.response.UserResponse;
import com.intelliatech.app.entity.*;
import com.intelliatech.app.exception.DuplicateResourceException;
import com.intelliatech.app.exception.ResourceNotFoundException;
import com.intelliatech.app.exception.ResourceConflictException;
import com.intelliatech.app.repository.*;
import com.intelliatech.app.security.CurrentUserService;
import com.intelliatech.app.security.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class UserInvitationService {
    private static final String PASSWORD_POLICY="(?s)(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{12,128}";
    private final UserInvitationRepository invitations;
    private final AppUserRepository users;
    private final BusinessRecordRepository records;
    private final OrganizationMembershipRepository memberships;
    private final TenantContext tenants;
    private final CurrentUserService currentUsers;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher events;
    private final AppUserService userResponses;

    @Transactional
    public UserResponse invite(UserRequest request){
        Long organizationId=tenants.requireOrganizationId();
        BusinessRecord resource=records.findByOrganizationIdAndModuleAndTypeAndId(organizationId,"resources","resources",request.resourceId())
                .orElseThrow(()->new ResourceNotFoundException("Selected resource was not found"));
        String email=resource.getPartyEmail()==null?"":resource.getPartyEmail().trim();
        if(!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new IllegalArgumentException("The selected resource must have a valid email address. Update it in Resources and try again.");
        users.findByResourceId(resource.getId()).ifPresent(existing->{throw new DuplicateResourceException("A user account already exists for this resource. Use Resend Invitation when it is pending.");});
        if(!users.findAllForLoginEmail(email).isEmpty()){
            throw new ResourceConflictException("This email address is already registered with an organization. Each email address can belong to only one organization.");
        }
        OrganizationRole role=userResponses.resolveAssignableRole(request.role());
        if(role.isSystemRole()&&"PLATFORM_SUPER_ADMIN".equals(role.getCode())) throw new IllegalArgumentException("Platform roles cannot be assigned by an organization administrator.");
        AppUser user=new AppUser(); user.setResource(resource); user.setEmail(email.toLowerCase(java.util.Locale.ROOT)); user.setRoleName(role.getName()); user.setStatus("Pending Invitation");
        user.setAccountStatus("PENDING_INVITATION"); user.setEmailVerified(false); user.setPasswordHash(null);
        user=users.save(user);
        OrganizationMembership membership=new OrganizationMembership(); membership.setOrganization(role.getOrganization());membership.setUser(user);membership.setRole(role);membership.setStatus(OrganizationMembership.Status.PENDING);memberships.save(membership);
        issue(user,membership,resource);
        return userResponses.response(user,role.getName(),organizationId);
    }

    @Transactional
    public UserResponse resend(Long userId){
        Long organizationId=tenants.requireOrganizationId();
        OrganizationMembership membership=memberships.findByOrganizationIdAndUserId(organizationId,userId).orElseThrow(()->new ResourceNotFoundException("User not found"));
        if(membership.getStatus()!=OrganizationMembership.Status.PENDING) throw new IllegalArgumentException("Only pending invitations can be resent.");
        invitations.findFirstByOrganizationIdAndUserIdOrderByCreatedAtDesc(organizationId,userId).ifPresent(latest->{
            if(latest.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(1))) throw new IllegalArgumentException("Please wait one minute before resending this invitation.");
        });
        issue(membership.getUser(),membership,membership.getUser().getResource());
        return userResponses.response(membership.getUser(),membership.getRole().getName(),organizationId);
    }

    @Transactional
    public void cancel(Long userId){
        Long organizationId=tenants.requireOrganizationId();
        OrganizationMembership membership=memberships.findByOrganizationIdAndUserId(organizationId,userId).orElseThrow(()->new ResourceNotFoundException("User not found"));
        if(membership.getStatus()!=OrganizationMembership.Status.PENDING) throw new IllegalArgumentException("Only pending invitations can be cancelled.");
        invitations.findAllByOrganizationIdAndUserIdAndStatus(organizationId,userId,UserInvitation.Status.PENDING).forEach(invitation->{invitation.setStatus(UserInvitation.Status.CANCELLED);invitation.setRevokedAt(LocalDateTime.now());});
        membership.setStatus(OrganizationMembership.Status.DEACTIVATED); membership.getUser().setStatus("Inactive");membership.getUser().setAccountStatus("INACTIVE");membership.getUser().setTokenVersion(membership.getUser().getTokenVersion()+1);
    }

    @Transactional(readOnly=true)
    public InvitationDetailsResponse validate(String rawToken){
        UserInvitation invitation=valid(rawToken);
        return new InvitationDetailsResponse(invitation.getOrganization().getOrganizationName(),invitation.getResource().getPartyName(),invitation.getResource().getPartyEmail(),invitation.getUser().getRoleName(),"Pending Invitation",invitation.getExpiresAt());
    }

    @Transactional
    public void accept(AcceptInvitationRequest request){
        if(!request.password().matches(PASSWORD_POLICY)) throw new IllegalArgumentException("Password must contain uppercase, lowercase, number and special characters and be at least 12 characters.");
        UserInvitation invitation=valid(request.token()); AppUser user=invitation.getUser();
        OrganizationMembership membership=memberships.findByOrganizationIdAndUserId(invitation.getOrganization().getId(),user.getId()).orElseThrow(()->new IllegalArgumentException("This invitation is invalid or has expired."));
        user.setPasswordHash(encoder.encode(request.password()));user.setEmailVerified(true);user.setStatus("Active");user.setAccountStatus("ACTIVE");user.setFailedLoginAttempts(0);user.setLockedUntil(null);
        membership.setStatus(OrganizationMembership.Status.ACTIVE);membership.setJoinedAt(LocalDateTime.now());
        invitation.setStatus(UserInvitation.Status.ACCEPTED);invitation.setAcceptedAt(LocalDateTime.now());invitation.setTokenHash(hash(randomToken()));
    }

    private void issue(AppUser user,OrganizationMembership membership,BusinessRecord resource){
        LocalDateTime now=LocalDateTime.now();
        invitations.findAllByOrganizationIdAndUserIdAndStatus(membership.getOrganization().getId(),user.getId(),UserInvitation.Status.PENDING).forEach(invitation->{invitation.setStatus(UserInvitation.Status.CANCELLED);invitation.setRevokedAt(now);});
        String token=randomToken();UserInvitation invitation=new UserInvitation();invitation.setOrganization(membership.getOrganization());invitation.setUser(user);invitation.setResource(resource);invitation.setTokenHash(hash(token));invitation.setStatus(UserInvitation.Status.PENDING);invitation.setDeliveryStatus(UserInvitation.DeliveryStatus.QUEUED);invitation.setExpiresAt(now.plusHours(48));invitation.setCreatedBy(currentUsers.findCurrentUser().orElse(null));invitation=invitations.save(invitation);
        events.publishEvent(new UserInvitationEmailRequested(invitation.getId(),resource.getPartyEmail(),resource.getPartyName(),membership.getOrganization().getOrganizationName(),membership.getRole().getName(),token));
    }

    private UserInvitation valid(String rawToken){
        UserInvitation invitation=invitations.findByTokenHash(hash(rawToken)).orElseThrow(()->new IllegalArgumentException("This invitation link is invalid or has expired."));
        if(invitation.getStatus()!=UserInvitation.Status.PENDING||invitation.getExpiresAt().isBefore(LocalDateTime.now())||invitation.getOrganization().getStatus()!=Organization.Status.ACTIVE){
            if(invitation.getStatus()==UserInvitation.Status.PENDING&&invitation.getExpiresAt().isBefore(LocalDateTime.now())) invitation.setStatus(UserInvitation.Status.EXPIRED);
            throw new IllegalArgumentException("This invitation link is invalid or has expired.");
        }
        return invitation;
    }
    private String randomToken(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return HexFormat.of().formatHex(bytes);}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
