package com.studen.messaging;

import java.util.UUID;

public interface UnreadCountProjection {
    UUID getConversationId();

    long getCount();
}
