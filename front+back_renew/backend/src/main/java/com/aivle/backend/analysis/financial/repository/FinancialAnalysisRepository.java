package com.aivle.backend.analysis.financial.repository;
import com.aivle.backend.analysis.financial.entity.FinancialAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FinancialAnalysisRepository extends JpaRepository<FinancialAnalysis, Long> {}
