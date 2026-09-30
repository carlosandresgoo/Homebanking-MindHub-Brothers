package com.mindhub.homebanking.service;

import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.AlertPreferences;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.MovementRecorded;
import com.mindhub.homebanking.domain.Transaction;
import com.mindhub.homebanking.domain.TransactionType;
import com.mindhub.homebanking.service.notification.NotificationService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Low-balance and large-movement alerts, checked for every debit whatever its origin (transfer,
 * fixed term, loan installment…). Runs inside the transaction that moved the money, so an operation
 * that rolls back leaves no alert behind.
 */
@Component
public class MovementAlerts {

    private final NotificationService notifications;

    public MovementAlerts(NotificationService notifications) {
        this.notifications = notifications;
    }

    @EventListener
    public void onMovement(MovementRecorded event) {
        Transaction movement = event.transaction();
        if (movement.getType() != TransactionType.DEBIT) {
            return;
        }
        Account account = movement.getAccount();
        Client client = account.getClient();
        AlertPreferences alerts = client.getAlerts();

        BigDecimal large = alerts.getLargeMovement();
        if (large != null && movement.getAmount().compareTo(large) >= 0) {
            notifications.largeMovement(client, movement, large);
        }
        // Only when this debit crosses the threshold: later debits below it do not repeat the alert.
        BigDecimal low = alerts.getLowBalance();
        BigDecimal before = movement.getBalanceAfter().add(movement.getAmount());
        if (low != null && movement.getBalanceAfter().compareTo(low) < 0 && before.compareTo(low) >= 0) {
            notifications.lowBalance(client, account, movement.getBalanceAfter(), low);
        }
    }
}
