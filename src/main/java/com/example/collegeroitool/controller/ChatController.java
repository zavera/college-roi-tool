package com.example.collegeroitool.controller;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.model.ChatMessage;
import com.example.collegeroitool.model.ChatSession;
import com.example.collegeroitool.model.Chatbot;
import com.example.collegeroitool.model.InputPayloadType;
import com.example.collegeroitool.model.ModelResponse;
import com.example.collegeroitool.repository.ChatMessageRepository;
import com.example.collegeroitool.repository.ChatSessionRepository;
import com.example.collegeroitool.repository.ChatbotRepository;
import com.example.collegeroitool.repository.ModelResponseRepository;
import com.example.collegeroitool.service.AnthropicService;
import com.example.collegeroitool.service.ChatbotContextService;
import com.example.collegeroitool.service.MonthlyCostCapExceededException;
import com.example.collegeroitool.service.TavilySearchClient;
import com.example.collegeroitool.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final int MAX_MESSAGE_LENGTH = 100;

    // SseEmitter.send() calls made before the controller method returns are queued internally by
    // Spring and only flushed once the method returns and async processing is handed off — so
    // real token-by-token streaming requires doing the generation work on another thread and
    // returning the emitter immediately. Same pattern as GroqService's streamExecutor.
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    private final AnthropicService anthropicService;
    private final TavilySearchClient tavilySearchClient;
    private final UserService userService;
    private final ChatbotRepository chatbotRepository;
    private final ModelResponseRepository modelResponseRepository;
    private final ChatbotContextService chatbotContextService;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    public ChatController(AnthropicService anthropicService, TavilySearchClient tavilySearchClient,
                           UserService userService, ChatbotRepository chatbotRepository,
                           ModelResponseRepository modelResponseRepository,
                           ChatbotContextService chatbotContextService,
                           ChatSessionRepository chatSessionRepository,
                           ChatMessageRepository chatMessageRepository) {
        this.anthropicService = anthropicService;
        this.tavilySearchClient = tavilySearchClient;
        this.userService = userService;
        this.chatbotRepository = chatbotRepository;
        this.modelResponseRepository = modelResponseRepository;
        this.chatbotContextService = chatbotContextService;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter send(@RequestBody Map<String, Object> body, Principal principal,
                            HttpServletRequest httpRequest) {
        if (principal == null && !devBypass) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        String message = (String) body.getOrDefault("message", "");
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is required");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_MESSAGE_LENGTH);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) body.getOrDefault("history", List.of());

        // Resolve the user from the server-side HttpSession (set at login by
        // SecurityConfig.bootstrapSessionState) — never from client-supplied data. Falls back to
        // re-resolving from the Authentication principal only if the session predates that change
        // (e.g. a session created before this attribute existed) or under dev bypass.
        HttpSession httpSession = httpRequest.getSession(true);
        Object sessionUserId = httpSession.getAttribute("userId");
        AppUser user = sessionUserId instanceof Long
            ? userService.findById((Long) sessionUserId).orElse(null)
            : resolveUser(principal);
        Long userId = user != null ? user.getId() : null;
        String sessionId = httpSession.getId();

        // Live search: route to relevant domain based on question content. The model can also
        // pull the user's own saved-data history via the get_my_saved_data_history tool (see
        // AnthropicService.getChatResponse) — no PII summary is pre-injected here anymore.
        String liveContent = fetchLiveContent(message);

        Chatbot chatbotEntry = null;
        ChatSession chatSession = null;
        if (user != null) {
            chatbotEntry = new Chatbot();
            chatbotEntry.setUserId(user.getId());
            chatbotEntry.setQueryInput(message);
            chatbotEntry = chatbotRepository.save(chatbotEntry);

            chatSession = getOrCreateChatSession(user, httpSession);
            ChatMessage userMsg = new ChatMessage();
            userMsg.setChatSessionId(chatSession.getId());
            userMsg.setUserId(user.getId());
            userMsg.setRole("user");
            userMsg.setContent(message);
            chatMessageRepository.save(userMsg);
        }

        SseEmitter emitter = new SseEmitter(120_000L);
        Chatbot finalChatbotEntry = chatbotEntry;
        ChatSession finalChatSession = chatSession;
        AppUser finalUser = user;
        String finalMessage = message;

        streamExecutor.submit(() -> {
            try {
                String answer = anthropicService.getChatResponse(history, finalMessage, liveContent, userId, sessionId,
                    token -> {
                        try {
                            emitter.send(SseEmitter.event().data(token));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                logModelResponse(finalChatbotEntry, answer, 200);
                if (finalChatSession != null) {
                    saveAssistantMessage(finalChatSession, finalUser, answer);
                }
                emitter.complete();
            } catch (MonthlyCostCapExceededException e) {
                logModelResponse(finalChatbotEntry, null, 429);
                emitter.completeWithError(e);
            } catch (Exception e) {
                logModelResponse(finalChatbotEntry, null, 500);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /** Reuses the caller's chat session (stored on HttpSession, ORM-guarded to this user) if
     *  one already exists, otherwise starts a new one. */
    private ChatSession getOrCreateChatSession(AppUser user, HttpSession httpSession) {
        Object existingId = httpSession.getAttribute("chatSessionId");
        if (existingId instanceof Long) {
            ChatSession existing = chatSessionRepository.findByIdAndUserId((Long) existingId, user.getId()).orElse(null);
            if (existing != null) return existing;
        }
        ChatSession created = new ChatSession();
        created.setUserId(user.getId());
        created = chatSessionRepository.save(created);
        httpSession.setAttribute("chatSessionId", created.getId());
        return created;
    }

    private void saveAssistantMessage(ChatSession chatSession, AppUser user, String answer) {
        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setChatSessionId(chatSession.getId());
        assistantMsg.setUserId(user.getId());
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(answer);
        chatMessageRepository.save(assistantMsg);
        chatSession.setUpdatedAt(java.time.LocalDateTime.now());
        chatSessionRepository.save(chatSession);
    }

    private void logModelResponse(Chatbot chatbotEntry, String outputPayload, int status) {
        if (chatbotEntry == null) return;
        ModelResponse resp = new ModelResponse();
        resp.setModelName(anthropicService.getChatbotModel());
        resp.setTypeInputPayload(InputPayloadType.CHATBOT);
        resp.setInputId(chatbotEntry.getId());
        resp.setOutputPayload(outputPayload);
        resp.setResponseStatus(status);
        modelResponseRepository.save(resp);
    }

    private AppUser resolveUser(Principal principal) {
        String email = resolveEmail(principal);
        if (email == null) return devBypass ? userService.findOrCreateDevUser() : null;
        return userService.findByEmail(email).orElse(null);
    }

    private String resolveEmail(Principal principal) {
        if (principal == null) return null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof OAuth2User oAuth2User)
            ? oAuth2User.<String>getAttribute("email")
            : principal.getName();
    }

    private String fetchLiveContent(String message) {
        String q = message.toLowerCase();
        List<String> domains;
        String query;

        if (q.contains("pslf") || q.contains("forgiveness") || q.contains("save") || q.contains("repayment") || q.contains("loan")) {
            domains = List.of("studentaid.gov");
            query = message + " site:studentaid.gov 2025";
        } else if (q.contains("scholarship") || q.contains("grant")) {
            domains = List.of("studentaid.gov", "scholarships.com");
            query = message + " scholarships 2025";
        } else if (q.contains("fafsa") || q.contains("financial aid") || q.contains("sai") || q.contains("efc")) {
            domains = List.of("studentaid.gov", "fsapartners.ed.gov");
            query = message + " site:studentaid.gov";
        } else {
            domains = List.of("studentaid.gov");
            query = message + " college financial aid 2025";
        }

        try {
            StringBuilder sb = new StringBuilder();
            var results = tavilySearchClient.searchHandbook(query, 3, domains, 800);
            for (var r : results) {
                sb.append("[Source: ").append(r.get("url")).append("]\n");
                sb.append(r.get("content")).append("\n\n");
            }
            return sb.toString().trim();
        } catch (Exception ignored) {
            return "";
        }
    }
}
