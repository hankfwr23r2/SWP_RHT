package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Country;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryRepository extends JpaRepository<Country, String> {
}
