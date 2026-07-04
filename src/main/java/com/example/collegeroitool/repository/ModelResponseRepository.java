package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.InputPayloadType;
import com.example.collegeroitool.model.ModelResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModelResponseRepository extends JpaRepository<ModelResponse, Long> {
    List<ModelResponse> findByTypeInputPayloadAndInputIdIn(InputPayloadType type, List<Long> inputIds);
    Optional<ModelResponse> findFirstByTypeInputPayloadAndInputIdOrderByCreatedAtDesc(InputPayloadType type, Long inputId);
}
