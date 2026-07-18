package com.example.collegeroitool.controller;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.service.SubscriptionService;
import com.example.collegeroitool.service.UserService;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/stripe")
public class StripeController {

    @Value("${stripe.secret.key}")
    private String secretKey;

    @Value("${stripe.publishable.key}")
    private String publishableKey;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    @Value("${stripe.price.id}")
    private String priceId;

    private final UserService userService;
    private final SubscriptionService subscriptionService;

    public StripeController(UserService userService, SubscriptionService subscriptionService) {
        this.userService = userService;
        this.subscriptionService = subscriptionService;
    }

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> config() {
        return ResponseEntity.ok(Map.of("publishableKey", publishableKey));
    }

    @PostMapping("/create-checkout-session")
    public ResponseEntity<?> createCheckoutSession(
            Principal principal,
            @RequestBody(required = false) Map<String, String> body) {

        String email = resolveEmail(principal);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        try {
            String baseUrl = (body != null && body.containsKey("baseUrl"))
                ? body.get("baseUrl")
                : "https://astra-ed.org";

            SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomerEmail(email)
                .addLineItem(SessionCreateParams.LineItem.builder()
                    .setPrice(priceId)
                    .setQuantity(1L)
                    .build())
                .setSuccessUrl(baseUrl + "/?payment=success&session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(baseUrl + "/?payment=cancelled")
                .putMetadata("email", email)
                .build();

            Session session = Session.create(params);
            return ResponseEntity.ok(Map.of("url", session.getUrl()));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** Turns recurring billing OFF: cancels the live Stripe subscription so no further $99/mo charges occur. */
    @PostMapping("/cancel-subscription")
    public ResponseEntity<?> cancelSubscription(Principal principal) {
        String email = resolveEmail(principal);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        AppUser user = userService.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
        }

        subscriptionService.getStripeSubscriptionId(user).ifPresent(stripeSubscriptionId -> {
            try {
                Subscription stripeSub = Subscription.retrieve(stripeSubscriptionId);
                stripeSub.cancel();
            } catch (Exception e) {
                // Subscription may already be canceled on Stripe's side (e.g. a prior webhook already
                // fired) — proceed to clear local state regardless so the user isn't stuck.
            }
        });

        subscriptionService.deactivateAndClearStripe(user);
        return ResponseEntity.ok(Map.of("subscriptionActive", false));
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            HttpServletRequest request,
            @RequestHeader("Stripe-Signature") String sigHeader) throws IOException {

        String payload = new String(request.getInputStream().readAllBytes());

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        switch (event.getType()) {
            case "checkout.session.completed" -> {
                Session session = (Session) event.getDataObjectDeserializer()
                    .getObject().orElse(null);
                if (session != null) {
                    String email = session.getCustomerEmail();
                    if (email == null && session.getMetadata() != null) {
                        email = session.getMetadata().get("email");
                    }
                    if (email != null) {
                        userService.activateSubscription(email, session.getCustomer(), session.getSubscription());
                    }
                }
            }
            case "customer.subscription.deleted", "customer.subscription.updated" -> {
                Subscription sub = (Subscription) event.getDataObjectDeserializer()
                    .getObject().orElse(null);
                if (sub != null && "canceled".equals(sub.getStatus())) {
                    try {
                        Customer customer = Customer.retrieve(sub.getCustomer());
                        if (customer.getEmail() != null) {
                            userService.deactivateSubscription(customer.getEmail());
                        }
                    } catch (Exception e) {
                        // log and continue — webhook must return 200
                    }
                }
            }
        }

        return ResponseEntity.ok("received");
    }

    private String resolveEmail(Principal principal) {
        if (principal == null) return null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth.getPrincipal() instanceof OAuth2User oAuth2User) {
            return oAuth2User.getAttribute("email");
        }
        return principal.getName();
    }
}
