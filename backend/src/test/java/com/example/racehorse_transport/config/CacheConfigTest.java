package com.example.racehorse_transport.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigTest {

    @Test
    void cacheManagerProvidesLocationsCache() {
        CacheConfig config = new CacheConfig();
        CacheManager manager = config.cacheManager();

        assertThat(manager).isNotNull();
        Cache locationsCache = manager.getCache("locations");
        assertThat(locationsCache).isNotNull();

        locationsCache.put("VN_all", "cachedData");
        assertThat(locationsCache.get("VN_all", String.class)).isEqualTo("cachedData");
    }
}
