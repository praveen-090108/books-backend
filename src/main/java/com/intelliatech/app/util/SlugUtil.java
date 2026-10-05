package com.intelliatech.app.util;

public final class SlugUtil {

    private SlugUtil() {
    }

    public static String toSlug(String value) {
        return value == null ? "" : value.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
