package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Province;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProvinceRepository extends JpaRepository<Province, String> {

    List<Province> findByCountry_CodeOrderByName(String countryCode);
}
