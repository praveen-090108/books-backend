package com.intelliatech.app.service;

import com.intelliatech.app.exception.RateLimitExceededException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthRateLimitService {
    private final JdbcTemplate jdbc;

    @Transactional
    public void check(String operation, String subject, int maximumAttempts, Duration window) {
        String key = hash(operation + ":" + String.valueOf(subject).trim().toLowerCase());
        long seconds = window.toSeconds();
        jdbc.update("""
                INSERT INTO auth_rate_limits(bucket_key,attempt_count,window_started_at,expires_at)
                VALUES (?,1,NOW(6),DATE_ADD(NOW(6), INTERVAL ? SECOND))
                ON DUPLICATE KEY UPDATE
                  attempt_count=IF(expires_at<=NOW(6),1,attempt_count+1),
                  window_started_at=IF(expires_at<=NOW(6),NOW(6),window_started_at),
                  expires_at=IF(expires_at<=NOW(6),DATE_ADD(NOW(6), INTERVAL ? SECOND),expires_at)
                """, key, seconds, seconds);
        Integer attempts = jdbc.queryForObject(
                "SELECT attempt_count FROM auth_rate_limits WHERE bucket_key=?", Integer.class, key);
        if (attempts != null && attempts > maximumAttempts) throw new RateLimitExceededException();
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to apply authentication rate limiting.", exception);
        }
    }
}
