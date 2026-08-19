package com.studen.communication;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// First and only @Scheduled usage in the app — powers CampaignScheduler's poll for due
// SCHEDULED campaigns. No other scheduled/cron job exists anywhere else in the codebase.
@Configuration
@EnableScheduling
public class CommunicationSchedulingConfig {
}
