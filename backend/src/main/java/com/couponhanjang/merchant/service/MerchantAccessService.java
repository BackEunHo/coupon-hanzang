package com.couponhanjang.merchant.service;

import com.couponhanjang.common.exception.ApiException;
import com.couponhanjang.merchant.repository.OperatorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class MerchantAccessService {
    private final OperatorRepository operators;

    public MerchantAccessService(OperatorRepository operators) {
        this.operators = operators;
    }

    @Transactional(readOnly = true)
    public UUID requireActiveMerchantId(UUID operatorId) {
        return operators.findById(operatorId).filter(o -> o.isActive()).map(o -> o.getMerchant().getId())
                .orElseThrow(() -> new ApiException(401, "UNAUTHORIZED", "Authentication required"));
    }
}
