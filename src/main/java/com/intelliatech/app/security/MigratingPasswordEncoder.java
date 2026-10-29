package com.intelliatech.app.security;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Encodes all new passwords with Argon2id while accepting legacy BCrypt hashes. */
public final class MigratingPasswordEncoder implements PasswordEncoder {
    private final PasswordEncoder argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final PasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Override public String encode(CharSequence rawPassword) { return argon2.encode(rawPassword); }
    @Override public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null) return false;
        return encodedPassword.startsWith("$2")
                ? bcrypt.matches(rawPassword, encodedPassword)
                : argon2.matches(rawPassword, encodedPassword);
    }
    @Override public boolean upgradeEncoding(String encodedPassword) {
        return encodedPassword != null && encodedPassword.startsWith("$2");
    }
}
