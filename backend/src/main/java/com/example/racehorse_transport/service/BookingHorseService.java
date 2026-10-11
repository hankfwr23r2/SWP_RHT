package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.BookingHorseId;
import java.util.List;
import java.util.Optional;

public interface BookingHorseService {
    List<BookingHorse> findAll();
    Optional<BookingHorse> findById(BookingHorseId id);
    BookingHorse save(BookingHorse entity);
    void deleteById(BookingHorseId id);
}
