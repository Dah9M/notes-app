package com.notesapp.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Process-local event counters, exported even before the first operation. */
@Component
public class BusinessMetrics {

    private final Counter registrations;
    private final Counter logins;
    private final Counter notesCreated;
    private final Counter notesUpdated;
    private final Counter notesDeleted;

    public BusinessMetrics(MeterRegistry registry) {
        registrations = counter(registry, "notesapp.registrations", "Successfully registered users");
        logins = counter(registry, "notesapp.logins", "Successful credential checks");
        notesCreated = counter(registry, "notesapp.notes.creations", "Successfully created notes");
        notesUpdated = counter(registry, "notesapp.notes.updated", "Successful note update operations");
        notesDeleted = counter(registry, "notesapp.notes.deleted", "Successfully deleted notes");
    }

    public void registrationSucceeded() {
        incrementAfterCommit(registrations);
    }

    public void loginSucceeded() {
        incrementAfterCommit(logins);
    }

    public void noteCreated() {
        incrementAfterCommit(notesCreated);
    }

    public void noteUpdated() {
        incrementAfterCommit(notesUpdated);
    }

    public void noteDeleted() {
        incrementAfterCommit(notesDeleted);
    }

    private static Counter counter(MeterRegistry registry, String name, String description) {
        return Counter.builder(name).description(description).register(registry);
    }

    private static void incrementAfterCommit(Counter counter) {
        // Repository methods normally commit before returning. If the caller has
        // an enclosing transaction, count only its successful commit, not rollback.
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    counter.increment();
                }
            });
        } else {
            counter.increment();
        }
    }
}
