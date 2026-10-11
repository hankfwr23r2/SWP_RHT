package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryService {
    List<Category> findAll();
    Optional<Category> findById(Integer id);
    Category save(Category entity);
    void deleteById(Integer id);
}
