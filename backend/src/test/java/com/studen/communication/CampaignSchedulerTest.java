package com.studen.communication;

import static org.assertj.core.api.Assertions.assertThat;

import com.studen.user.User;
import com.studen.user.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled-campaign idempotency (spec: "a scheduler retry must NOT send the same campaign
 * twice"). Calls {@link CampaignScheduler#processDueCampaigns()} directly rather than waiting for
 * the real one-minute {@code @Scheduled} tick — the method itself is what a real tick invokes, so
 * calling it twice in a row is exactly equivalent to two overlapping/retried ticks.
 */
@SpringBootTest
@Transactional
@Import(CommunicationTestSupport.class)
@TestPropertySource(properties = "app.communication.async.enabled=false")
class CampaignSchedulerTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommunicationCampaignRepository campaignRepository;

    @Autowired
    private CommunicationRecipientRepository recipientRepository;

    @Autowired
    private CampaignScheduler campaignScheduler;

    @Autowired
    private FakeEmailService fakeEmailService;

    // See AdminCommunicationCampaignControllerTest's identical @BeforeEach for why this is needed
    // — FakeEmailService is a shared singleton, not reset by @Transactional rollback.
    @BeforeEach
    void resetFakeEmailService() {
        fakeEmailService.reset();
    }

    @Test
    void claimForProcessing_secondAttempt_returnsZero_notOne() {
        User admin = userRepository.save(new User("Sched Admin", "sched-claim-" + System.nanoTime() + "@example.com",
                "hash"));
        CommunicationCampaign campaign = new CommunicationCampaign("Claim test", CommunicationCategory.CUSTOM, "{}",
                admin);
        campaign.setStatus(CampaignStatus.SCHEDULED);
        campaign.setScheduledAt(Instant.now().minusSeconds(5));
        campaign = campaignRepository.save(campaign);

        int firstClaim = campaignRepository.claimForProcessing(campaign.getId(), Instant.now());
        int secondClaim = campaignRepository.claimForProcessing(campaign.getId(), Instant.now());

        assertThat(firstClaim).isEqualTo(1);
        assertThat(secondClaim).isEqualTo(0);
    }

    @Test
    void processDueCampaigns_calledTwice_neverDoubleSends() {
        User user = userRepository.save(new User("Sched Student", "sched-student-" + System.nanoTime() + "@example.com",
                "hash"));
        String filter = "{\"field\":\"USER_SPECIFIC_IDS\",\"params\":{\"userIds\":\"" + user.getId() + "\"}}";
        CommunicationCampaign campaign = new CommunicationCampaign("Due campaign", CommunicationCategory.CUSTOM, filter,
                user);
        campaign.setSendEmail(true);
        campaign.setEmailSubject("Subject");
        campaign.setEmailBodyHtml("Body");
        campaign.setStatus(CampaignStatus.SCHEDULED);
        campaign.setScheduledAt(Instant.now().minusSeconds(5));
        campaign = campaignRepository.save(campaign);

        campaignScheduler.processDueCampaigns();
        campaignScheduler.processDueCampaigns();

        assertThat(fakeEmailService.sent()).hasSize(1);
        assertThat(recipientRepository.countByCampaignId(campaign.getId())).isEqualTo(1);

        CommunicationCampaign reloaded = campaignRepository.findById(campaign.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CampaignStatus.SENT);
    }
}
