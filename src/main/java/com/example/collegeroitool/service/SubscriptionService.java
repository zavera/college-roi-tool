package com.example.collegeroitool.service;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.model.Subscription;
import com.example.collegeroitool.repository.SubscriptionRepository;
import com.example.collegeroitool.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionService {

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    private final SubscriptionRepository repo;
    private final UserRepository userRepository;
    private final AppConfigService appConfigService;
    private final ExemptionService exemptionService;
    private final ResendEmailService emailService;

    public SubscriptionService(SubscriptionRepository repo, UserRepository userRepository,
                                AppConfigService appConfigService, ExemptionService exemptionService,
                                ResendEmailService emailService) {
        this.repo = repo;
        this.userRepository = userRepository;
        this.appConfigService = appConfigService;
        this.exemptionService = exemptionService;
        this.emailService = emailService;
    }

    /** True if the user has a paid subscription OR a manually granted exemption. */
    public boolean hasAccess(AppUser user) {
        return getOrCreateForUser(user).isActive() || exemptionService.getOrCreateForUser(user).isActive();
    }

    /** Returns the user's subscription row, creating a default (inactive) one on first login. */
    public Subscription getOrCreateForUser(AppUser user) {
        return repo.findByUserId(user.getId()).orElseGet(() -> {
            Subscription sub = new Subscription();
            sub.setUserId(user.getId());
            sub.setActive(false);
            sub.setAmountCents(appConfigService.getSubscriptionAmountCents());
            return repo.save(sub);
        });
    }

    public boolean setActive(AppUser user, boolean active) {
        Subscription sub = getOrCreateForUser(user);
        sub.setActive(active);
        return repo.save(sub).isActive();
    }

    /**
     * Turns recurring billing ON: records the Stripe customer/subscription this access is tied to.
     * Emails the user only on a real inactive→active transition, so webhook retries and the
     * checkout-redirect/webhook pair don't send duplicate notifications.
     */
    public boolean activateWithStripe(AppUser user, String stripeCustomerId, String stripeSubscriptionId) {
        Subscription sub = getOrCreateForUser(user);
        boolean wasActive = sub.isActive();
        sub.setActive(true);
        sub.setStripeCustomerId(stripeCustomerId);
        sub.setStripeSubscriptionId(stripeSubscriptionId);
        boolean active = repo.save(sub).isActive();
        if (!wasActive) {
            sendToggleEmail(user, true);
        }
        return active;
    }

    /**
     * Turns recurring billing OFF and clears the Stripe subscription reference so the recurrence is
     * fully terminated. Emails the user only on a real active→inactive transition — whichever of the
     * user-initiated cancel or the async Stripe webhook lands first triggers the email; the other
     * sees no state change and is a no-op.
     */
    public boolean deactivateAndClearStripe(AppUser user) {
        Subscription sub = getOrCreateForUser(user);
        boolean wasActive = sub.isActive();
        sub.setActive(false);
        sub.setStripeCustomerId(null);
        sub.setStripeSubscriptionId(null);
        boolean active = repo.save(sub).isActive();
        if (wasActive) {
            sendToggleEmail(user, false);
        }
        return !active;
    }

    private void sendToggleEmail(AppUser user, boolean turnedOn) {
        if (user.getEmail() == null) return;
        String amount = String.format("$%.2f", appConfigService.getSubscriptionAmountCents() / 100.0);
        String firstName = (user.getName() != null && user.getName().contains(" "))
            ? user.getName().substring(0, user.getName().indexOf(' ')) : (user.getName() != null ? user.getName() : "there");

        String subject = turnedOn ? "Your Astra subscription is active" : "Your Astra subscription has been cancelled";
        String body = turnedOn
            ? "Hi " + firstName + ",\n\n" +
              "Your Astra subscription is now active — you'll be billed " + amount + "/month on a recurring basis.\n\n" +
              "You can turn this off anytime from the subscription toggle in the Astra header, which cancels future billing immediately.\n\n" +
              "Questions? Reply to this email or reach us at ambreen@callistotech.org\n\n" +
              "— Ambreen & the Astra Team"
            : "Hi " + firstName + ",\n\n" +
              "Your Astra subscription has been cancelled and your " + amount + "/month recurring billing has stopped immediately — " +
              "you won't be charged again, and premium access has ended as of now.\n\n" +
              "You can resubscribe anytime from the subscription toggle in the Astra header.\n\n" +
              "Questions? Reply to this email or reach us at ambreen@callistotech.org\n\n" +
              "— Ambreen & the Astra Team";

        emailService.send(user.getEmail(), subject, body);
    }

    /** The live Stripe subscription backing this user's access, if any (absent for dev/admin-granted access). */
    public java.util.Optional<String> getStripeSubscriptionId(AppUser user) {
        return repo.findByUserId(user.getId())
            .map(Subscription::getStripeSubscriptionId)
            .filter(id -> id != null && !id.isBlank());
    }

    public boolean isActive() {
        if (devBypass) return true;

        String email = currentUserEmail();
        if (email == null) return false;

        return userRepository.findByEmail(email)
            .map(this::hasAccess)
            .orElse(false);
    }

    private String currentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;

        Object principal = auth.getPrincipal();
        if (principal instanceof OAuth2User oAuth2User) {
            return oAuth2User.getAttribute("email");
        }
        // Local (email/password) users: getName() returns the email
        String name = auth.getName();
        return "anonymousUser".equals(name) ? null : name;
    }
}
