package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// intakeByName: nhân viên đã tiếp nhận đơn, chỉ trả cho nhân viên
public record BookingDetailResponseDto(BookingResponseDto booking, List<PartySnapshot> parties,
        List<AddressSnapshot> addresses, List<HorseDetail> horses, String reviewReason,
        TransferMethod pickupMethod, TransferMethod deliveryMethod, String intakeByName, boolean rated, Instant balanceHoldAt, boolean assignedToMe, boolean canChangeDelivery, String deliveryChangeDisabledReason,
        String customerName, String customerPhone, String routeName, LocalDate departureDate) {

    public static BookingDetailResponseDto from(Booking booking, List<BookingParty> parties,
                                                 List<BookingAddress> addresses, List<HorseDetail> horses) {
        return from(booking, parties, addresses, horses, null, null, false);
    }

    public static BookingDetailResponseDto from(Booking booking, List<BookingParty> parties,
            List<BookingAddress> addresses, List<HorseDetail> horses, String reviewReason, String intakeByName, boolean rated) {
        return from(booking, parties, addresses, horses, reviewReason, intakeByName, rated, null, false);
    }

    public static BookingDetailResponseDto from(Booking booking, List<BookingParty> parties,
            List<BookingAddress> addresses, List<HorseDetail> horses, String reviewReason, String intakeByName, boolean rated,
            Instant balanceHoldAt, boolean assignedToMe) {
        var departure = booking.getDeparture();
        return new BookingDetailResponseDto(BookingResponseDto.from(booking),
                parties.stream().map(PartySnapshot::from).toList(),
                addresses.stream().map(AddressSnapshot::from).toList(), List.copyOf(horses), reviewReason,
                booking.getPickupMethod(), booking.getDeliveryMethod(), intakeByName, rated, balanceHoldAt, assignedToMe, false, null,
                booking.getCustomer().getFullName(), booking.getCustomer().getPhone(),
                departure == null ? null : departure.getRoute().getName(),
                departure == null ? null : departure.getDepartureDate());
    }

    public BookingDetailResponseDto withDeliveryChange(String reason) {
        return new BookingDetailResponseDto(booking, parties, addresses, horses, reviewReason, pickupMethod,
                deliveryMethod, intakeByName, rated, balanceHoldAt, assignedToMe, reason == null, reason, customerName, customerPhone, routeName, departureDate);
    }

    public record PartySnapshot(BookingPartyRole bookingRole, String fullName, String phone,
            String identityDocumentType, String identityDocumentNumber, String identityDocumentCountryCode, String address) {

        public static PartySnapshot from(BookingParty party) {
            return new PartySnapshot(party.getId().getBookingRole(), party.getFullName(), party.getPhone(),
                    party.getIdentityDocumentType(), party.getIdentityDocumentNumber(),
                    party.getIdentityDocumentCountryCode(), party.getAddress());
        }
    }

    public record AddressSnapshot(Long id, AddressRole addressType, String addressLine, String provinceCode,
            String countryCode, BigDecimal latitude, BigDecimal longitude, String contactName, String contactPhone,
            String accessNote, Long sourceCustomerAddressId) {

        public static AddressSnapshot from(BookingAddress address) {
            return new AddressSnapshot(address.getId(), address.getAddressType(), address.getAddressLine(),
                    address.getProvince().getCode(), address.getCountry().getCode(), address.getLatitude(),
                    address.getLongitude(), address.getContactName(), address.getContactPhone(), address.getAccessNote(),
                    address.getSourceAddress() == null ? null : address.getSourceAddress().getId());
        }
    }

    public record HorseDetail(Long id, Long horseId, String horseName, String stallTypeCode, String dietNote,
            Short feedingsPerDay, Boolean feedSuppliedByOwner, BookingHorseStatus status,
            List<AddOn> addOns, InsuranceDetail insurance, Long deliveryAddressId, String microchip, String horseTypeName, HorseGender sex, Short birthYear, String color) {

        public static HorseDetail from(BookingHorse row, List<BookingHorseAddOn> addOns, HorseInsurance insurance) {
            var horse = row.getHorse();
            return new HorseDetail(row.getId(), horse.getId(), horse.getName(),
                    row.getStallType().getCode(), row.getDietNote(), row.getFeedingsPerDay(),
                    row.getFeedSuppliedByOwner(), row.getStatus(), addOns.stream().map(AddOn::from).toList(),
                    insurance == null ? null : InsuranceDetail.from(insurance),
                    row.getDeliveryAddress() == null ? null : row.getDeliveryAddress().getId(), horse.getMicrochip(),
                    horse.getHorseType() == null ? null : horse.getHorseType().getName(), horse.getSex(), horse.getBirthYear(), horse.getColor());
        }
    }

    public record AddOn(Long serviceId, String serviceName, int quantity) {

        public static AddOn from(BookingHorseAddOn addOn) {
            return new AddOn(addOn.getBillableService().getId(), addOn.getBillableService().getName(), addOn.getQuantity());
        }
    }

    public record InsuranceDetail(Long id, InsuranceCoverageOption coverageOption, HorseInsuranceStatus status,
            String insurerName, String policyNumber, LocalDate validFrom, LocalDate validTo, Long waiverTermsAcceptanceId,
            BigDecimal coverageAmount, BigDecimal premiumAmount, String currencyCode, Instant quoteExpiresAt,
            String revisionReason, BigDecimal refundedPremiumAmount) {

        public static InsuranceDetail from(HorseInsurance insurance) {
            return new InsuranceDetail(insurance.getId(), insurance.getCoverageOption(), insurance.getStatus(),
                    insurance.getInsurerName(),
                    insurance.getPolicyNumber(), insurance.getValidFrom(), insurance.getValidTo(),
                    insurance.getWaiverTermsAcceptance() == null ? null : insurance.getWaiverTermsAcceptance().getId(),
                    insurance.getCoverageAmount(), insurance.getPremiumAmount(), insurance.getCurrencyCode(),
                    insurance.getQuoteExpiresAt(), insurance.getRevisionReason(), insurance.getRefundedPremiumAmount());
        }
    }
}
