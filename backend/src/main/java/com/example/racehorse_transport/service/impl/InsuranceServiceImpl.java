package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.InsuranceDeclarationRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.HorseInsurance;
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.PartyRole;
import com.example.racehorse_transport.entity.PartyRoleId;
import com.example.racehorse_transport.entity.TermsAcceptance;
import com.example.racehorse_transport.entity.enums.HorseInsuranceStatus;
import com.example.racehorse_transport.entity.enums.PartyRoleType;
import com.example.racehorse_transport.entity.enums.TermsKind;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.policy.InsuranceDeclarationPolicy;
import com.example.racehorse_transport.policy.TermsDocumentPolicy;
import com.example.racehorse_transport.repository.HorseInsuranceRepository;
import com.example.racehorse_transport.repository.PartyRoleRepository;
import com.example.racehorse_transport.repository.TermsAcceptanceRepository;
import com.example.racehorse_transport.service.InsuranceService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InsuranceServiceImpl implements InsuranceService {

    private final PartyRoleRepository roles;
    private final TermsAcceptanceRepository terms;
    private final HorseInsuranceRepository insurances;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void declare(BookingHorse horse, InsuranceDeclarationRequestDto request, LocalDate submittedOn, String ipAddress) {
        InsuranceDeclarationPolicy.validate(request);
        HorseInsurance insurance = switch (request.coverageOption()) {
            case PERSONAL -> request.toEntity(horse, insurer(request.insurerPartyId(), submittedOn), null);
            case ARRANGED -> request.toEntity(horse, null, null);
            case NONE -> request.toEntity(horse, null, acceptWaiver(horse.getBooking(), ipAddress));
        };
        insurance.setStatus(HorseInsuranceStatus.DRAFT);
        insurances.save(insurance);
    }

    private Party insurer(Long id, LocalDate submittedOn) {
        if (id == null) {
            return null;
        }
        return roles.findById(new PartyRoleId(id, PartyRoleType.INSURER))
                .filter(role -> InsuranceDeclarationPolicy.isRoleEffective(role, submittedOn))
                .map(PartyRole::getParty)
                .orElseThrow(() -> new BookingRuleException("Đối tác chưa có vai trò bảo hiểm còn hiệu lực."));
    }

    private TermsAcceptance acceptWaiver(Booking booking, String ipAddress) {
        TermsAcceptance acceptance = new TermsAcceptance();
        acceptance.setBooking(booking);
        acceptance.setUser(booking.getCreatedBy());
        acceptance.setTermsType(TermsKind.LIABILITY_WAIVER);
        acceptance.setTermsVersion(TermsDocumentPolicy.LIMITED_LIABILITY_VERSION);
        acceptance.setIpAddress(ipAddress);
        return terms.save(acceptance);
    }
}
