package bg.parkingprognoza.service;

import bg.parkingprognoza.config.AppConfig;
import bg.parkingprognoza.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

/**
 * Sends HTML mails rendered from templates/mail/*. Without SMTP settings
 * (spring.mail.host) mails are only written to the log, which is handy locally.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final TemplateEngine templates;
    private final AppProperties props;
    private final boolean smtpConfigured;

    public MailService(ObjectProvider<JavaMailSender> sender, TemplateEngine templates, AppProperties props,
                       @Value("${spring.mail.host:}") String smtpHost) {
        this.sender = sender;
        this.templates = templates;
        this.props = props;
        this.smtpConfigured = !smtpHost.isBlank();
    }

    /**
     * @param unsubscribeUrl if set, adds List-Unsubscribe headers so mail clients show an unsubscribe button
     */
    public void send(String to, String subject, String template, Map<String, Object> model, String unsubscribeUrl) {
        Context ctx = new Context(AppConfig.BG);
        ctx.setVariables(model);
        ctx.setVariable("baseUrl", props.trimmedBaseUrl());
        String html = templates.process("mail/" + template, ctx);

        JavaMailSender mailSender = smtpConfigured ? sender.getIfAvailable() : null;
        if (mailSender == null) {
            log.info("SMTP not configured, mail not sent.\nTo: {}\nSubject: {}\n{}", to, subject, html);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(props.mail().from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            if (unsubscribeUrl != null) {
                message.setHeader("List-Unsubscribe", "<" + unsubscribeUrl + ">");
                message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            }
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("Could not build mail to " + to, e);
        }
    }
}
