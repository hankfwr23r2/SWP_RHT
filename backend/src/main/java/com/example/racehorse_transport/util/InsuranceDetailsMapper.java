package com.example.racehorse_transport.util;

import com.example.racehorse_transport.dto.booking.HorseInsuranceDto;
import com.example.racehorse_transport.entity.BookingHorseInsurance;
import com.example.racehorse_transport.entity.InsuranceDocument;

import java.time.LocalDate;
import java.util.*;

/** Pure mapping of the existing HTTP DTO to relational insurance; never alters quote snapshots. */
public final class InsuranceDetailsMapper {
    private InsuranceDetailsMapper() {}

    public static HorseInsuranceDto toDto(BookingHorseInsurance insurance) {
        if (insurance == null) return null;
        List<String> documents = insurance.isDocumentsPresent() ? new ArrayList<>() : null;
        List<String> policies = insurance.isPolicyDocumentsPresent() ? new ArrayList<>() : null;
        Map<String, String> names = insurance.isDocumentNamesPresent() ? new LinkedHashMap<>() : null;
        // Do not sort the persistent collection in place: reading must not dirty managed state.
        List<InsuranceDocument> rows = new ArrayList<>(insurance.getInsuranceDocuments());
        rows.sort(Comparator.comparingInt(InsuranceDocument::getSortOrder)
                .thenComparing(InsuranceDocument::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        for (InsuranceDocument row : rows) {
            if (documents != null && InsuranceDocument.DECLARATION.equals(row.getKind())) documents.add(row.getUrl());
            if (policies != null && InsuranceDocument.POLICY.equals(row.getKind())) policies.add(row.getUrl());
            if (names != null && row.isNamed()) names.put(row.getUrl(), row.getOriginalName());
        }
        return HorseInsuranceDto.builder()
                .option(insurance.getOption()).provider(insurance.getProvider()).policyNumber(insurance.getPolicyNumber())
                .validFrom(formatDate(insurance.getValidFrom())).validUntil(formatDate(insurance.getValidUntil()))
                .quoteValidUntil(formatDate(insurance.getQuoteValidUntil())).premium(insurance.getPremium())
                .note(insurance.getNote()).reviewed(insurance.getReviewed()).arranged(insurance.getArranged())
                .documents(documents).policyDocuments(policies).documentNames(names).build();
    }

    /** Updates the managed collection in place and keeps IDs of references retained by kind and URL. */
    public static BookingHorseInsurance update(BookingHorseInsurance insurance, HorseInsuranceDto dto) {
        Objects.requireNonNull(insurance, "insurance");
        Objects.requireNonNull(dto, "dto");
        // Parse before changing the entity so malformed dates never leave partially mapped state.
        LocalDate validFrom = parseDate(dto.getValidFrom());
        LocalDate validUntil = parseDate(dto.getValidUntil());
        LocalDate quoteValidUntil = parseDate(dto.getQuoteValidUntil());
        List<DocumentValue> desired = documentValues(dto);

        insurance.setOption(dto.getOption());
        insurance.setProvider(dto.getProvider());
        insurance.setPolicyNumber(dto.getPolicyNumber());
        insurance.setValidFrom(validFrom);
        insurance.setValidUntil(validUntil);
        insurance.setQuoteValidUntil(quoteValidUntil);
        insurance.setPremium(dto.getPremium());
        insurance.setNote(dto.getNote());
        insurance.setReviewed(dto.getReviewed());
        insurance.setArranged(dto.getArranged());
        insurance.setDocumentsPresent(dto.getDocuments() != null);
        insurance.setPolicyDocumentsPresent(dto.getPolicyDocuments() != null);
        insurance.setDocumentNamesPresent(dto.getDocumentNames() != null);

        Map<DocumentKey, Deque<InsuranceDocument>> existing = new HashMap<>();
        for (InsuranceDocument row : insurance.getInsuranceDocuments()) {
            existing.computeIfAbsent(new DocumentKey(row.getKind(), row.getUrl()), ignored -> new ArrayDeque<>()).add(row);
        }
        List<InsuranceDocument> retained = new ArrayList<>();
        for (DocumentValue value : desired) {
            Deque<InsuranceDocument> matches = existing.get(new DocumentKey(value.kind(), value.url()));
            InsuranceDocument row = matches == null || matches.isEmpty() ? new InsuranceDocument() : matches.removeFirst();
            row.setInsurance(insurance);
            row.setKind(value.kind());
            row.setUrl(value.url());
            row.setOriginalName(value.name());
            row.setNamed(value.named());
            row.setSortOrder(value.position());
            retained.add(row);
        }
        insurance.getInsuranceDocuments().removeIf(row -> !retained.contains(row));
        for (InsuranceDocument row : retained) {
            if (!insurance.getInsuranceDocuments().contains(row)) insurance.getInsuranceDocuments().add(row);
        }
        return insurance;
    }

    private static List<DocumentValue> documentValues(HorseInsuranceDto dto) {
        List<DocumentValue> values = new ArrayList<>();
        Map<String, String> names = dto.getDocumentNames() == null ? Map.of() : dto.getDocumentNames();
        Set<String> referenced = new HashSet<>();
        addDocuments(values, dto.getDocuments(), InsuranceDocument.DECLARATION, names, referenced);
        addDocuments(values, dto.getPolicyDocuments(), InsuranceDocument.POLICY, names, referenced);
        int position = 0;
        for (Map.Entry<String, String> entry : names.entrySet()) {
            if (!referenced.contains(entry.getKey())) {
                values.add(new DocumentValue(InsuranceDocument.NAME_ONLY, Objects.requireNonNull(entry.getKey(), "document URL"),
                        entry.getValue(), true, position++));
            }
        }
        return values;
    }

    private static void addDocuments(List<DocumentValue> result, List<String> urls, String kind,
                                     Map<String, String> names, Set<String> referenced) {
        if (urls == null) return;
        for (int index = 0; index < urls.size(); index++) {
            String url = Objects.requireNonNull(urls.get(index), "document URL");
            referenced.add(url);
            result.add(new DocumentValue(kind, url, names.get(url), names.containsKey(url), index));
        }
    }

    private static LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private static String formatDate(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private record DocumentKey(String kind, String url) {}
    private record DocumentValue(String kind, String url, String name, boolean named, int position) {}
}
