package com.example.racehorse_transport.repository;
import com.example.racehorse_transport.entity.CaptureAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface CaptureAssetRepository extends JpaRepository<CaptureAsset, Long> {
    Optional<CaptureAsset> findByUrl(String url);
}
