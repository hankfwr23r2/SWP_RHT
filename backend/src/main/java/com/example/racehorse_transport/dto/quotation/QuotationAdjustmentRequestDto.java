package com.example.racehorse_transport.dto.quotation;

import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.QuotationLine;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record QuotationAdjustmentRequestDto(@NotNull @Digits(integer = 13, fraction = 0) BigDecimal adjustment,
        @NotBlank @Size(max = 500) String reason) {

    public QuotationLine toEntity(Quotation quotation) {
        QuotationLine line = new QuotationLine();
        line.setQuotation(quotation);
        line.setDescription(reason.strip());
        return line;
    }
}
