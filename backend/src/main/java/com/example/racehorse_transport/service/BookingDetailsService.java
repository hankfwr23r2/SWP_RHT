package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.AddressInput;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.HorseInput;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingAddress;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.Province;
import java.time.LocalDate;
import org.springframework.web.multipart.MultipartFile;

public interface BookingDetailsService {
    void saveParties(BookingCreateRequestDto request, Booking booking);

    BookingAddress saveAddress(AddressInput input, Province province, Booking booking);

    void saveHorse(HorseInput input, Horse horse, Booking booking, BookingAddress deliveryAddress,
            String ipAddress, LocalDate submittedOn, MultipartFile insuranceFile);

    void acceptServiceTerms(Booking booking, String ipAddress);
}
