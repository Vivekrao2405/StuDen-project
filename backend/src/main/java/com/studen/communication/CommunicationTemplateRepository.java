package com.studen.communication;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunicationTemplateRepository extends JpaRepository<CommunicationTemplate, UUID> {

    List<CommunicationTemplate> findAllByArchivedFalseOrderByCreatedAtDesc();

    List<CommunicationTemplate> findAllByOrderByCreatedAtDesc();
}
