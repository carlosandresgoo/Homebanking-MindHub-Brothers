package com.mindhub.homebanking.service.notification;

import com.mindhub.homebanking.config.SecurityProperties;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Prepara el body HTML de los e-mails usando templates Thymeleaf.
 * Cada template se encuentra en src/main/resources/mail-templates/
 */
@Service
public class EmailTemplateService {

	private final TemplateEngine templateEngine;
	private final String frontendUrl;
	private static final Locale ES_AR = new Locale("es", "AR");
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", ES_AR);
	private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", ES_AR);

	public EmailTemplateService(TemplateEngine templateEngine, SecurityProperties properties) {
		this.templateEngine = templateEngine;
		this.frontendUrl = properties.frontendUrl();
	}

	/** Recuperación de contraseña: link con token válido 24 horas. */
	public String passwordReset(String clientName, String resetLink) {
		Context ctx = new Context(ES_AR);
		ctx.setVariable("clientName", clientName);
		ctx.setVariable("resetLink", resetLink);
		ctx.setVariable("expirationHours", 24);
		ctx.setVariable("supportEmail", "soporte@mindhubbrothers.com");
		return templateEngine.process("password-reset", ctx);
	}

	/** Confirmación de cambio de contraseña. */
	public String passwordChanged(String clientName) {
		Context ctx = new Context(ES_AR);
		ctx.setVariable("clientName", clientName);
		ctx.setVariable("timestamp", LocalDateTime.now().format(DATETIME_FORMAT));
		ctx.setVariable("securityUrl", frontendUrl + "/profile");
		return templateEngine.process("password-changed", ctx);
	}

	/** Confirmación de 2FA habilitado. */
	public String twoFactorEnabled(String clientName) {
		Context ctx = new Context(ES_AR);
		ctx.setVariable("clientName", clientName);
		ctx.setVariable("timestamp", LocalDateTime.now().format(DATETIME_FORMAT));
		ctx.setVariable("securityUrl", frontendUrl + "/profile");
		return templateEngine.process("2fa-enabled", ctx);
	}

	/** Confirmación de 2FA deshabilitado (por el usuario). */
	public String twoFactorDisabled(String clientName) {
		Context ctx = new Context(ES_AR);
		ctx.setVariable("clientName", clientName);
		ctx.setVariable("timestamp", LocalDateTime.now().format(DATETIME_FORMAT));
		ctx.setVariable("securityUrl", frontendUrl + "/profile");
		return templateEngine.process("2fa-disabled", ctx);
	}

	/** Notificación de transferencia enviada. */
	public String transferSent(String clientName, String targetAccountNumber, BigDecimal amount, String description) {
		Context ctx = new Context(ES_AR);
		ctx.setVariable("clientName", clientName);
		ctx.setVariable("targetAccount", maskAccount(targetAccountNumber));
		ctx.setVariable("amount", formatCurrency(amount));
		ctx.setVariable("description", description != null ? description : "");
		ctx.setVariable("timestamp", LocalDateTime.now().format(DATETIME_FORMAT));
		ctx.setVariable("accountsUrl", frontendUrl + "/accounts");
		return templateEngine.process("transfer-sent", ctx);
	}

	/** Notificación de transferencia recibida. */
	public String transferReceived(String clientName, String sourceAccountNumber, BigDecimal amount, String description) {
		Context ctx = new Context(ES_AR);
		ctx.setVariable("clientName", clientName);
		ctx.setVariable("sourceAccount", maskAccount(sourceAccountNumber));
		ctx.setVariable("amount", formatCurrency(amount));
		ctx.setVariable("description", description != null ? description : "");
		ctx.setVariable("timestamp", LocalDateTime.now().format(DATETIME_FORMAT));
		ctx.setVariable("accountsUrl", frontendUrl + "/accounts");
		return templateEngine.process("transfer-received", ctx);
	}

	/** Notificación de plazo fijo creado. */
	public String fixedTermCreated(String clientName, BigDecimal principal, int termDays, BigDecimal interestRate, LocalDate maturityDate) {
		Context ctx = new Context(ES_AR);
		ctx.setVariable("clientName", clientName);
		ctx.setVariable("principal", formatCurrency(principal));
		ctx.setVariable("termDays", termDays);
		ctx.setVariable("annualRate", String.format("%.2f%%", interestRate.doubleValue()));
		ctx.setVariable("maturityDate", maturityDate.format(DATE_FORMAT));
		ctx.setVariable("investmentsUrl", frontendUrl + "/investments");
		return templateEngine.process("fixed-term-created", ctx);
	}

	/** Notificación de plazo fijo vencido y pagado. */
	public String fixedTermMatured(String clientName, BigDecimal principal, BigDecimal interest, BigDecimal total) {
		Context ctx = new Context(ES_AR);
		ctx.setVariable("clientName", clientName);
		ctx.setVariable("principal", formatCurrency(principal));
		ctx.setVariable("interest", formatCurrency(interest));
		ctx.setVariable("total", formatCurrency(total));
		ctx.setVariable("timestamp", LocalDateTime.now().format(DATETIME_FORMAT));
		ctx.setVariable("accountsUrl", frontendUrl + "/accounts");
		return templateEngine.process("fixed-term-matured", ctx);
	}

	/** Notificación de cuota de préstamo próxima a vencer. */
	public String loanPaymentReminder(String clientName, String loanType, BigDecimal installmentAmount, LocalDate dueDate) {
		Context ctx = new Context(ES_AR);
		ctx.setVariable("clientName", clientName);
		ctx.setVariable("loanType", loanType);
		ctx.setVariable("amount", formatCurrency(installmentAmount));
		ctx.setVariable("dueDate", dueDate.format(DATE_FORMAT));
		ctx.setVariable("loansUrl", frontendUrl + "/loans");
		return templateEngine.process("loan-payment-reminder", ctx);
	}

	/** Notificación de cuota de préstamo pagada. */
	public String loanPaymentConfirmed(String clientName, String loanType, BigDecimal amount, int remainingInstallments) {
		Context ctx = new Context(ES_AR);
		ctx.setVariable("clientName", clientName);
		ctx.setVariable("loanType", loanType);
		ctx.setVariable("amount", formatCurrency(amount));
		ctx.setVariable("remainingInstallments", remainingInstallments);
		ctx.setVariable("timestamp", LocalDateTime.now().format(DATETIME_FORMAT));
		ctx.setVariable("loansUrl", frontendUrl + "/loans");
		return templateEngine.process("loan-payment-confirmed", ctx);
	}

	/** Bienvenida a nuevo cliente. */
	public String welcomeNewClient(String clientName) {
		Context ctx = new Context(ES_AR);
		ctx.setVariable("clientName", clientName);
		ctx.setVariable("accountsUrl", frontendUrl + "/accounts");
		ctx.setVariable("profileUrl", frontendUrl + "/profile");
		return templateEngine.process("welcome", ctx);
	}

	private static String maskAccount(String accountNumber) {
		// Mostrar solo los últimos 4 dígitos: VIN001 → VIN...01
		if (accountNumber == null || accountNumber.length() < 4) return accountNumber;
		return accountNumber.substring(0, 3) + "..." + accountNumber.substring(accountNumber.length() - 2);
	}

	private static String formatCurrency(BigDecimal amount) {
		return String.format("$%,.2f", amount);
	}
}
