package com.mindhub.homebanking.service.notification;

/**
 * A readable device from a User-Agent header ("Chrome en Windows"), for sign-in alerts. Deliberately
 * coarse: it only helps the client recognise their own sign-ins, and the raw header is never stored.
 */
public final class DeviceLabel {

    private DeviceLabel() {
    }

    public static String of(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "un dispositivo desconocido";
        }
        String browser = browser(userAgent);
        String system = system(userAgent);
        if (browser == null) {
            return system == null ? "otra aplicación" : "una aplicación en " + system;
        }
        return system == null ? browser : browser + " en " + system;
    }

    private static String browser(String ua) {
        if (ua.contains("Edg/")) return "Edge";
        if (ua.contains("OPR/") || ua.contains("Opera")) return "Opera";
        if (ua.contains("Firefox/")) return "Firefox";
        if (ua.contains("Chrome/") || ua.contains("CriOS/")) return "Chrome";
        if (ua.contains("Safari/")) return "Safari";
        return null;
    }

    private static String system(String ua) {
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) return "macOS";
        if (ua.contains("Linux")) return "Linux";
        return null;
    }
}
