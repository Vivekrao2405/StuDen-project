package com.studen.questionbank;

import java.util.List;

public record ImportParseResponse(
        String fileName,
        int totalDetected,
        int validCount,
        int errorCount,
        List<ImportedQuestionDraft> questions) {

    public static ImportParseResponse of(String fileName, List<ImportedQuestionDraft> questions) {
        long validCount = questions.stream().filter(ImportedQuestionDraft::valid).count();
        return new ImportParseResponse(fileName, questions.size(), (int) validCount,
                questions.size() - (int) validCount, questions);
    }
}
