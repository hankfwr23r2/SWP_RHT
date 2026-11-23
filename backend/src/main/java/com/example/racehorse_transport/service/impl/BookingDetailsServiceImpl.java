package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.AddressInput;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.HorseInput;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.TermsKind;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.BookingSubmissionPolicy;
import com.example.racehorse_transport.policy.TermsDocumentPolicy;
import com.example.racehorse_transport.repository.BillableServiceRepository;
import com.example.racehorse_transport.repository.BookingAddressRepository;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.BookingHorseAddOnRepository;
import com.example.racehorse_transport.repository.BookingPartyRepository;
import com.example.racehorse_transport.repository.CustomerAddressRepository;
import com.example.racehorse_transport.repository.StallTypeRepository;
import com.example.racehorse_transport.repository.TermsAcceptanceRepository;
import com.example.racehorse_transport.service.BookingDetailsService;
import com.example.racehorse_transport.service.InsuranceService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class BookingDetailsServiceImpl implements BookingDetailsService {

    private final BookingPartyRepository parties;
    private final BookingAddressRepository addresses;
    private final CustomerAddressRepository customerAddresses;
    private final BookingHorseRepository bookingHorses;
    private final StallTypeRepository stalls;
    private final BillableServiceRepository services;
    private final BookingHorseAddOnRepository addOns;
    private final TermsAcceptanceRepository terms;
    private final InsuranceService insurance;

    @Override
    public void saveParties(BookingCreateRequestDto request, Booking booking) {
        for (var input : request.parties()) {
            BookingParty party = input.toEntity(booking);
            party.setId(new BookingPartyId(booking.getId(), input.bookingRole()));
            parties.save(party);
        }
    }

    @Override
    public void saveAddress(AddressInput input, Province province, Booking booking) {
        CustomerAddress source = null;
        if (input.sourceCustomerAddressId() != null) {
            source = customerAddresses.findByIdAndCustomer_UserId(input.sourceCustomerAddressId(), booking.getCustomer().getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ trong sổ của khách."));
        }
        addresses.save(input.toEntity(booking, province, source));
    }

    @Override
    public void saveHorse(HorseInput input, Horse horse, Booking booking, String ipAddress, LocalDate submittedOn) {
        StallType stall = stalls.findById(input.stallTypeCode().trim())
                .orElseThrow(() -> new BookingRuleException("Loại khoang không hợp lệ."));
        BookingHorse row = input.toEntity(booking, horse, stall);
        row.setStatus(BookingHorseStatus.ACTIVE);
        bookingHorses.save(row);
        if (input.addOns() != null) {
            for (var addOn : input.addOns()) {
                BillableService service = services.findById(addOn.serviceId())
                        .orElseThrow(() -> new BookingRuleException("Không tìm thấy dịch vụ thêm."));
                BookingSubmissionPolicy.validateAddOn(service);
                BookingHorseAddOn selection = addOn.toEntity(row, service);
                selection.setId(new BookingHorseAddOnId(row.getId(), service.getId()));
                addOns.save(selection);
            }
        }
        insurance.declare(row, input.insurance(), submittedOn, ipAddress);
    }

    @Override
    public void acceptServiceTerms(Booking booking, String ipAddress) {
        TermsAcceptance acceptance = new TermsAcceptance();
        acceptance.setBooking(booking);
        acceptance.setUser(booking.getCreatedBy());
        acceptance.setTermsType(TermsKind.SERVICE_TERMS);
        acceptance.setTermsVersion(TermsDocumentPolicy.TRANSPORT_TERMS_VERSION);
        acceptance.setIpAddress(ipAddress);
        terms.save(acceptance);
    }
}
