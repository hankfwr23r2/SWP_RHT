package com.example.racehorse_transport.util;

import com.example.racehorse_transport.entity.DocumentRequirement;
import java.util.Locale;

public final class DocumentRequirementPolicy {
    private DocumentRequirementPolicy() {
    }

    public static boolean applies(DocumentRequirement requirement, String bookingType, String country) {
        String tripType = "INTERNATIONAL".equalsIgnoreCase(bookingType) ? "INTERNATIONAL" : "DOMESTIC";
        boolean tripMatches = "ALL".equalsIgnoreCase(requirement.getTripType())
                || tripType.equalsIgnoreCase(requirement.getTripType());
        String normalizedCountry = normalizeCountry(country);
        boolean countryMatches = "ALL".equalsIgnoreCase(requirement.getCountry())
                || requirement.getCountry().equalsIgnoreCase(normalizedCountry);
        return tripMatches && countryMatches;
    }

    public static String normalizeCountry(String country) {
        if (country == null || country.isBlank()) {
            return "ALL";
        }
        String value = country.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "VIETNAM", "VIET NAM", "VIỆT NAM", "VN" -> "VN";
            case "LAOS", "LAO", "LÀO", "LA" -> "LA";
            case "CAMBODIA", "CAMPUCHIA", "KH" -> "KH";
            default -> value;
        };
    }

    public static boolean matchesHorseDocument(String configuredType, String backendType) {
        if (backendType == null) {
            return false;
        }
        return switch (configuredType) {
            case "passport" -> "PASSPORT".equalsIgnoreCase(backendType);
            case "vaccine" -> "VACCINATION".equalsIgnoreCase(backendType)
                    || "VACCINE".equalsIgnoreCase(backendType);
            case "lab" -> "BLOOD_TEST".equalsIgnoreCase(backendType) || "LAB".equalsIgnoreCase(backendType);
            default -> false;
        };
    }
}
