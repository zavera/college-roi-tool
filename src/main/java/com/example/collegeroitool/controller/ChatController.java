package com.example.collegeroitool.controller;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.model.Chatbot;
import com.example.collegeroitool.model.InputPayloadType;
import com.example.collegeroitool.model.ModelResponse;
import com.example.collegeroitool.repository.ChatbotRepository;
import com.example.collegeroitool.repository.ModelResponseRepository;
import com.example.collegeroitool.service.ChatbotContextService;
import com.example.collegeroitool.service.GroqService;
import com.example.collegeroitool.service.TavilySearchClient;
import com.example.collegeroitool.service.UserService;
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

    private final GroqService groqService;
    private final TavilySearchClient tavilySearchClient;
    private final UserService userService;
    private final ChatbotRepository chatbotRepository;
    private final ModelResponseRepository modelResponseRepository;
    private final ChatbotContextService chatbotContextService;

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    @Value("${groq.model}")
    private String modelName;

    public ChatController(GroqService groqService, TavilySearchClient tavilySearchClient,
                           UserService userService, ChatbotRepository chatbotRepository,
                           ModelResponseRepository modelResponseRepository,
                           ChatbotContextService chatbotContextService) {
        this.groqService = groqService;
        this.tavilySearchClient = tavilySearchClient;
        this.userService = userService;
        this.chatbotRepository = chatbotRepository;
        this.modelResponseRepository = modelResponseRepository;
        this.chatbotContextService = chatbotContextService;
    }

    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter send(@RequestBody Map<String, Object> body, Principal principal) {
        if (principal == null && !devBypass) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        String message = (String) body.getOrDefault("message", "");
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is required");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) body.getOrDefault("history", List.of());

        // Resolve the user from the server-side session — never from client-supplied data
        AppUser user = principal != null ? userService.findByEmail(resolveEmail(principal)).orElse(null) : null;

        // Live search: route to relevant domain based on question content
        String liveContent = fetchLiveContent(message);
        if (user != null) {
            liveContent = chatbotContextService.buildContextSummary(user.getId()) + "\n\n" + liveContent;
        }

        Chatbot chatbotEntry = null;
        if (user != null) {
            chatbotEntry = new Chatbot();
            chatbotEntry.setUserId(user.getId());
            chatbotEntry.setQueryInput(message);
            chatbotEntry = chatbotRepository.save(chatbotEntry);
        }

        SseEmitter emitter = new SseEmitter(120_000L);
        StringBuilder answer = new StringBuilder();
        Chatbot finalChatbotEntry = chatbotEntry;

        groqService.streamAstraChatResponse(history, message, liveContent,
            token -> {
                answer.append(token);
                try {
                    emitter.send(SseEmitter.event().data(token));
                } catch (Exception ignored) {
                    // client likely disconnected; the stream will be torn down by onError/onComplete
                }
            },
            () -> {
                logModelResponse(finalChatbotEntry, answer.toString(), 200);
                emitter.complete();
            },
            error -> {
                logModelResponse(finalChatbotEntry, null, 500);
                emitter.completeWithError(error);
            });

        return emitter;
    }

    private void logModelResponse(Chatbot chatbotEntry, String outputPayload, int status) {
        if (chatbotEntry == null) return;
        ModelResponse resp = new ModelResponse();
        resp.setModelName(modelName);
        resp.setTypeInputPayload(InputPayloadType.CHATBOT);
        resp.setInputId(chatbotEntry.getId());
        resp.setOutputPayload(outputPayload);
        resp.setResponseStatus(status);
        modelResponseRepository.save(resp);
    }

    private String resolveEmail(Principal principal) {
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
