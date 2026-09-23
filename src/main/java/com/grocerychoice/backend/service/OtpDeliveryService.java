package com.grocerychoice.backend.service;

import com.grocerychoice.backend.entity.OtpPurpose;

/**
 * Clean abstraction for OTP dispatch across various delivery channels
 * (SMS providers like Twilio/MSG91, Email providers like SendGrid/JavaMail, and Dev logger).
 */
public interface OtpDeliveryService {

    /**
     * Dispatches an OTP to the given customer identifier (mobile phone or email).
     */
    void deliverOtp(String identifier, String otp, OtpPurpose purpose);

    /**
     * Retrieves the latest generated OTP for a given identifier in development mode.
     * Returns null if not in development mode or not available.
     */
    String getDevOtp(String identifier);
}
