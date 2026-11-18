package com.example.racehorse_transport.service;

import java.util.Optional;

public interface RouteCacheService {

    <T> Optional<T> find(String key, Class<T> type);

    void put(String key, Object value);
}
