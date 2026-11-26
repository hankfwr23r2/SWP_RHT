package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.dto.horse.HorseCreateRequestDto;
import com.example.racehorse_transport.dto.horse.HorseUpdateRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.HorseGender;
import com.example.racehorse_transport.entity.enums.HorseStatus;
import com.example.racehorse_transport.exception.HorseRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.repository.StallTypeRepository;
import com.example.racehorse_transport.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@Transactional
class HorseServiceTest {

    @Autowired private HorseService horses;
    @Autowired private TestData data;
    @Autowired private CustomerRepository customers;
    @Autowired private HorseRepository horseRows;
    @Autowired private StallTypeRepository stallTypes;
    @Autowired private BookingHorseRepository bookingHorses;

    @Test
    void customerCreatesAndUpdatesOwnHorse() {
        String owner = customer();
        String chip = chip();
        var created = horses.createHorse(owner, create(chip.substring(0, 3) + "-" + chip.substring(3)));
        assertThat(created.microchip()).isEqualTo(chip);
        assertThat(created.status()).isEqualTo(HorseStatus.ACTIVE);
        assertThat(created.createdAt()).isNotNull();

        var updated = horses.updateHorse(owner, created.id(), new HorseUpdateRequestDto("Đổi tên", HorseGender.MARE,
                (short) 2020, "Đen", typeId(), null));
        assertThat(updated.name()).isEqualTo("Đổi tên");
        assertThat(updated.microchip()).isEqualTo(chip);
        assertThat(updated.color()).isEqualTo("Đen");
        assertThat(updated.birthYear()).isEqualTo((short) 2020);
        assertThat(updated.updatedAt()).isNotNull();
        assertThat(horses.horses(owner)).singleElement().satisfies(h -> assertThat(h.name()).isEqualTo("Đổi tên"));
    }

    @Test
    void otherCustomersCannotSeeOrEditTheHorse() {
        Long id = horses.createHorse(customer(), create(chip())).id();
        String other = customer();
        assertThat(horses.horses(other)).isEmpty();
        assertThatThrownBy(() -> horses.horse(other, id)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> horses.updateHorse(other, id, new HorseUpdateRequestDto("X", HorseGender.MARE,
                (short) 2019, "Nâu", typeId(), null))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsWrongChipAndFutureBirthYear() {
        String owner = customer();
        assertThatThrownBy(() -> horses.createHorse(owner, create("123"))).isInstanceOf(HorseRuleException.class);
        var future = new HorseCreateRequestDto(chip(), "Sao Mai", HorseGender.MARE, Short.MAX_VALUE, "Nâu", typeId(), null);
        assertThatThrownBy(() -> horses.createHorse(owner, future))
                .isInstanceOf(HorseRuleException.class).hasMessage("Nhập năm sinh hợp lệ.");
        assertThat(horses.horses(owner)).isEmpty();
    }

    @Test
    void showsTheUnfinishedBookingHoldingTheHorse() {
        String owner = customer();
        Long held = horses.createHorse(owner, create(chip())).id();
        Long free = horses.createHorse(owner, create(chip())).id();
        Booking open = booking(owner, BookingStatus.PENDING_INTAKE, held, BookingHorseStatus.ACTIVE);
        booking(owner, BookingStatus.COMPLETED, free, BookingHorseStatus.ACTIVE);
        booking(owner, BookingStatus.CANCELLED, free, BookingHorseStatus.ACTIVE);
        booking(owner, BookingStatus.PENDING_INTAKE, free, BookingHorseStatus.CANCELLED);

        assertThat(horses.horse(owner, held).activeBookingCode()).isEqualTo(open.getCode());
        assertThat(horses.horse(owner, free).activeBookingCode()).isNull();
        assertThat(horses.horses(owner)).filteredOn(horse -> horse.activeBookingCode() != null)
                .singleElement().satisfies(horse -> assertThat(horse.id()).isEqualTo(held));
    }

    private Booking booking(String owner, BookingStatus status, Long horseId, BookingHorseStatus horseStatus) {
        Booking booking = data.booking(customers.findByUser_Username(owner).orElseThrow(), BookingType.LOCAL, status);
        BookingHorse row = new BookingHorse();
        row.setBooking(booking);
        row.setHorse(horseRows.findById(horseId).orElseThrow());
        row.setStallType(stallTypes.findAll().get(0));
        row.setStatus(horseStatus);
        bookingHorses.saveAndFlush(row);
        return booking;
    }

    // Câu vi phạm ràng buộc DB luôn là câu cuối của transaction
    @Test
    void duplicateChipIsRejected() {
        String chip = chip();
        horses.createHorse(customer(), create(chip));
        String other = customer();
        assertThatThrownBy(() -> horses.createHorse(other, create(chip)))
                .isInstanceOf(HorseRuleException.DuplicateHorseException.class);
    }

    private HorseCreateRequestDto create(String microchip) {
        return new HorseCreateRequestDto(microchip, "Sao Mai", HorseGender.MARE, (short) 2019, "Nâu", typeId(), null);
    }

    private Long typeId() {
        return horses.horseTypes().get(0).id();
    }

    private String customer() {
        String username = data.unique("horse-a5");
        data.customer(username);
        return username;
    }

    private String chip() {
        return "%015d".formatted(900_000_000_000_000L + System.nanoTime() % 1_000_000_000_000L);
    }
}
