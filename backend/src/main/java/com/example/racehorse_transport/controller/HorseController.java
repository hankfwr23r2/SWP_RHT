package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.HorseDTO;
import com.example.racehorse_transport.entity.Horse;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.HorseService;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/horses")
public class HorseController {

    private final HorseService horseService;
    private final ModelMapper modelMapper;

    @Autowired
    public HorseController(HorseService horseService, ModelMapper modelMapper) {
        this.horseService = horseService;
        this.modelMapper = modelMapper;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<HorseDTO>>> getAllHorses() {
        List<Horse> horses = horseService.findAll();
        List<HorseDTO> dtos = horses.stream()
                .map(horse -> modelMapper.map(horse, HorseDTO.class))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos, "Success fetching all horses"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HorseDTO>> getHorseById(@PathVariable Integer id) {
        Optional<Horse> horse = horseService.findById(id);
        if (horse.isPresent()) {
            HorseDTO dto = modelMapper.map(horse.get(), HorseDTO.class);
            return ResponseEntity.ok(ApiResponse.success(dto, "Success fetching horse"));
        } else {
            return ResponseEntity.status(404).body(ApiResponse.error("Horse not found"));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HorseDTO>> createHorse(@RequestBody HorseDTO horseDTO) {
        Horse horse = modelMapper.map(horseDTO, Horse.class);
        Horse savedHorse = horseService.save(horse);
        HorseDTO resultDTO = modelMapper.map(savedHorse, HorseDTO.class);
        return ResponseEntity.ok(ApiResponse.success(resultDTO, "Horse created successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteHorse(@PathVariable Integer id) {
        horseService.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Horse deleted successfully"));
    }
}
