package com.studen.practical.judge;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.execution", name = "enabled", havingValue = "false")
public class UnavailableSqlExecutionService implements SqlExecutionService {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public SqlRunOutcome run(String seedScript, String studentQuery, String referenceQuery, int timeoutSeconds) {
        throw new IllegalStateException("SQL execution is not available in this environment");
    }
}
