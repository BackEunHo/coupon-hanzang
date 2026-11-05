package com.couponhanjang.message.service;

import com.couponhanjang.integration.solapi.*;
import com.couponhanjang.message.repository.MessageDeliveryStore;
import org.springframework.stereotype.Service;

@Service
public class DeliveryResultProcessor {
    private final MessageDeliveryStore store;
    private final SolapiClient provider;
    private final SolapiProperties settings;

    public DeliveryResultProcessor(MessageDeliveryStore store, SolapiClient provider, SolapiProperties settings) {
        this.store = store;
        this.provider = provider;
        this.settings = settings;
    }

    public void processPending() {
        for (var event : store.pendingEvents()) {
            var result = new SolapiClient.ProviderResult(event.messageId(), null, event.code(), event.type(),
                    event.attemptId(), event.reportedAt());
            boolean applied = store.apply(result, false);
            if (!applied && event.attemptId() != null && !settings.getApiKey().isBlank()) {
                var verified = provider.lookup(event.messageId());
                if (verified.isPresent() && event.attemptId().equals(verified.get().attemptId()))
                    applied = store.apply(verified.get(), true);
            }
            store.finishEvent(event.id(), applied);
        }
    }

    public void reconcile() {
        if (settings.getApiKey().isBlank())
            return;
        for (var target : store.unidentifiedTargets()) {
            var result = provider.findAttempt(target.id(), target.requestedAt());
            result.filter(value -> target.id().equals(value.attemptId())).ifPresent(value -> store.apply(value, true));
            store.polled(target.id());
        }
        for (var target : store.pollTargets()) {
            var result = provider.lookup(target.messageId());
            result.ifPresent(value -> store.apply(value, true));
            store.polled(target.id());
        }
    }
}
