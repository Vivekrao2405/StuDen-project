package com.studen.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studen.user.User;
import com.studen.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the V24 unique constraint on (campaign_id, user_id, channel) directly — this is the
 * entire duplicate-prevention mechanism CampaignSendService relies on (see its javadoc): no
 * overlapping AND/OR filter, retry, or scheduler race can ever produce two rows for the same
 * student on the same channel of the same campaign.
 */
@SpringBootTest
@Transactional
class CommunicationRecipientRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommunicationCampaignRepository campaignRepository;

    @Autowired
    private CommunicationRecipientRepository recipientRepository;

    @Test
    void secondInsertForSameCampaignUserChannel_violatesUniqueConstraint() {
        User user = userRepository.save(new User("Dup Test", "dup-constraint-" + System.nanoTime() + "@example.com",
                "hash"));
        CommunicationCampaign campaign = campaignRepository
                .save(new CommunicationCampaign("Dup test", CommunicationCategory.CUSTOM, "{}", user));

        recipientRepository.saveAndFlush(new CommunicationRecipient(campaign, user, RecipientChannel.EMAIL, user.getEmail()));

        // Postgres aborts the whole transaction on the first failed statement (every later
        // statement errors with "current transaction is aborted" until rollback) — this method's
        // @Transactional rollback-at-teardown handles that, but it also means no further query
        // (e.g. a recipient count) can run in this same test after the violation is thrown.
        assertThatThrownBy(() -> recipientRepository
                .saveAndFlush(new CommunicationRecipient(campaign, user, RecipientChannel.EMAIL, user.getEmail())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void differentChannelsForSameCampaignUser_areBothAllowed() {
        User user = userRepository.save(new User("Multi Channel", "multi-channel-" + System.nanoTime() + "@example.com",
                "hash"));
        CommunicationCampaign campaign = campaignRepository
                .save(new CommunicationCampaign("Multi channel test", CommunicationCategory.CUSTOM, "{}", user));

        recipientRepository.saveAndFlush(new CommunicationRecipient(campaign, user, RecipientChannel.EMAIL, user.getEmail()));
        recipientRepository.saveAndFlush(new CommunicationRecipient(campaign, user, RecipientChannel.PUSH, null));
        recipientRepository.saveAndFlush(new CommunicationRecipient(campaign, user, RecipientChannel.INAPP, null));

        assertThat(recipientRepository.countByCampaignId(campaign.getId())).isEqualTo(3);
    }
}
