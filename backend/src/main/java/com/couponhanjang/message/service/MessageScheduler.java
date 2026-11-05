package com.couponhanjang.message.service;

import com.couponhanjang.integration.solapi.SolapiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(name = "solapi.background-enabled", havingValue = "true", matchIfMissing = true)
public class MessageScheduler {
    private static final Logger log = LoggerFactory.getLogger(MessageScheduler.class);
    private final MessageDispatchWorker worker;
    private final DeliveryResultProcessor results;
    private final SolapiProperties settings;

    public MessageScheduler(MessageDispatchWorker worker, DeliveryResultProcessor results, SolapiProperties settings) {
        this.worker = worker;
        this.results = results;
        this.settings = settings;
    }

    @Scheduled(fixedDelayString = "${solapi.dispatch-delay-ms:10000}", initialDelayString = "${solapi.dispatch-delay-ms:10000}")
    public void run() {
        try {
            for (int i = 0; i < settings.getMaxSendsPerRun() && worker.dispatchOnce(); i++) {
            }
            results.processPending();
            results.reconcile();
        } catch (RuntimeException error) {
            // 업체 예외·응답에는 민감 정보가 섞일 수 있으므로 원문과 예외 메시지를 기록하지 않는다.
            log.warn("Message background job failed; persisted state will be retried safely");
        }
    }
}
