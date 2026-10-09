package com.intelliatech.app.config;
import com.intelliatech.app.entity.AppUser; import com.intelliatech.app.repository.AppUserRepository;
import lombok.RequiredArgsConstructor; import org.springframework.boot.CommandLineRunner; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="app.bootstrap.default-admin-enabled", havingValue="true")
public class DefaultAdminInitializer implements CommandLineRunner {
 private final AppUserRepository users; private final PasswordEncoder encoder;
 @Override public void run(String... args){
  users.findByEmailIgnoreCase("admin@intelliatech.com").ifPresentOrElse(user->{
   if(user.getPasswordHash()==null){ user.setPasswordHash(encoder.encode("admin123")); users.save(user); }
  },()->{ AppUser user=new AppUser(); user.setName("Praveen Admin"); user.setEmail("admin@intelliatech.com"); user.setPasswordHash(encoder.encode("admin123")); user.setRoleName("Admin"); user.setStatus("Active"); user.setModuleAccess("All Modules"); users.save(user); });
 }
}
