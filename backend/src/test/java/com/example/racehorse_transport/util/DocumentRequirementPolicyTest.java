package com.example.racehorse_transport.util;

import com.example.racehorse_transport.entity.DocumentRequirement;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentRequirementPolicyTest {
    @Test
    void appliesOnlyToConfiguredTripAndNormalizedCountry() {
        DocumentRequirement requirement = new DocumentRequirement();
        requirement.setTripType("INTERNATIONAL");
        requirement.setCountry("KH");

        assertThat(DocumentRequirementPolicy.applies(requirement, "international", "Cambodia")).isTrue();
        assertThat(DocumentRequirementPolicy.applies(requirement, "domestic", "Cambodia")).isFalse();
        assertThat(DocumentRequirementPolicy.applies(requirement, "international", "Laos")).isFalse();
    }

    @Test
    void matchesApprovedHorseDocumentAliases() {
        assertThat(DocumentRequirementPolicy.matchesHorseDocument("vaccine", "VACCINATION")).isTrue();
        assertThat(DocumentRequirementPolicy.matchesHorseDocument("lab", "BLOOD_TEST")).isTrue();
        assertThat(DocumentRequirementPolicy.matchesHorseDocument("passport", "PENDING")).isFalse();
    }
}
