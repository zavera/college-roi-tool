package com.example.collegeroitool.controller;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.model.Chatbot;
import com.example.collegeroitool.model.InputPayloadType;
import com.example.collegeroitool.model.ModelResponse;
import com.example.collegeroitool.repository.ChatbotRepository;
import com.example.collegeroitool.repository.ModelResponseRepository;
import com.example.collegeroitool.service.AnthropicService;
import com.example.collegeroitool.service.ChatbotContextService;
import com.example.collegeroitool.service.MonthlyCostCapExceededException;
import com.example.collegeroitool.service.TavilySearchClient;
import com.example.collegeroitool.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
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

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final int MAX_MESSAGE_LENGTH = 100;

    private final AnthropicService anthropicService;
    private final TavilySearchClient tavilySearchClient;
    private final UserService userService;
    private final ChatbotRepository chatbotRepository;
    private final ModelResponseRepository modelResponseRepository;
    private final ChatbotContextService chatbotContextService;

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    public ChatController(AnthropicService anthropicService, TavilySearchClient tavilySearchClient,
                           UserService userService, ChatbotRepository chatbotRepository,
                           ModelResponseRepository modelResponseRepository,
                           ChatbotContextService chatbotContextService) {
        this.anthropicService = anthropicService;
        this.tavilySearchClient = tavilySearchClient;
        this.userService = userService;
        this.chatbotRepository = chatbotRepository;
        this.modelResponseRepository = modelResponseRepository;
        this.chatbotContextService = chatbotContextService;
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

        // Resolve the user from the server-side session — never from client-supplied data
        AppUser user = resolveUser(principal);
        Long userId = user != null ? user.getId() : null;
        String sessionId = httpRequest.getSession(true).getId();

        // Live search: route to relevant domain based on question content. The model can also
        // pull the user's own saved-data history via the get_my_saved_data_history tool (see
        // AnthropicService.getChatResponse) — no PII summary is pre-injected here anymore.
        String liveContent = fetchLiveContent(message);

        Chatbot chatbotEntry = null;
        if (user != null) {
            chatbotEntry = new Chatbot();
            chatbotEntry.setUserId(user.getId());
            chatbotEntry.setQueryInput(message);
            chatbotEntry = chatbotRepository.save(chatbotEntry);
        }

        SseEmitter emitter = new SseEmitter(120_000L);
        Chatbot finalChatbotEntry = chatbotEntry;
        String finalMessage = message;

        try {
            String answer = anthropicService.getChatResponse(history, finalMessage, liveContent, userId, sessionId);
            emitter.send(SseEmitter.event().data(answer));
            logModelResponse(finalChatbotEntry, answer, 200);
            emitter.complete();
        } catch (MonthlyCostCapExceededException e) {
            logModelResponse(finalChatbotEntry, null, 429);
            emitter.completeWithError(e);
        } catch (Exception e) {
            logModelResponse(finalChatbotEntry, null, 500);
            emitter.completeWithError(e);
        }

        return emitter;
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
