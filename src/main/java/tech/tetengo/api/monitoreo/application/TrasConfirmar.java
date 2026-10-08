package tech.tetengo.api.monitoreo.application;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs side effects outside the database (messages to the agent, MediaMTX calls) once the transaction
 * commits, so MediaMTX never asks for a token that is not stored yet; right away without a transaction.
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
