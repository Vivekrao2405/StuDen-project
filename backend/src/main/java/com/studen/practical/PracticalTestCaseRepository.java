package com.studen.practical;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticalTestCaseRepository extends JpaRepository<PracticalTestCase, UUID> {

    // Every caller — admin (sees all) and student-facing DTO mapping (filters hidden==true rows
    // out before serialization) — starts from this same full list, so there's exactly one place
    // ("does this response include hidden rows?") to audit for leaks, rather than two divergent
    // queries.
    List<PracticalTestCase> findAllByPracticalQuestionIdOrderByDisplayOrderAsc(UUID practicalQuestionId);

    List<PracticalTestCase> findAllByPracticalQuestionIdIn(List<UUID> practicalQuestionIds);

    void deleteAllByPracticalQuestionId(UUID practicalQuestionId);
}
