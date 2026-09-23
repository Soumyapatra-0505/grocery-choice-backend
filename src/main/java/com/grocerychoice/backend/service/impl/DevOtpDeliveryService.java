package com.grocerychoice.backend.service.impl;

import com.grocerychoice.backend.entity.OtpPurpose;
import com.grocerychoice.backend.service.OtpDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Development & testing implementation of OtpDeliveryService.
 * Logs generated OTPs locally to backend logs and enables safe inspection in development mode.
 * Architecture is prepared for plugging in Twilio/MSG91 and SendGrid without changing service consumers.
 */
@Service
public class DevOtpDeliveryService implements OtpDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DevOtpDeliveryService.class);

    private final boolean devMode;
    private final Map<String, String> devOtpCache = new ConcurrentHashMap<>();

    public DevOtpDeliveryService(@Value("${app.dev-mode:true}") boolean devMode) {
        this.devMode = devMode;
    }

    @Override
    public void deliverOtp(String identifier, String otp, OtpPurpose purpose) {
        if (devMode) {
            devOtpCache.put(identifier.trim().toLowerCase(), otp);
            // Also store stripped digits version if phone
            String digits = identifier.replaceAll("\\D", "");
            if (!digits.isEmpty()) {
                devOtpCache.put(digits, otp);
                if (digits.length() == 12 && digits.startsWith("91")) {
                    devOtpCache.put(digits.substring(2), otp);
                } else if (digits.length() == 10) {
                    devOtpCache.put("91" + digits, otp);
                }
            }

            log.info("================================================================================");
            log.info("[DEV OTP DELIVERY] Generated OTP for '{}' (purpose: {}): [{}]", identifier, purpose, otp);
            log.info("================================================================================");
        } else {
            log.info("[OTP DISPATCH] Dispatched OTP verification code to recipient '{}'", maskIdentifier(identifier));
        }
    }

    @Override
    public String getDevOtp(String identifier) {
        if (!devMode || identifier == null) {
            return null;
        }
        String key = identifier.trim().toLowerCase();
        String otp = devOtpCache.get(key);
        if (otp == null) {
            String digits = identifier.replaceAll("\\D", "");
            otp = devOtpCache.get(digits);
            if (otp == null && digits.length() == 12 && digits.startsWith("91")) {
                otp = devOtpCache.get(digits.substring(2));
            } else if (otp == null && digits.length() == 10) {
                otp = devOtpCache.get("91" + digits);
            }
        }
        return otp;
    }

    private String maskIdentifier(String identifier) {
        if (identifier == null || identifier.length() < 4) return "****";
        if (identifier.contains("@")) {
            int atIndex = identifier.indexOf('@');
            return identifier.substring(0, Math.min(2, atIndex)) + "***" + identifier.substring(atIndex);
        }
        return identifier.substring(0, 2) + "******" + identifier.substring(identifier.length() - 2);
    }
}
