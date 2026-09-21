package com.synchat.server.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;

/**
 * Passwords are never stored in clear text. Each user gets a random 16 byte
 * salt; the database keeps salt + SHA-256(salt || password), both hex encoded.
 */
public final class PasswordUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    public static String newSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return toHex(salt);
    }

    public static String hash(String password, String saltHex) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(fromHex(saltHex));
            md.update(password.getBytes(StandardCharsets.UTF_8));
            return toHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Constant-time comparison, so a wrong password cannot be timed out. */
    public static boolean matches(String password, String saltHex, String expectedHash) {
        return MessageDigest.isEqual(
                hash(password, saltHex).getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static byte[] fromHex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
