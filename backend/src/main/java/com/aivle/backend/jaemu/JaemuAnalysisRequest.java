package com.aivle.backend.jaemu;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record JaemuAnalysisRequest(
    @NotBlank String productName,
    String category,
    String businessModelType,
    @PositiveOrZero double marketSizeTam,
    @DecimalMin("0.0") @DecimalMax("3.0") double cagr,
    @PositiveOrZero double targetPrice,
    @PositiveOrZero double unitCogs,
    @PositiveOrZero double annualLaborCost,
    @PositiveOrZero double annualOfficeCost,
    @PositiveOrZero double annualServerCost,
    @PositiveOrZero double initialInvestment,
    @Size(min = 3, max = 3) List<@PositiveOrZero Integer> targetSalesQ,
    @Size(min = 3, max = 3) List<@PositiveOrZero Integer> targetUsers,
    @PositiveOrZero double cac,
    @DecimalMin("0.0") @DecimalMax("100.0") double monthlyChurnRate,
    @DecimalMin("0.0") @DecimalMax("1.0") double discountRate,
    @PositiveOrZero Double initialSubscribers,
    @PositiveOrZero Double monthlyNewSubscribers,
    @DecimalMin("0.0") @DecimalMax("100.0") Double monthlySubscriberGrowthRate,
    @PositiveOrZero Double paymentFeeRate,
    @PositiveOrZero Double otherVariableCostPerSubscriber,
    @PositiveOrZero Double monthlyMarketingCost,
    @PositiveOrZero Double monthlyOtherFixedCost
) { }
