package com.intelliatech.app.security;
import com.intelliatech.app.repository.AppUserRepository; import com.intelliatech.app.repository.OrganizationMembershipRepository; import com.intelliatech.app.entity.Organization; import com.intelliatech.app.entity.OrganizationMembership; import com.intelliatech.app.service.AppUserService; import jakarta.servlet.*; import jakarta.servlet.http.*; import java.io.IOException; import java.util.*;
import lombok.RequiredArgsConstructor; import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; import org.springframework.security.core.authority.SimpleGrantedAuthority; import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter;
@Component @RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
 private final JwtService jwt; private final AppUserRepository users; private final OrganizationMembershipRepository memberships; private final AppUserService userService;
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  try {
   String header=req.getHeader("Authorization");
   if(SecurityContextHolder.getContext().getAuthentication()==null && header!=null && header.startsWith("Bearer ") && !header.startsWith("Bearer demo-token-")){
   String token=header.substring(7);
   if(jwt.isValid(token)){ Long userId = jwt.extractUserId(token); users.findById(userId).filter(u->"Active".equalsIgnoreCase(u.getStatus()) && "ACTIVE".equalsIgnoreCase(u.getAccountStatus()) && u.isEmailVerified() && u.getTokenVersion()==jwt.extractUserVersion(token)).ifPresent(u->{
    if(jwt.isPlatformAdmin(token) && u.isPlatformAdmin()) {
     SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUserPrincipal(u.getId(),principalName(u)),null,List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_SUPER_ADMIN"))));
     return;
    }
    OrganizationMembership membership=memberships.findByIdAndUserId(jwt.extractMembershipId(token),u.getId()).filter(m->m.getStatus()==OrganizationMembership.Status.ACTIVE && m.getOrganization().getStatus()==Organization.Status.ACTIVE && m.getOrganization().getSecurityVersion()==jwt.extractOrganizationVersion(token)).orElse(null);
    if(membership==null)return;
    if(!membership.getOrganization().getId().equals(jwt.extractOrganizationId(token)))return;
    TenantContextHolder.setOrganizationId(membership.getOrganization().getId());
    String role=securityRole(u.getRoleName());
    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
    authorities.add(new SimpleGrantedAuthority("ROLE_"+role));
    userService.permissionsFor(u.getRoleName()).stream()
      .filter(permission -> permission != null && !permission.isBlank())
      .map(String::trim)
      .map(String::toUpperCase)
      .distinct()
      .map(SimpleGrantedAuthority::new)
      .forEach(authorities::add);
    // The validated JWT subject is already the canonical login email. Do not
    // dereference the user's lazy Resource association in the servlet filter.
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUserPrincipal(u.getId(),principalName(u)),null,authorities));
   }); }
   }
   chain.doFilter(req,res);
  } finally {
   TenantContextHolder.clear();
  }
 }

 private String securityRole(String roleName) {
  String role = roleName == null ? "USER" : roleName.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
  // Organization administrators are the tenant-level administrators and must
  // remain compatible with the existing endpoints protected by ROLE_ADMIN.
  return role.contains("ADMIN") ? "ADMIN" : role;
 }

 private String principalName(com.intelliatech.app.entity.AppUser user) {
  // CurrentUserService resolves authenticated users by id. Avoid touching the
  // lazy Resource association in the servlet filter after its repository
  // transaction has closed.
  return user.getEmail() == null || user.getEmail().isBlank() ? String.valueOf(user.getId()) : user.getEmail();
 }
}
