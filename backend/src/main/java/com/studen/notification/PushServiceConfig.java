package com.studen.notification;

import java.security.GeneralSecurityException;
import java.security.Security;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PushServiceConfig {

    public PushServiceConfig() {
        // Registered once at startup, before anything attempts to build the PushService bean
        // below — web-push's VAPID/payload-encryption code needs the "BC" provider present in
        // the JVM's security provider list to resolve EC key operations.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    // A single shared instance, reused across every send — constructing it parses/validates the
    // VAPID keypair, which only needs to happen once, not per notification.
    @Bean
    public PushService pushService(VapidProperties vapidProperties) throws GeneralSecurityException {
        return new PushService(vapidProperties.getPublicKey(), vapidProperties.getPrivateKey(),
                vapidProperties.getSubject());
    }
}
