package com.aivle.backend.jaemu;

import static com.aivle.backend.analysis.financial.FinancialModels.*;

import com.aivle.backend.analysis.financial.FinancialCalculationService;
import com.aivle.backend.analysis.financial.entity.RevenueModel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class JaemuAnalysisService {
    private static final int PERIOD_MONTHS = 36;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private final JaemuMarketAiClient marketAiClient;
    private final FinancialCalculationService financialCalculationService;

    public JaemuAnalysisService(
        JaemuMarketAiClient marketAiClient,
        FinancialCalculationService financialCalculationService
    ) {
        this.marketAiClient = marketAiClient;
        this.financialCalculationService = financialCalculationService;
    }

    public JaemuPipelineResponse pipeline(JaemuPipelineRequest request) {
        String category = category(request);
        String modelType = modelType(request.businessModelType(), category);
        var aiHints = marketAiClient.analyze(request, category, modelType);
        double targetPrice = pick(request.targetPrice(),
            pick(aiHints.map(JaemuMarketAiClient.MarketAiHints::recommendedPrice).orElse(null), recommendedPrice(category)));
        double unitCogs = pick(request.unitCogs(),
            pick(aiHints.map(JaemuMarketAiClient.MarketAiHints::unitCost).orElse(null), unitCost(category, targetPrice)));
        long tam = Math.round(pick(request.marketSizeTam(),
            pick(aiHints.map(JaemuMarketAiClient.MarketAiHints::tam).orElse(null), defaultTam(category))));
        double cagr = clamp(pick(request.cagr(),
            pick(aiHints.map(JaemuMarketAiClient.MarketAiHints::cagr).orElse(null), defaultCagr(category))), 0, .8);
        double annualLabor = pick(request.annualLaborCost(), defaultLaborCost(category));
        double annualOffice = pick(request.annualOfficeCost(), defaultOfficeCost());
        double annualInfra = pick(request.annualInfraCost(), defaultInfraCost(category));
        double initialInvestment = pick(request.initialDevelopmentCost(), defaultDevelopmentCost(category))
            + pick(request.initialFacilityCost(), defaultFacilityCost())
            + pick(request.initialLicenseCost(), defaultLicenseCost(category));
        double cac = cac(request, targetPrice);
        List<Integer> targetUsers = listOrDefault(request.targetUsers(), defaultTargetUsers(tam, targetPrice));
        List<Integer> targetSales = listOrDefault(request.targetSalesQ(), defaultTargetSales(targetUsers));

        JaemuPipelineResponse.IdeaSummary idea = new JaemuPipelineResponse.IdeaSummary(
            request.productName(),
            category,
            request.targetCustomer(),
            request.problem(),
            request.valueProposition()
        );
        JaemuPipelineResponse.LegalReview legal = legalReview(category);
        JaemuPipelineResponse.ConceptInput concept = conceptInput(request, category);
        JaemuPipelineResponse.MarketJoinData marketJoinData = marketJoinData(request, category, modelType, tam, cagr, targetPrice, aiHints.orElse(null));
        JaemuPipelineResponse.MarketAnalysis market = marketAnalysis(request, category, modelType, tam, cagr, targetPrice);
        List<JaemuPipelineResponse.ConceptOption> concepts = concepts(request, category);
        JaemuPipelineResponse.ConceptOption selected = concepts.get(0);
        JaemuPipelineResponse.BusinessModelCanvas canvas = canvas(request, category, selected);
        JaemuPipelineResponse.BmAnalysis bm = new JaemuPipelineResponse.BmAnalysis(
            concepts,
            selected,
            canvas,
            bmScore(targetPrice, unitCogs),
            bmDecision(targetPrice, unitCogs),
            List.of(
                "Finance supports product and subscription models.",
                "Monthly cash flow is projected for 36 months.",
                "CAC, LTV, runway, and three break-even types are included."
            )
        );
        JaemuAnalysisRequest financialInput = new JaemuAnalysisRequest(
            request.productName(),
            category,
            modelType,
            tam,
            cagr,
            targetPrice,
            unitCogs,
            annualLabor,
            annualOffice,
            annualInfra,
            initialInvestment,
            targetSales,
            targetUsers,
            cac,
            modelType.equals("SUBSCRIPTION") ? 5.0 : 0.0,
            .10,
            modelType.equals("SUBSCRIPTION") ? initialSubscribers(targetUsers).doubleValue() : 0.0,
            modelType.equals("SUBSCRIPTION") ? monthlyNewSubscribers(targetUsers) : 0.0,
            modelType.equals("SUBSCRIPTION") ? monthlySubscriberGrowthRate(cagr, targetUsers) : monthlySalesGrowthRate(cagr, targetSales),
            0.0,
            0.0,
            null,
            0.0
        );
        return new JaemuPipelineResponse(
            idea,
            legal,
            concept,
            pipelineStates(marketJoinData, aiHints.isPresent()),
            marketJoinData,
            market,
            bm,
            financialSources(request, aiHints.isPresent()),
            financialInput,
            analyze(financialInput)
        );
    }

    public JaemuAnalysisResponse analyze(JaemuAnalysisRequest input) {
        Assumptions assumptions = assumptions(input);
        CalculationResult calculation = financialCalculationService.calculate(assumptions, PERIOD_MONTHS, scenarios());
        ScenarioResult base = calculation.scenarios().stream()
            .filter(item -> "BASE".equals(item.code()))
            .findFirst()
            .orElse(calculation.scenarios().get(0));

        return new JaemuAnalysisResponse(
            input.productName(),
            category(input),
            responseAssumptions(input, assumptions),
            yearly(base.months(), input.initialInvestment()),
            calculation.scenarios().stream().map(this::scenario).toList(),
            kpis(input, base),
            metrics(base, input.initialInvestment()),
            npvDistribution(calculation.scenarios()),
            report(base, input)
        );
    }

    private Assumptions assumptions(JaemuAnalysisRequest input) {
        RevenueModel revenueModel = revenueModel(input.businessModelType(), category(input));
        double targetPrice = effectivePrice(input);
        double unitCogs = effectiveUnitCogs(input, targetPrice);
        long initialSubscribers = initialSubscribers(input);
        long monthlyNewSubscribers = Math.round(monthlyNewSubscribers(input));
        long monthlyMarketingCost = monthlyMarketingCost(input, monthlyNewSubscribers);
        double monthlyGrowthRate = monthlyGrowthRate(input, revenueModel);
        return new Assumptions(
            revenueModel,
            revenueModel == RevenueModel.ONE_TIME ? decimal(targetPrice) : null,
            revenueModel == RevenueModel.ONE_TIME ? decimal(monthlySalesVolume(input)) : null,
            decimal(monthlyGrowthRate),
            decimal(unitCogs),
            decimal(input.paymentFeeRate()),
            decimal(input.otherVariableCostPerSubscriber()),
            monthly(input.annualLaborCost()),
            decimal(monthlyMarketingCost),
            monthly(input.annualServerCost()),
            monthly(input.annualOfficeCost()),
            decimal(input.monthlyOtherFixedCost()),
            decimal(input.initialInvestment()),
            ZERO,
            ZERO,
            ZERO,
            revenueModel == RevenueModel.SUBSCRIPTION ? decimal(targetPrice) : null,
            revenueModel == RevenueModel.SUBSCRIPTION ? decimal(initialSubscribers) : ZERO,
            revenueModel == RevenueModel.SUBSCRIPTION ? decimal(monthlyNewSubscribers) : ZERO,
            revenueModel == RevenueModel.SUBSCRIPTION ? decimal(input.monthlyChurnRate()) : ZERO
        );
    }

    private JaemuAnalysisResponse.Assumptions responseAssumptions(JaemuAnalysisRequest input, Assumptions assumptions) {
        return new JaemuAnalysisResponse.Assumptions(
            assumptions.revenueModel().name(),
            longValue(assumptions.unitPrice()),
            longValue(assumptions.monthlySalesVolume()),
            longValue(assumptions.monthlySubscriptionPrice()),
            longValue(assumptions.initialSubscribers()),
            longValue(assumptions.monthlyNewSubscribers()),
            doubleValue(assumptions.monthlyGrowthRate()),
            doubleValue(assumptions.monthlyChurnRate()),
            Math.round(input.cac()),
            longValue(assumptions.unitVariableCost()),
            longValue(sum(assumptions.monthlyLaborCost(), assumptions.monthlyMarketingCost(),
                assumptions.monthlyInfrastructureCost(), assumptions.monthlyRentCost(), assumptions.monthlyOtherFixedCost())),
            Math.round(input.initialInvestment())
        );
    }

    private List<JaemuAnalysisResponse.YearlyResult> yearly(List<MonthlyResult> months, double initialInvestment) {
        List<JaemuAnalysisResponse.YearlyResult> values = new ArrayList<>();
        for (int year = 0; year < 3; year++) {
            int start = year * 12;
            int end = Math.min(start + 12, months.size());
            List<MonthlyResult> window = months.subList(start, end);
            long revenue = sumLong(window, MonthlyResult::revenue);
            long variable = sumLong(window, MonthlyResult::variableCost);
            long fixed = sumLong(window, MonthlyResult::fixedCost);
            long profit = sumLong(window, MonthlyResult::operatingProfit);
            long projectCumulative = Math.round(window.get(window.size() - 1).cumulativeCashFlow().doubleValue());
            long subscribers = Math.round(window.get(window.size() - 1).activeSubscribers().doubleValue());
            double margin = revenue == 0 ? 0 : round2(profit * 100.0 / revenue);
            values.add(new JaemuAnalysisResponse.YearlyResult(
                year + 1,
                subscribers,
                revenue,
                variable,
                fixed,
            profit,
            projectCumulative,
            margin,
            revenue - variable,
            profit,
            profit
        ));
        }
        return values;
    }

    private JaemuAnalysisResponse.Scenario scenario(ScenarioResult scenario) {
        return new JaemuAnalysisResponse.Scenario(
            scenario.code(),
            scenario.label(),
            scenario.months().stream().map(this::month).toList(),
            longValue(scenario.totalRevenue()),
            longValue(scenario.totalVariableCost()),
            longValue(scenario.totalFixedCost()),
            longValue(scenario.totalOperatingProfit()),
            doubleValue(scenario.contributionMarginRate()),
            breakEven(scenario),
            longValue(scenario.minimumCashBalance()),
            longValue(scenario.requiredWorkingCapital()),
            scenario.months().stream()
                .map(month -> month.cumulativeCashFlow().doubleValue())
                .toList()
        );
    }

    private JaemuAnalysisResponse.MonthlyPoint month(MonthlyResult month) {
        return new JaemuAnalysisResponse.MonthlyPoint(
            month.month(),
            longValue(month.activeSubscribers()),
            longValue(month.revenue()),
            longValue(month.variableCost()),
            longValue(month.fixedCost()),
            longValue(month.operatingProfit()),
            longValue(month.cumulativeCashFlow())
        );
    }

    private JaemuAnalysisResponse.BreakEven breakEven(ScenarioResult scenario) {
        return new JaemuAnalysisResponse.BreakEven(
            longValue(scenario.breakEvenUnits()),
            longValue(scenario.breakEvenRevenue()),
            scenario.breakEvenMonth(),
            scenario.paybackMonth()
        );
    }

    private JaemuAnalysisResponse.Kpis kpis(JaemuAnalysisRequest input, ScenarioResult base) {
        RevenueModel revenueModel = revenueModel(input.businessModelType(), category(input));
        double targetPrice = effectivePrice(input);
        double unitCogs = effectiveUnitCogs(input, targetPrice);
        long arpu = Math.round(targetPrice);
        double grossMarginRate = arpu <= 0 ? 0 : Math.max(0, (targetPrice - unitCogs) / targetPrice);
        Long ltv = null;
        Double ltvToCac = null;
        if (revenueModel == RevenueModel.SUBSCRIPTION && input.monthlyChurnRate() > 0) {
            ltv = Math.round((targetPrice - unitCogs) / (input.monthlyChurnRate() / 100.0));
            ltvToCac = input.cac() <= 0 ? null : round2(ltv / input.cac());
        }
        return new JaemuAnalysisResponse.Kpis(
            Math.round(input.cac()),
            arpu,
            ltv,
            ltvToCac,
            runwayMonths(input.initialInvestment(), base.months()),
            round2(grossMarginRate * 100)
        );
    }

    private JaemuAnalysisResponse.Metrics metrics(ScenarioResult base, double initialInvestment) {
        long monthlyFixedCost = longValue(base.totalFixedCost().divide(BigDecimal.valueOf(PERIOD_MONTHS), 0, RoundingMode.HALF_UP));
        long npv = longValue(base.months().get(base.months().size() - 1).cumulativeCashFlow());
        return new JaemuAnalysisResponse.Metrics(
            base.totalOperatingProfit().signum() >= 0 ? "PROFITABLE" : "LOSS_MAKING",
            base.breakEvenMonth(),
            base.paybackMonth(),
            longValue(base.breakEvenUnits()),
            longValue(base.breakEvenRevenue()),
            monthlyFixedCost,
            Math.round(initialInvestment),
            successProbability(base),
            npv,
            base.breakEvenMonth(),
            monthlyFixedCost * 12
        );
    }

    private double successProbability(ScenarioResult base) {
        double score = 50;
        if (base.breakEvenMonth() != null) score += Math.max(0, 24 - base.breakEvenMonth()) * 1.5;
        if (base.paybackMonth() != null) score += Math.max(0, 36 - base.paybackMonth());
        if (base.totalOperatingProfit().signum() > 0) score += 15;
        return round2(Math.max(5, Math.min(95, score)));
    }

    private List<Double> npvDistribution(List<ScenarioResult> scenarios) {
        List<Double> anchors = scenarios.stream()
            .map(scenario -> scenario.months().get(scenario.months().size() - 1).cumulativeCashFlow().doubleValue())
            .sorted()
            .toList();
        if (anchors.isEmpty()) return List.of();
        double min = anchors.get(0);
        double max = anchors.get(anchors.size() - 1);
        if (min == max) {
            return List.of(min * .85, min * .9, min * .95, min, min * 1.05, min * 1.1, min * 1.15);
        }
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            values.add(min + (max - min) * i / 19.0);
        }
        return values;
    }

    private JaemuAnalysisResponse.Report report(ScenarioResult base, JaemuAnalysisRequest input) {
        RevenueModel revenueModel = revenueModel(input.businessModelType(), category(input));
        boolean profitable = base.totalOperatingProfit().signum() >= 0;
        String grade = profitable && base.paybackMonth() != null ? "STRONG"
            : profitable ? "WATCH"
            : "RISK";
        return new JaemuAnalysisResponse.Report(
            grade,
            List.of(
                "36-month " + (revenueModel == RevenueModel.SUBSCRIPTION ? "subscription" : "product") + " projection complete.",
                base.breakEvenMonth() == null
                    ? "Monthly operating break-even is not reached in the projection window."
                    : "Monthly operating break-even occurs in month " + base.breakEvenMonth() + ".",
                base.paybackMonth() == null
                    ? "Initial investment is not recovered within 36 months."
                    : "Cumulative cash flow turns positive in month " + base.paybackMonth() + "."
            ),
            List.of(
                "Validate CAC with real channel tests before committing the base scenario.",
                revenueModel == RevenueModel.SUBSCRIPTION
                    ? "Track churn weekly from beta cohorts and revise LTV immediately."
                    : "Validate repeat purchase assumptions before scaling the base scenario.",
                "Use the conservative scenario as the default funding plan."
            ),
            List.of(
                "Revenue model: " + revenueModel.name() + ".",
                "Projection horizon: 36 monthly periods.",
                revenueModel == RevenueModel.SUBSCRIPTION
                    ? "Target price is treated as monthly subscription price."
                    : "Target price is treated as product unit price.",
                "Initial investment is used for payback and runway interpretation."
            )
        );
    }

    private List<Scenario> scenarios() {
        return List.of(
            new Scenario("CONSERVATIVE", "Conservative", decimal(-20), ZERO, decimal(10), decimal(10)),
            new Scenario("BASE", "Base", ZERO, ZERO, ZERO, ZERO),
            new Scenario("OPTIMISTIC", "Optimistic", decimal(20), decimal(5), decimal(-5), decimal(-5))
        );
    }

    private Double runwayMonths(double initialInvestment, List<MonthlyResult> months) {
        if (initialInvestment <= 0 || months.isEmpty()) return null;
        double totalBurn = 0;
        int burnMonths = 0;
        for (MonthlyResult month : months) {
            if (month.operatingProfit().signum() < 0) {
                totalBurn += Math.abs(month.operatingProfit().doubleValue());
                burnMonths++;
            }
            if (month.operatingProfit().signum() >= 0) break;
        }
        if (burnMonths == 0 || totalBurn <= 0) return null;
        return round2(initialInvestment / (totalBurn / burnMonths));
    }

    private long initialSubscribers(JaemuAnalysisRequest input) {
        if (input.initialSubscribers() != null && input.initialSubscribers() > 0) {
            return Math.round(input.initialSubscribers());
        }
        return initialSubscribers(input.targetUsers());
    }

    private Double monthlyNewSubscribers(JaemuAnalysisRequest input) {
        if (input.monthlyNewSubscribers() != null && input.monthlyNewSubscribers() >= 0) {
            return input.monthlyNewSubscribers();
        }
        return monthlyNewSubscribers(input.targetUsers());
    }

    private double monthlySubscriberGrowthRate(JaemuAnalysisRequest input) {
        if (input.monthlySubscriberGrowthRate() != null && input.monthlySubscriberGrowthRate() >= 0) {
            return input.monthlySubscriberGrowthRate();
        }
        return monthlySubscriberGrowthRate(input.cagr(), input.targetUsers());
    }

    private long monthlyMarketingCost(JaemuAnalysisRequest input, long monthlyNewSubscribers) {
        if (input.monthlyMarketingCost() != null && input.monthlyMarketingCost() >= 0) {
            return Math.round(input.monthlyMarketingCost());
        }
        return Math.round(input.cac() * monthlyNewSubscribers);
    }

    private Long initialSubscribers(List<Integer> targetUsers) {
        int firstYear = targetUsers == null || targetUsers.isEmpty() ? 0 : targetUsers.get(0);
        return (long) Math.max(50, Math.round(firstYear * 0.20));
    }

    private Double monthlyNewSubscribers(List<Integer> targetUsers) {
        int firstYear = targetUsers == null || targetUsers.isEmpty() ? 0 : targetUsers.get(0);
        return (double) Math.max(10, Math.round(firstYear / 12.0));
    }

    private double monthlySubscriberGrowthRate(double cagr, List<Integer> targetUsers) {
        if (targetUsers != null && targetUsers.size() >= 2 && targetUsers.get(0) > 0 && targetUsers.get(1) > targetUsers.get(0)) {
            return round2((Math.pow(targetUsers.get(1) / (double) targetUsers.get(0), 1.0 / 12.0) - 1) * 100);
        }
        return round2((Math.pow(1 + Math.max(cagr, 0), 1.0 / 12.0) - 1) * 100);
    }

    private double monthlySalesGrowthRate(double cagr, List<Integer> targetSales) {
        if (targetSales != null && targetSales.size() >= 2 && targetSales.get(0) > 0 && targetSales.get(1) > targetSales.get(0)) {
            return round2((Math.pow(targetSales.get(1) / (double) targetSales.get(0), 1.0 / 12.0) - 1) * 100);
        }
        return round2((Math.pow(1 + Math.max(cagr, 0), 1.0 / 12.0) - 1) * 100);
    }

    private double monthlyGrowthRate(JaemuAnalysisRequest input, RevenueModel revenueModel) {
        if (input.monthlySubscriberGrowthRate() != null && input.monthlySubscriberGrowthRate() >= 0) {
            return input.monthlySubscriberGrowthRate();
        }
        return revenueModel == RevenueModel.SUBSCRIPTION
            ? monthlySubscriberGrowthRate(input.cagr(), input.targetUsers())
            : monthlySalesGrowthRate(input.cagr(), input.targetSalesQ());
    }

    private long monthlySalesVolume(JaemuAnalysisRequest input) {
        if (input.targetSalesQ() == null || input.targetSalesQ().isEmpty()) return 0;
        return Math.max(1, Math.round(input.targetSalesQ().get(0) / 12.0));
    }

    private double effectivePrice(JaemuAnalysisRequest input) {
        return input.targetPrice() > 0 ? input.targetPrice() : recommendedPrice(category(input));
    }

    private double effectiveUnitCogs(JaemuAnalysisRequest input, double targetPrice) {
        return input.unitCogs() > 0 ? input.unitCogs() : unitCost(category(input), targetPrice);
    }

    private BigDecimal monthly(double annualValue) {
        return decimal(annualValue / 12.0);
    }

    private double pick(Double value, double fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private List<Integer> listOrDefault(List<Integer> value, List<Integer> fallback) {
        return value == null || value.size() != 3 ? fallback : value;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double recommendedPrice(String category) {
        if (category.contains("AI")) return 49000;
        if (category.contains("subscription")) return 29000;
        return 39000;
    }

    private double unitCost(String category, double price) {
        if (category.contains("AI")) return Math.max(2500, price * .12);
        return Math.max(1000, price * .08);
    }

    private long defaultTam(String category) {
        if (category.contains("AI")) return 300_000_000_000L;
        if (category.contains("education")) return 120_000_000_000L;
        return 150_000_000_000L;
    }

    private double defaultCagr(String category) {
        if (category.contains("AI")) return .22;
        if (category.contains("education")) return .14;
        return .16;
    }

    private double defaultLaborCost(String category) {
        return category.contains("AI") ? 108_000_000 : 96_000_000;
    }

    private double defaultOfficeCost() {
        return 24_000_000;
    }

    private double defaultInfraCost(String category) {
        return category.contains("AI") ? 36_000_000 : 18_000_000;
    }

    private double defaultDevelopmentCost(String category) {
        return category.contains("AI") ? 120_000_000 : 90_000_000;
    }

    private double defaultFacilityCost() {
        return 20_000_000;
    }

    private double defaultLicenseCost(String category) {
        return category.contains("health") ? 20_000_000 : 10_000_000;
    }

    private double cac(JaemuPipelineRequest request, double price) {
        if (request.totalMarketingCost() != null && request.totalSalesCost() != null
            && request.newCustomers() != null && request.newCustomers() > 0) {
            return (request.totalMarketingCost() + request.totalSalesCost()) / request.newCustomers();
        }
        return Math.max(5000, price * .35);
    }

    private List<Integer> defaultTargetUsers(long tam, double price) {
        int base = Math.max(1200, (int) Math.round(tam / Math.max(price, 1) * .0003));
        return List.of(base, Math.round(base * 1.8f), Math.round(base * 2.7f));
    }

    private List<Integer> defaultTargetSales(List<Integer> targetUsers) {
        return List.of(
            Math.max(100, Math.round(targetUsers.get(0) / 12f)),
            Math.max(160, Math.round(targetUsers.get(1) / 12f)),
            Math.max(220, Math.round(targetUsers.get(2) / 12f))
        );
    }

    private JaemuPipelineResponse.MarketAnalysis marketAnalysis(JaemuPipelineRequest request, String category, String modelType, long tam, double cagr, double price) {
        return new JaemuPipelineResponse.MarketAnalysis(
            ("SUBSCRIPTION".equals(modelType) ? "Subscription" : "Product")
                + " opportunity sized from target customer pain, competitor pricing, and category benchmark.",
            tam,
            cagr,
            request.targetCustomer(),
            List.of(
                "Acquisition efficiency matters across both product and subscription models.",
                "Pricing discipline matters more than early discounting.",
                "Retention evidence is critical when the model is subscription."
            ),
            competitors(request),
            List.of(
                "Churn can erase contribution gains quickly.",
                "Paid acquisition may scale worse than the benchmark CAC.",
                "Infrastructure cost can spike when usage concentration grows."
            ),
            List.of(
                request.marketSizeTam() == null ? "TAM uses category benchmark." : "TAM uses provided value.",
                request.cagr() == null ? "Growth uses category benchmark." : "Growth uses provided value."
            )
        );
    }

    private JaemuPipelineResponse.LegalReview legalReview(String category) {
        return new JaemuPipelineResponse.LegalReview(
            "CONDITIONAL",
            List.of("Confirm subscription cancellation, refund, and privacy requirements."),
            category.contains("health")
                ? List.of("Sensitive data handling review", "Terms of service update")
                : List.of("Subscription terms review", "Privacy policy update"),
            List.of("Ship the MVP with the minimum regulated scope first.")
        );
    }

    private JaemuPipelineResponse.ConceptInput conceptInput(JaemuPipelineRequest request, String category) {
        return new JaemuPipelineResponse.ConceptInput(
            request.productName(),
            request.problem(),
            request.targetCustomer(),
            request.solution() == null || request.solution().isBlank() ? request.valueProposition() : request.solution(),
            request.valueProposition(),
            category,
            competitors(request),
            differentiationFeatures(request),
            modelType(request.businessModelType(), category)
        );
    }

    private List<String> differentiationFeatures(JaemuPipelineRequest request) {
        List<String> result = new ArrayList<>();
        if (request.valueProposition() != null && !request.valueProposition().isBlank()) result.add(request.valueProposition());
        if (request.solution() != null && !request.solution().isBlank()) result.add(request.solution());
        if (result.isEmpty()) result.add("Operational clarity");
        return result.stream().distinct().limit(3).toList();
    }

    private JaemuPipelineResponse.MarketJoinData marketJoinData(
        JaemuPipelineRequest request,
        String category,
        String modelType,
        long tam,
        double cagr,
        double price,
        JaemuMarketAiClient.MarketAiHints aiHints
    ) {
        List<JaemuPipelineResponse.MarketMetric> supplyMetrics = List.of(
            metric("Target reachable users", Math.max(10000, tam / Math.max(1, Math.round(price))), "users", "current", "benchmark", ""),
            metric("Category growth", cagr * 100, "%", "annual", aiHints == null ? "benchmark" : "benchmark+live", ""),
            metric("Recommended " + ("SUBSCRIPTION".equals(modelType) ? "monthly subscription price" : "unit price"), Math.round(price), "KRW", "current", aiHints == null ? "benchmark" : "benchmark+live", "")
        );
        List<JaemuPipelineResponse.MarketMetric> sizeMetrics = List.of(
            metric("Current market value", tam, "KRW", "current", "benchmark", ""),
            metric("Year 1 market value", Math.round(tam * (1 + cagr)), "KRW", "year1", "calculated", ""),
            metric("Year 2 market value", Math.round(tam * Math.pow(1 + cagr, 2)), "KRW", "year2", "calculated", "")
        );
        List<JaemuPipelineResponse.CompetitorProduct> products = competitorProducts(request, price, aiHints);
        List<Long> prices = products.stream().filter(p -> p.price() != null && p.price() > 0)
            .map(JaemuPipelineResponse.CompetitorProduct::price).sorted().toList();
        JaemuPipelineResponse.PriceSummary priceSummary = prices.isEmpty()
            ? new JaemuPipelineResponse.PriceSummary(0, 0, 0, 0, "KRW")
            : new JaemuPipelineResponse.PriceSummary(prices.size(), prices.get(0), prices.get(prices.size() / 2), prices.get(prices.size() - 1), "KRW");
        List<JaemuPipelineResponse.DifferentiationRow> diffRows = differentiationRows(request, products);
        List<String> candidates = diffRows.stream().filter(row -> "GAP".equals(row.verdict()))
            .map(JaemuPipelineResponse.DifferentiationRow::conceptFeature).toList();
        return new JaemuPipelineResponse.MarketJoinData(
            new JaemuPipelineResponse.MarketSupplyDemand(
                supplyMetrics,
                aiHints == null ? List.of("Live market search was unavailable; category benchmarks were used.")
                    : mergeWarnings(List.of("Live market search contributed to the market handoff."), aiHints.warnings())
            ),
            new JaemuPipelineResponse.MarketSizeGrowth(
                sizeMetrics,
                List.of(new JaemuPipelineResponse.GrowthCalculation("CAGR", cagr * 100, "%", "((next/current)^(1/n)-1) * 100"))
            ),
            new JaemuPipelineResponse.CompetitorPrice(
                products,
                priceSummary,
                List.of("The price summary uses only non-zero observable or estimated prices.")
            ),
            new JaemuPipelineResponse.Differentiation(diffRows, candidates),
            new JaemuPipelineResponse.MarketFinalSummary(
                Math.round(tam / 100_000_000.0) + "억 원",
                cagr,
                request.targetCustomer(),
                products.size(),
                prices.isEmpty() ? "price review needed" : prices.get(0) + "~" + prices.get(prices.size() - 1) + " KRW",
                candidates,
                "Market and BM have been reduced to " + ("SUBSCRIPTION".equals(modelType) ? "subscription" : "product") + " finance assumptions."
            )
        );
    }

    private JaemuPipelineResponse.MarketMetric metric(String name, double value, String unit, String period, String source, String url) {
        return new JaemuPipelineResponse.MarketMetric(name, value, unit, period, source, url);
    }

    private List<JaemuPipelineResponse.CompetitorProduct> competitorProducts(
        JaemuPipelineRequest request,
        double price,
        JaemuMarketAiClient.MarketAiHints aiHints
    ) {
        if (aiHints != null && aiHints.competitorProducts() != null && !aiHints.competitorProducts().isEmpty()) {
            List<JaemuPipelineResponse.CompetitorProduct> aiProducts = new ArrayList<>();
            for (JaemuMarketAiClient.AiProduct product : aiHints.competitorProducts()) {
                if (product.company() == null || product.company().isBlank()) continue;
                aiProducts.add(new JaemuPipelineResponse.CompetitorProduct(
                    product.company(),
                    product.model() == null || product.model().isBlank() ? product.company() + " offer" : product.model(),
                    product.price(),
                    product.price() == null ? "manual review needed" : "market observed",
                    product.features() == null ? List.of() : product.features(),
                    product.sourceUrl(),
                    product.price() == null ? "FEATURE_ONLY" : "PRICE_VERIFIED"
                ));
            }
            if (!aiProducts.isEmpty()) return aiProducts;
        }
        List<String> competitors = competitors(request);
        List<JaemuPipelineResponse.CompetitorProduct> result = new ArrayList<>();
        for (int i = 0; i < competitors.size(); i++) {
            String company = competitors.get(i);
            long competitorPrice = Math.max(1000, Math.round(price * (0.8 + i * 0.15)));
            result.add(new JaemuPipelineResponse.CompetitorProduct(
                company,
                company + " plan",
                competitorPrice,
                "estimated",
                i % 2 == 0 ? List.of("automation", "retention hooks") : List.of("analytics", "bundled support"),
                "",
                "PRICE_ESTIMATED"
            ));
        }
        return result;
    }

    private List<String> mergeWarnings(List<String> base, List<String> extra) {
        List<String> values = new ArrayList<>(base);
        if (extra != null) values.addAll(extra);
        return values;
    }

    private List<JaemuPipelineResponse.DifferentiationRow> differentiationRows(
        JaemuPipelineRequest request,
        List<JaemuPipelineResponse.CompetitorProduct> products
    ) {
        List<JaemuPipelineResponse.DifferentiationRow> rows = new ArrayList<>();
        for (String feature : differentiationFeatures(request)) {
            int supported = 0;
            for (JaemuPipelineResponse.CompetitorProduct product : products) {
                if (product.features().stream().anyMatch(item -> item.contains(feature) || feature.contains(item))) {
                    supported++;
                }
            }
            int compared = Math.max(1, products.size());
            double rate = round2(supported * 100.0 / compared);
            rows.add(new JaemuPipelineResponse.DifferentiationRow(
                feature,
                supported,
                compared,
                rate,
                rate >= 60 ? "PARITY" : "GAP"
            ));
        }
        return rows;
    }

    private List<JaemuPipelineResponse.PipelineState> pipelineStates(JaemuPipelineResponse.MarketJoinData marketJoinData, boolean liveSearchUsed) {
        return List.of(
            state("input", "Input normalized", "DONE", "concept", "Core concept input was normalized.", List.of("Required fields present")),
            state("collection", "Market collection", liveSearchUsed ? "LIVE" : "PARTIAL", "market", "Market data prepared for BM and finance.", marketJoinData.marketSupplyDemand().warnings()),
            state("bm", "Business model", "DONE", "bm", "Product or subscription model selected.", List.of("Revenue stream fixed before finance")),
            state("finance", "Financial model", "DONE", "finance", "36-month finance projection generated.", List.of("Three scenarios", "CAC/LTV/Runway", "Three break-even types"))
        );
    }

    private JaemuPipelineResponse.PipelineState state(String id, String label, String status, String owner, String output, List<String> checks) {
        return new JaemuPipelineResponse.PipelineState(id, label, status, owner, output, checks);
    }

    private List<String> competitors(JaemuPipelineRequest request) {
        if (request.competitors() != null && !request.competitors().isBlank()) {
            return List.of(request.competitors().split("\\s*,\\s*"));
        }
        return List.of("Existing SaaS competitor", "Adjacent workflow tool", "Manual alternative");
    }

    private List<JaemuPipelineResponse.ConceptOption> concepts(JaemuPipelineRequest request, String category) {
        String product = request.productName();
        return List.of(
            new JaemuPipelineResponse.ConceptOption("concept-a", product + " Core", request.problem(), "Direct revenue", 86),
            new JaemuPipelineResponse.ConceptOption("concept-b", product + " Team", category + " workflow bundle", "Recurring plan", 81),
            new JaemuPipelineResponse.ConceptOption("concept-c", product + " Pro", "Retention-led premium expansion", "Upsell expansion", 77)
        );
    }

    private JaemuPipelineResponse.BusinessModelCanvas canvas(
        JaemuPipelineRequest request,
        String category,
        JaemuPipelineResponse.ConceptOption selected
    ) {
        return new JaemuPipelineResponse.BusinessModelCanvas(
            List.of("Cloud vendor", "Performance marketing channels", "Support tooling"),
            List.of("Acquire subscribers", "Retain cohorts", "Iterate activation"),
            List.of("Product team", "Usage data", "Support operations"),
            List.of(request.valueProposition(), "Faster outcome for " + request.targetCustomer()),
            List.of("Self-serve onboarding", "Lifecycle messaging"),
            List.of("SEO", "Paid acquisition", "Referral"),
            List.of(request.targetCustomer()),
            List.of("Labor", "Marketing", "Infrastructure", "Support"),
            List.of(selected.revenueModel())
        );
    }

    private int bmScore(double targetPrice, double unitCogs) {
        double margin = targetPrice <= 0 ? 0 : (targetPrice - unitCogs) / targetPrice;
        return (int) Math.max(45, Math.min(92, Math.round(60 + margin * 35)));
    }

    private String bmDecision(double targetPrice, double unitCogs) {
        return targetPrice > unitCogs ? "Proceed with model validation." : "Rework pricing or delivery cost first.";
    }

    private List<JaemuPipelineResponse.FinancialInputSource> financialSources(JaemuPipelineRequest request, boolean aiUsed) {
        return List.of(
            new JaemuPipelineResponse.FinancialInputSource("targetPrice", "Unit or subscription price", request.targetPrice() == null ? "default" : "user", "Interpreted by the selected revenue model."),
            new JaemuPipelineResponse.FinancialInputSource("unitCogs", "Variable cost", request.unitCogs() == null ? "default" : "user", "Mapped to per-unit or per-subscriber cost."),
            new JaemuPipelineResponse.FinancialInputSource("targetUsers", "Demand targets", request.targetUsers() == null ? "benchmark" : "user", "Used for subscription demand shaping and CAC fallback."),
            new JaemuPipelineResponse.FinancialInputSource("market", "Market benchmark", aiUsed ? "live+benchmark" : "benchmark", "Supports TAM, CAGR, and pricing fallbacks.")
        );
    }

    private String category(JaemuPipelineRequest request) {
        boolean productModel = "PRODUCT".equals(modelType(request.businessModelType(), ""));
        String text = (request.productName() + " " + request.solution() + " " + request.industryHint()).toLowerCase(Locale.ROOT);
        String suffix = productModel ? " product" : " subscription";
        if (containsAny(text, "ai", "assistant", "automation")) return "AI" + suffix;
        if (containsAny(text, "education", "learning", "course")) return "education" + suffix;
        if (containsAny(text, "health", "wellness", "care")) return "health" + suffix;
        return productModel ? "product business" : "subscription service";
    }

    private String category(JaemuAnalysisRequest request) {
        if (request.category() != null && !request.category().isBlank()) return request.category();
        String text = (request.productName() + " " + request.businessModelType()).toLowerCase(Locale.ROOT);
        if (containsAny(text, "ai", "assistant", "automation")) return "AI subscription";
        if (containsAny(text, "education", "learning", "course")) return "education subscription";
        if (containsAny(text, "health", "wellness", "care")) return "health subscription";
        return request.businessModelType() != null && request.businessModelType().toLowerCase(Locale.ROOT).contains("product")
            ? "product business"
            : "subscription service";
    }

    private String modelType(String value, String category) {
        if (value == null || value.isBlank()) {
            return category.contains("subscription") ? "SUBSCRIPTION" : "PRODUCT";
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.contains("sub") || normalized.contains("saas") || normalized.contains("구독") || normalized.contains("service")) {
            return "SUBSCRIPTION";
        }
        return "PRODUCT";
    }

    private RevenueModel revenueModel(String value, String category) {
        return "SUBSCRIPTION".equals(modelType(value, category)) ? RevenueModel.SUBSCRIPTION : RevenueModel.ONE_TIME;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) if (text.contains(keyword)) return true;
        return false;
    }

    private long sumLong(List<MonthlyResult> months, java.util.function.Function<MonthlyResult, BigDecimal> getter) {
        return months.stream().map(getter).reduce(ZERO, BigDecimal::add).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private BigDecimal sum(BigDecimal... values) {
        BigDecimal total = ZERO;
        for (BigDecimal value : values) total = total.add(value == null ? ZERO : value);
        return total;
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(Double value) {
        return value == null ? ZERO : decimal(value.doubleValue());
    }

    private long longValue(BigDecimal value) {
        return value == null ? 0 : value.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private double doubleValue(BigDecimal value) {
        return value == null ? 0 : value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
