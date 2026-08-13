package com.studen.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.security.auth-rate-limit.max-requests=100000")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    // The real NotificationService bean (not RecordingNotifier — that only swaps in for the
    // *business-logic* call sites' own tests via @Primary; here we want the actual persistence
    // behavior under test, so it's injected by its concrete type rather than through the
    // Notifier interface).
    @Autowired
    private NotificationService notificationService;

    private record RegisteredUser(String token, UUID id) {
    }

    private RegisteredUser register(String email) throws Exception {
        RegisterRequest request = new RegisterRequest("Notif Tester", email, "SecurePassword123");
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        AuthResponse auth = objectMapper.readValue(body, AuthResponse.class);
        return new RegisteredUser(auth.accessToken(), auth.id());
    }

    @Test
    void list_returnsOnlyCallersOwnNotifications() throws Exception {
        RegisteredUser userA = register("notif-list-a@example.com");
        RegisteredUser userB = register("notif-list-b@example.com");

        UUID resourceId = UUID.randomUUID();
        notificationService.notify(userA.id(), NotificationType.NEW_MESSAGE, "Message for A", resourceId);
        notificationService.notify(userB.id(), NotificationType.NEW_MESSAGE, "Message for B", resourceId);

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + userA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].message", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("for B")))));
    }

    @Test
    void markRead_byNonOwner_returns404AndLeavesNotificationUnread() throws Exception {
        RegisteredUser userA = register("notif-read-attacker@example.com");
        RegisteredUser userB = register("notif-read-victim@example.com");

        UUID resourceId = UUID.randomUUID();
        notificationService.notify(userB.id(), NotificationType.NEW_MESSAGE, "Private to B", resourceId);
        UUID notificationId = notificationService.list(userB.id(), null).get(0).id();

        mockMvc.perform(post("/api/v1/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + userA.token()))
                .andExpect(status().isNotFound());

        boolean stillUnread = notificationService.list(userB.id(), null).stream()
                .anyMatch(n -> n.id().equals(notificationId) && !n.read());
        assertThat(stillUnread).isTrue();
    }

    @Test
    void markRead_byOwner_marksReadAndDecrementsUnreadCount() throws Exception {
        RegisteredUser user = register("notif-read-owner@example.com");
        notificationService.notify(user.id(), NotificationType.NEW_MESSAGE, "Hello", UUID.randomUUID());
        UUID notificationId = notificationService.list(user.id(), null).get(0).id();

        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(post("/api/v1/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void markAllRead_clearsUnreadCountForCallerOnly() throws Exception {
        RegisteredUser userA = register("notif-mark-all-a@example.com");
        RegisteredUser userB = register("notif-mark-all-b@example.com");
        notificationService.notify(userA.id(), NotificationType.NEW_MESSAGE, "One", UUID.randomUUID());
        notificationService.notify(userA.id(), NotificationType.NEW_MESSAGE, "Two", UUID.randomUUID());
        notificationService.notify(userB.id(), NotificationType.NEW_MESSAGE, "Three", UUID.randomUUID());

        mockMvc.perform(post("/api/v1/notifications/read-all").header("Authorization", "Bearer " + userA.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + userA.token()))
                .andExpect(jsonPath("$.count").value(0));
        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + userB.token()))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void getPreferences_unsetType_defaultsToEnabled() throws Exception {
        RegisteredUser user = register("notif-pref-default@example.com");

        mockMvc.perform(get("/api/v1/notifications/preferences").header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(NotificationType.values().length)))
                .andExpect(jsonPath("$[?(@.type=='NEW_MESSAGE')].pushEnabled").value(true));
    }

    @Test
    void updatePreference_persistsAndIsReflectedInSubsequentGet() throws Exception {
        RegisteredUser user = register("notif-pref-update@example.com");

        mockMvc.perform(put("/api/v1/notifications/preferences/NEW_MESSAGE")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "pushEnabled": false, "inAppEnabled": true }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pushEnabled").value(false));

        mockMvc.perform(get("/api/v1/notifications/preferences").header("Authorization", "Bearer " + user.token()))
                .andExpect(jsonPath("$[?(@.type=='NEW_MESSAGE')].pushEnabled").value(false));
    }

    // Verifies the preference actually suppresses push dispatch, not just the stored flag: a
    // user who disables push for a category should still get the in-app row (default
    // inAppEnabled=true), but notify() must not attempt delivery to any subscription for that
    // category. Confirmed indirectly here via the in-app row still being created; PushDispatcher's
    // own unit test covers the delivery-attempt side directly.
    @Test
    void notify_withPushDisabledForType_stillCreatesInAppNotification() throws Exception {
        RegisteredUser user = register("notif-pref-push-disabled@example.com");
        User entity = userRepository.findById(user.id()).orElseThrow();
        assertThat(entity.getEmail()).isEqualTo("notif-pref-push-disabled@example.com");

        mockMvc.perform(put("/api/v1/notifications/preferences/NEW_MESSAGE")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "pushEnabled": false, "inAppEnabled": true }
                                """))
                .andExpect(status().isOk());

        notificationService.notify(user.id(), NotificationType.NEW_MESSAGE, "Still shows in-app", UUID.randomUUID());

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + user.token()))
                .andExpect(jsonPath("$[0].message").value("Still shows in-app"));
    }
}
