package com.couponhanjang.message.service;

import com.couponhanjang.integration.solapi.*;
import com.couponhanjang.message.repository.MessageDeliveryStore;
import com.couponhanjang.coupon.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.time.Clock;

@Service
public class MessageDispatchWorker {
    private final MessageDeliveryStore store;
    private final SolapiClient provider;
    private final SolapiProperties settings;
    private final CustomerPhoneProtector phones;
    private final CouponCodeProtector cipher;
    private final ObjectMapper json;
    private final Clock clock;

    public MessageDispatchWorker(MessageDeliveryStore store, SolapiClient provider, SolapiProperties settings,
            CustomerPhoneProtector phones, CouponCodeProtector cipher, ObjectMapper json, Clock clock) {
        this.store = store;
        this.provider = provider;
        this.settings = settings;
        this.phones = phones;
        this.cipher = cipher;
        this.json = json;
        this.clock = clock;
    }

    public boolean dispatchOnce() {
        if (!settings.isSendEnabled())
            return false;
        store.recoverExpiredLeases();
        var selected = store.claimNext();
        if (selected.isEmpty())
            return false;
        var job = selected.get();
        SolapiClient.SendCommand command;
        try {
            String recipient = phones.decrypt(job.recipient());
            var payload = json.readTree(cipher.decryptPayload(job.payload()));
            String from = payload.path("from").asText(), text = payload.path("text").asText();
            if (!settings.allows(recipient) || !from.matches("[0-9]{8,12}") || text.isBlank()) {
                store.cancel(job);
                return true;
            }
            command = new SolapiClient.SendCommand(job.id(), recipient, from, text);
        } catch (Exception error) {
            store.cancel(job);
            return true;
        }
        SolapiClient.Submission outcome;
        try {
            outcome = provider.send(command);
        } catch (RuntimeException error) {
            outcome = new SolapiClient.Submission(SolapiClient.SubmissionState.UNKNOWN, null, null, null, null);
        }
        store.submission(job, outcome);
        if (outcome.state() == SolapiClient.SubmissionState.ACCEPTED && outcome.messageId() != null
                && outcome.code() != null) {
            store.apply(new SolapiClient.ProviderResult(outcome.messageId(), outcome.groupId(), outcome.code(),
                    outcome.type(), job.id(), clock.instant()), false);
        }
        return true;
    }
}
