package org.zanata.email;

import static com.google.common.base.Charsets.UTF_8;
import static javax.mail.Message.RecipientType.BCC;

import java.io.Serializable;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import com.oath.cyclops.types.persistent.PersistentMap;
import cyclops.data.HashMap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.log.CommonsLogLogChute;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import javax.inject.Inject;
import javax.inject.Named;

import org.zanata.ApplicationConfiguration;
import org.zanata.i18n.Messages;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import org.zanata.i18n.MessagesFactory;
import org.zanata.servlet.annotations.ServerPath;
import org.zanata.util.HtmlUtil;

/**
 * Uses a VelocityEmailStrategy to build an email from a Velocity
 * template and send it via the default JavaMail Transport.
 */
@Named("emailBuilder")
@javax.enterprise.context.Dependent
public class EmailBuilder implements Serializable {
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(EmailBuilder.class);
    public static final String MAIL_SESSION_JNDI = "mail/Default";
    // Use this if you want emails logged on stderr
    // Warning: The full message may contain sensitive information
    private static final boolean LOG_FULL_MESSAGES = false;
    private static final VelocityEngine velocityEngine = makeVelocityEngine();
    private static final long serialVersionUID = 7906997500076971623L;

    public EmailBuilder() {
    }

    @SuppressFBWarnings("SE_BAD_FIELD")
    @Resource(name = MAIL_SESSION_JNDI)
    private Session mailSession;
    @SuppressFBWarnings("SE_BAD_FIELD")
    @Inject
    private Context emailContext;
    @Inject
    private MessagesFactory messagesFactory;

    private static VelocityEngine makeVelocityEngine() {
        VelocityEngine ve = new VelocityEngine();
        ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        ve.setProperty("classpath.resource.loader.class",
                ClasspathResourceLoader.class.getName());
        // send Velocity log to SLF4J (via Commons Logging)
        ve.setProperty(VelocityEngine.RUNTIME_LOG_LOGSYSTEM_CLASS,
                CommonsLogLogChute.class.getName());
        // this allows unit tests to detect missing context vars:
        ve.setProperty("runtime.references.strict", true);
        ve.init();
        return ve;
    }

    /**
     * Build message using 'strategy' and send it via Transport to
     * 'toAddresses'.
     *
     * @param strategy
     */
    public void sendMessage(VelocityEmailStrategy strategy,
            List<String> receivedReasons, InternetAddress... toAddresses) {
        SendEmailKt.sendEmail(() -> {
            MimeMessage email = new MimeMessage(mailSession);
            return buildMessage(email, strategy, toAddresses, receivedReasons);
        });
    }

    /**
     * Fills in the provided MimeMessage 'msg' using 'strategy' to select the
     * desired body template and to provide context variable values. Does not
     * actually send the email.
     *
     * @param msg
     * @param strategy
     * @return
     * @throws javax.mail.MessagingException
     */
    @VisibleForTesting
    MimeMessage buildMessage(MimeMessage msg, VelocityEmailStrategy strategy,
            InternetAddress[] toAddresses, List<String> receivedReasons)
            throws MessagingException {
        // TODO remember users' locales, and customise for each recipient
        // msgs = messagesFactory.getMessages(account.getLocale());
        Messages msgs = messagesFactory.getDefaultLocaleMessages();
        Optional<InternetAddress> from = strategy.getFromAddress();
        String fromName = msgs.get("jsf.Zanata");
        msg.setFrom(from.or(
                Addresses.getAddress(emailContext.getFromAddress(), fromName)));
        Optional<InternetAddress[]> replyTo = strategy.getReplyToAddress();
        if (replyTo.isPresent()) {
            msg.setReplyTo(replyTo.get());
        }
        msg.addRecipients(BCC, toAddresses);
        msg.setSubject(strategy.getSubject(msgs), UTF_8.name());
        // optional future extension
        // strategy.setMailHeaders(msg, msgs);
        PersistentMap<String, Object> genericContext = HashMap
                .<String, Object>empty()
                .put("msgs", msgs)
                .put("receivedReasons", receivedReasons)
                .put("serverPath", emailContext.getServerPath());
        // NB contextMap needs to be mutable for "foreach" to work
        Map<String, Object> contextMap = new java.util.HashMap<>(
                strategy.makeContext(genericContext, toAddresses).mapView());
        VelocityContext context = new VelocityContext(contextMap);
        Template template =
                velocityEngine.getTemplate(strategy.getTemplateResourceName());
        StringWriter writer = new StringWriter();
        template.merge(context, writer);
        String body = writer.toString();
        // Alternative parts should be added in increasing order of preference,
        // ie the preferred format should be added last.
        Multipart mp = new MimeMultipart("alternative");
        MimeBodyPart textPart = new MimeBodyPart();
        String text = HtmlUtil.htmlToText(body);
        textPart.setText(text, "UTF-8");
        mp.addBodyPart(textPart);
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(body, "text/html; charset=UTF-8");
        mp.addBodyPart(htmlPart);
        msg.setContent(mp);
        return msg;
    }

    /**
     * A Seam component which can inject the required configuration and
     * components needed to create EmailBuilder at runtime.
     */
    @Named("emailContext")
    @javax.enterprise.context.RequestScoped
    public static class Context {

        @Inject
        @ServerPath
        String serverPath;
        @Inject
        private ApplicationConfiguration applicationConfiguration;

        String getServerPath() {
            return this.serverPath;
        }

        String getFromAddress() {
            return applicationConfiguration.getFromEmailAddr();
        }
    }

    @java.beans.ConstructorProperties({ "mailSession", "emailContext",
            "messagesFactory" })
    public EmailBuilder(final Session mailSession, final Context emailContext,
            final MessagesFactory messagesFactory) {
        this.mailSession = mailSession;
        this.emailContext = emailContext;
        this.messagesFactory = messagesFactory;
    }
}
