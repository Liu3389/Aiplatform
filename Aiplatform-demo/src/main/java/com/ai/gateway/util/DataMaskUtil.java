package com.ai.gateway.util;

/**
 * 敏感数据脱敏工具类
 */
public class DataMaskUtil {

    private DataMaskUtil() {}

    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) return email;
        int at = email.indexOf('@');
        if (at <= 1) return email;
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) return local.charAt(0) + "*" + domain;
        return local.substring(0, 2) + "*".repeat(Math.max(1, local.length() - 2)) + domain;
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) return phone;
        if (phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public static String maskApiKey(String key) {
        if (key == null || key.isEmpty()) return key;
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
