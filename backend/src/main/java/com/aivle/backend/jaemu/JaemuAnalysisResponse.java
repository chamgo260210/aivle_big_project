package com.aivle.backend.jaemu;

import java.util.List;

public record JaemuAnalysisResponse(
    String productName,
    String category,
    Assumptions assumptions,
    List<YearlyResult> yearly,
    List<Scenario> scenarios,
    Kpis kpis,
    Metrics metrics,
    List<Double> npvDistribution,
    Report report
) {
    public record Assumptions(
        String revenueModel,
        long unitPrice,
        long monthlySalesVolume,
        long monthlySubscriptionPrice,
        long initialSubscribers,
        long monthlyNewSubscribers,
        double monthlySubscriberGrowthRate,
        double monthlyChurnRate,
        long subscriberAcquisitionCost,
        long unitVariableCost,
        long monthlyFixedCost,
        long initialInvestment
    ) { }

    public record YearlyResult(
        int year,
        long endingSubscribers,
        long revenue,
        long variableCost,
        long fixedCost,
        long operatingProfit,
        long projectCumulativeCashFlow,
        double operatingMargin,
        long grossProfit,
        long operatingIncome,
        long netIncome
    ) { }

    public record Scenario(
        String code,
        String name,
        List<MonthlyPoint> months,
        long totalRevenue,
        long totalVariableCost,
        long totalFixedCost,
        long totalOperatingProfit,
        double contributionMarginRate,
        BreakEven breakEven,
        long minimumProjectCashFlow,
        long requiredWorkingCapital,
        List<Double> monthlyCash
    ) { }

    public record MonthlyPoint(
        int month,
        long activeSubscribers,
        long revenue,
        long variableCost,
        long fixedCost,
        long operatingProfit,
        long projectCumulativeCashFlow
    ) { }

    public record BreakEven(
        long subscribers,
        long revenue,
        Integer monthlyOperatingMonth,
        Integer cumulativeCashFlowMonth
    ) { }

    public record Kpis(
        long cac,
        long arpu,
        Long ltv,
        Double ltvToCac,
        Double runwayMonths,
        double grossMarginRate
    ) { }

    public record Metrics(
        String profitabilityStatus,
        Integer monthlyOperatingBreakEvenMonth,
        Integer cumulativeCashFlowBreakEvenMonth,
        long breakEvenSubscribers,
        long breakEvenRevenue,
        long monthlyFixedCost,
        long initialInvestment,
        double successProbability,
        long npv,
        Integer breakEvenMonth,
        long fixedAnnualCost
    ) { }

    public record Report(
        String grade,
        List<String> highlights,
        List<String> actions,
        List<String> assumptions
    ) { }
}
