package com.intelliatech.app.security;
import com.intelliatech.app.repository.AppUserRepository; import com.intelliatech.app.service.AppUserService; import jakarta.servlet.*; import jakarta.servlet.http.*; import java.io.IOException; import java.util.*;
import lombok.RequiredArgsConstructor; import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; import org.springframework.security.core.authority.SimpleGrantedAuthority; import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter;
@Component @RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
 private final JwtService jwt; private final AppUserRepository users; private final AppUserService userService;
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  String header=req.getHeader("Authorization");
  if(SecurityContextHolder.getContext().getAuthentication()==null && header!=null && header.startsWith("Bearer ") && !header.startsWith("Bearer demo-token-")){
   String token=header.substring(7);
   if(jwt.isValid(token)){ String loginEmail = jwt.extractUsername(token); users.findForLoginEmail(loginEmail).filter(u->"Active".equalsIgnoreCase(u.getStatus())).ifPresent(u->{
    String role=u.getRoleName().toUpperCase().replaceAll("[^A-Z0-9]","_");
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
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(loginEmail,null,authorities));
   }); }
  }
  chain.doFilter(req,res);
 }
}
