package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByChatSessionIdAndUserIdOrderByCreatedAtAsc(Long chatSessionId, Long userId);
    List<ChatMessage> findByUserIdOrderByCreatedAtAsc(Long userId);
}
