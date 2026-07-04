package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.Chatbot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatbotRepository extends JpaRepository<Chatbot, Long> {
    List<Chatbot> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
