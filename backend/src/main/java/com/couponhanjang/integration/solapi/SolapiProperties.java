package com.couponhanjang.integration.solapi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.InitializingBean;
import java.util.Set;

@ConfigurationProperties("solapi")
public class SolapiProperties implements InitializingBean {
    private boolean sendEnabled;
    private String apiKey = "", apiSecret = "", fromNumber = "", webhookSecret = "";
    private Set<String> allowedRecipients = Set.of();
    private boolean allowAllRecipients;
    private boolean countDeliveryFailures = true;
    private int maxSendsPerRun = 1;

    @Override
    public void afterPropertiesSet() {
        if (sendEnabled && (apiKey.isBlank() || apiSecret.isBlank() || webhookSecret.isBlank()
                || !fromNumber.matches("[0-9]{8,12}") || (!allowAllRecipients && allowedRecipients.isEmpty()))) {
            throw new IllegalArgumentException(
                    "SOLAPI sender, credentials, webhook secret and recipient policy are required");
        }
        if (maxSendsPerRun < 1 || maxSendsPerRun > 10)
            throw new IllegalArgumentException("Invalid send batch size");
    }

    public boolean allows(String phone) {
        return allowAllRecipients || allowedRecipients.contains(phone);
    }

    public boolean isSendEnabled() {
        return sendEnabled;
    }

    public void setSendEnabled(boolean v) {
        sendEnabled = v;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String v) {
        apiKey = v;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String v) {
        apiSecret = v;
    }

    public String getFromNumber() {
        return fromNumber;
    }

    public void setFromNumber(String v) {
        fromNumber = v;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String v) {
        webhookSecret = v;
    }

    public Set<String> getAllowedRecipients() {
        return allowedRecipients;
    }

    public void setAllowedRecipients(Set<String> v) {
        allowedRecipients = Set.copyOf(v);
    }

    public boolean isAllowAllRecipients() {
        return allowAllRecipients;
    }

    public void setAllowAllRecipients(boolean v) {
        allowAllRecipients = v;
    }

    public boolean isCountDeliveryFailures() {
        return countDeliveryFailures;
    }

    public void setCountDeliveryFailures(boolean v) {
        countDeliveryFailures = v;
    }

    public int getMaxSendsPerRun() {
        return maxSendsPerRun;
    }

    public void setMaxSendsPerRun(int v) {
        maxSendsPerRun = v;
    }

    @Override
    public String toString() {
        return "SolapiProperties[REDACTED]";
    }
}
