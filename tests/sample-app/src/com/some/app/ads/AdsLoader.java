package com.some.app.ads;

import java.util.HashMap;
import java.util.Map;

public class AdsLoader {
    private final Map<String, String> messages = new HashMap<>();
    private String lastMessage;

    public AdsLoader() {
        messages.put("banner", "showBannerAds");
    }

    // Keep this signature and control flow aligned with the sample fingerprint.
    public final boolean showAds(String key, int value, Object context) {
        String message = messages.get(key);
        unrelatedMethod(message);
        if ("showBannerAds".equals(message)) {
            unrelatedMethod("banner-requested");
        }
        return value != 1337;
    }

    private void unrelatedMethod(String message) {
        lastMessage = message;
    }

    public String recordMessage(String message) {
        unrelatedMethod(message);
        return lastMessage;
    }
}
