package com.mindhub.homebanking.service;

import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.Notification;
import com.mindhub.homebanking.dto.AlertSettingsDTO;
import com.mindhub.homebanking.dto.NotificationDTO;
import com.mindhub.homebanking.dto.PageDTO;
import com.mindhub.homebanking.exception.ResourceNotFoundException;
import com.mindhub.homebanking.repository.ClientRepository;
import com.mindhub.homebanking.repository.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** The caller's inbox (read side of the notifications) and alert preferences. Others' entries answer 404. */
@Service
@Transactional(readOnly = true)
public class InboxService {

    public static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notifications;
    private final ClientRepository clientRepository;
    private final Clock clock;

    public InboxService(NotificationRepository notifications, ClientRepository clientRepository, Clock clock) {
        this.notifications = notifications;
        this.clientRepository = clientRepository;
        this.clock = clock;
    }

    /** Newest first. */
    public PageDTO<NotificationDTO> page(String email, int page, int size) {
        return PageDTO.of(notifications.findByClientOrderByCreatedAtDescIdDesc(client(email),
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE))).map(InboxService::toDto));
    }

    public long unread(String email) {
        return notifications.countByClientAndReadAtIsNull(client(email));
    }

    @Transactional
    public void markRead(String email, Long id) {
        notifications.findByIdAndClient(id, client(email))
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"))
                .markRead(LocalDateTime.now(clock));
    }

    @Transactional
    public void markAllRead(String email) {
        notifications.markAllRead(client(email), LocalDateTime.now(clock));
    }

    public AlertSettingsDTO alerts(String email) {
        return toDto(client(email));
    }

    @Transactional
    public AlertSettingsDTO updateAlerts(String email, AlertSettingsDTO settings) {
        Client client = client(email);
        client.getAlerts().update(settings.lowBalanceThreshold(), settings.largeMovementThreshold(),
                settings.loginAlerts(), settings.emailAlerts());
        return toDto(client);
    }

    private Client client(String email) {
        return clientRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
    }

    private static AlertSettingsDTO toDto(Client client) {
        var alerts = client.getAlerts();
        return new AlertSettingsDTO(alerts.getLowBalance(), alerts.getLargeMovement(), alerts.isLogin(),
                alerts.isEmail());
    }

    private static NotificationDTO toDto(Notification n) {
        return new NotificationDTO(n.getId(), n.getType().name(), n.getTitle(), n.getMessage(), n.getLink(),
                n.getCreatedAt(), n.getReadAt() != null);
    }
}
