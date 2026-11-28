package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.CountryRule;
import com.example.racehorse_transport.entity.enums.RuleDirection;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryRuleRepository extends JpaRepository<CountryRule, Long> {

    @Override
    @EntityGraph(attributePaths = "country")
    List<CountryRule> findAll(Sort sort);

    @Override
    @EntityGraph(attributePaths = "country")
    Optional<CountryRule> findById(Long id);

    @EntityGraph(attributePaths = "country")
    List<CountryRule> findByCountry_CodeAndDirection(String countryCode, RuleDirection direction);
}
