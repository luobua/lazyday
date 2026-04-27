package com.fan.lazyday.domain.appkey.entity;

import com.fan.lazyday.domain.appkey.po.AppKey;
import com.fan.lazyday.domain.appkey.repository.AppKeyRepository;
import com.fan.lazyday.infrastructure.context.SpringContext;
import com.fan.lazyday.infrastructure.domain.Entity;
import com.fan.lazyday.infrastructure.utils.encrypt.AES;
import com.fan.lazyday.infrastructure.utils.encrypt.Base64;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.util.Lazy;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Getter
public class AppKeyEntity extends Entity<Long> {

    public static final Class<AppKey> PO_CLASS = AppKey.class;

    public static final Lazy<Context> CTX = SpringContext.getLazyBean(Context.class);

    private static final String AK_PREFIX = "ak_";
    private static final String SK_PREFIX = "sk_";
    private static final long GRACE_PERIOD_HOURS = 24;

    protected AppKeyEntity() {
    }

    @Setter(AccessLevel.PROTECTED)
    private AppKey delegate;

    public Long getId() {
        if (delegate != null) {
            return delegate.getId();
        }
        return null;
    }

    public static AppKeyEntity fromPo(AppKey po) {
        AppKeyEntity entity = new AppKeyEntity();
        entity.setDelegate(po);
        return entity;
    }

    public static String generateAppKey() {
        return AK_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    public static String generateSecretKey() {
        return SK_PREFIX + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static String encryptSecretKey(String plainSecretKey, String encryptionKey) {
        byte[] key = encryptionKey.getBytes(StandardCharsets.UTF_8);
        byte[] padded = padKey(key);
        byte[] encrypted = AES.encrypt(plainSecretKey.getBytes(StandardCharsets.UTF_8), padded);
        return Base64.encode(encrypted);
    }

    public static String decryptSecretKey(String encryptedSecretKey, String encryptionKey) {
        byte[] key = encryptionKey.getBytes(StandardCharsets.UTF_8);
        byte[] padded = padKey(key);
        byte[] decoded = Base64.decode(encryptedSecretKey);
        byte[] decrypted = AES.decrypt(decoded, padded);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    public boolean isInGracePeriod() {
        if (delegate.getGracePeriodEnd() == null) {
            return false;
        }
        return Instant.now().isBefore(delegate.getGracePeriodEnd());
    }

    public boolean verifySecretKey(String plainSecretKey, String encryptionKey) {
        String currentDecrypted = decryptSecretKey(delegate.getSecretKeyEncrypted(), encryptionKey);
        if (currentDecrypted.equals(plainSecretKey)) {
            return true;
        }
        if (isInGracePeriod() && delegate.getSecretKeyOld() != null) {
            String oldDecrypted = decryptSecretKey(delegate.getSecretKeyOld(), encryptionKey);
            return oldDecrypted.equals(plainSecretKey);
        }
        return false;
    }

    public String rotateSecretKey(String encryptionKey) {
        String newSecretKey = generateSecretKey();
        delegate.setSecretKeyOld(delegate.getSecretKeyEncrypted());
        delegate.setSecretKeyEncrypted(encryptSecretKey(newSecretKey, encryptionKey));
        delegate.setRotatedAt(Instant.now());
        delegate.setGracePeriodEnd(Instant.now().plus(GRACE_PERIOD_HOURS, ChronoUnit.HOURS));
        return newSecretKey;
    }

    private static byte[] padKey(byte[] key) {
        byte[] padded = new byte[16];
        System.arraycopy(key, 0, padded, 0, Math.min(key.length, 16));
        return padded;
    }

    @Getter
    @Component
    @AllArgsConstructor
    public static class Context {
        private final AppKeyRepository repository;
        private final R2dbcEntityTemplate entityTemplate;
    }
}