package com.studen.practical.judge;

/**
 * Isolated SQL sandbox -- structurally separate from {@link CodeExecutionService} (a SQL question
 * has no compile step and is graded by diffing a result set, not stdout). Every {@link #run} call
 * seeds a brand-new, network-disabled, throwaway Postgres instance, runs the student's query and
 * the admin's reference query against it, and destroys the whole instance afterward. Never runs
 * against the application's real database.
 */
public interface SqlExecutionService {

    boolean isAvailable();

    /**
     * @param seedScript      admin-authored setup SQL (INSERT/DDL), applied once before either query
     * @param studentQuery    raw student SQL text -- validated as a single SELECT before ever reaching
     *                        a container; a validation failure returns {@link SqlRunOutcome#rejected}
     * @param referenceQuery  admin-authored trusted reference query used to compute the expected result
     */
    SqlRunOutcome run(String seedScript, String studentQuery, String referenceQuery, int timeoutSeconds);
}
