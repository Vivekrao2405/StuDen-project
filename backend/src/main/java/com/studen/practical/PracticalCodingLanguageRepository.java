package com.studen.practical;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticalCodingLanguageRepository extends JpaRepository<PracticalCodingLanguage, UUID> {

    List<PracticalCodingLanguage> findAllByPracticalQuestionIdOrderByLanguageAsc(UUID practicalQuestionId);

    void deleteAllByPracticalQuestionId(UUID practicalQuestionId);
}
