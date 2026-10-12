package tech.tetengo.api.hogares.application;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Deletes objects from the store only once the transaction commits, so a rolled-back change never
 * points at a deleted photo; right away without a transaction.
 */
final class TrasConfirmar {

    private TrasConfirmar() {}

    static void ejecutar(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }
}
