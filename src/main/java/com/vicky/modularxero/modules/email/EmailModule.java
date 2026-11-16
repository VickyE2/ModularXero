package com.vicky.modularxero.modules.email;

import com.vicky.modularxero.*;
import com.vicky.modularxero.common.Logger.ANSIColor;
import com.vicky.modularxero.common.MapValueBuilder;
import com.vicky.modularxero.common.ResponseHelper;
import com.vicky.modularxero.common.values.MapValue;
import com.vicky.modularxero.common.values.MessageValue;
import com.vicky.modularxero.common.values.StringValue;
import com.vicky.modularxero.email.EmailTemplate;
import com.vicky.modularxero.email.ReplacementValue;
import com.vicky.modularxero.modules.email.dao.EmailVerificationDao;
import com.vicky.modularxero.modules.email.dao.SavedEmailDao;
import jakarta.mail.internet.InternetAddress;
import jakarta.persistence.*;
import kotlin.Pair;
import kotlin.coroutines.Continuation;
import kotlin.jvm.JvmClassMappingKt;
import org.hibernate.annotations.NaturalId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.vicky.modularxero.UtilsKt.eitherIsNull;
import static com.vicky.modularxero.UtilsKt.whichAreNull;
import static com.vicky.modularxero.common.GsonKt.toMap;
import static com.vicky.modularxero.email.EmailHelperKt.*;

public class EmailModule extends AbstractSystemModule {
    public static final ModuleMetrics emailMetrics = new ModuleMetrics();
    public static boolean isActive = true;

    public EmailModule() {
        super(DefaultServices.EMAIL_SERVICE.getId(),
                "A system service module for sending and processing email related data.");
    }
    public EmailVerificationDao emailVerificationDao;
    public SavedEmailDao savedEmailDao;

    public enum Services {
        SEND_MAIL("send_mail"),
        GET_VERIFICATION_CODE("get_email_code"),
        SAVE_EMAIL_VERIFICATION_CODE("save_email_code"),
        PING("ping");

        public final String code;
        Services(String code) {
            this.code = code;
        }
    }

    public enum CodeReason {
        EXPIRED, NO_CODE
    }

    @Override
    public void init(@NotNull ModularXeroDispatcher dispatcher) {
        super.init(dispatcher);
        emailVerificationDao = new EmailVerificationDao(sessionFactory());
        savedEmailDao = new SavedEmailDao(sessionFactory());

        getLogger().toast(ANSIColor.gradientColorize("""
                gradient-12deg-#00AAEE-#2288BB-#FF6600[
                                                          ,----,
                                                        ,/   .`|                                                  ____
                    ,---,.   ,---,       .--.--.      ,`   .'  :   ,---,.,-.----.                 ,---,.        ,'  , `.
                  ,'  .' |  '  .' \\     /  /    '.  ;    ;     / ,'  .' |\\    /  \\              ,'  .' |     ,-+-,.' _ |
                ,---.'   | /  ;    '.  |  :  /`. /.'___,/    ,',---.'   |;   :    \\     ,---,.,---.'   |  ,-+-. ;   , ||
                |   |   .':  :       \\ ;  |  |--` |    :     | |   |   .'|   | .\\ :   ,'  .' ||   |   .' ,--.'|'   |  ;|
                :   :  :  :  |   /\\   \\|  :  ;_   ;    |.';  ; :   :  |-,.   : |: | ,---.'   ,:   :  |-,|   |  ,', |  ':
                :   |  |-,|  :  ' ;.   :\\  \\    `.`----'  |  | :   |  ;/||   |  \\ : |   |    |:   |  ;/||   | /  | |  ||
                |   :  ;/||  |  ;/  \\   \\`----.   \\   '   :  ; |   :   .'|   : .  / :   :  .' |   :   .''   | :  | :  |,
                |   |   .''  :  | \\  \\ ,'__ \\  \\  |   |   |  ' |   |  |-,;   | |  \\ :   |.'   |   |  |-,;   . |  ; |--'
                '   :  '  |  |  '  '--' /  /`--'  /   '   :  | '   :  ;/||   | ;\\  \\`---'     '   :  ;/||   : |  | ,
                |   |  |  |  :  :      '--'.     /    ;   |.'  |   |    \\:   ' | \\.'          |   |    \\|   : '  |/
                |   :  \\  |  | ,'        `--'---'     '---'    |   :   .':   : :-'            |   :   .';   | |`-'
                |   | ,'  `--''                                |   | ,'  |   |.'              |   | ,'  |   ;/
                `----'                                         `----'    `---'                `----'    '---'
                ]"""));
    }

    @Override
    public void destroy() {
        isActive = false;
    }

    @Override
    public @NotNull ModuleMetrics getMetrics() {
        return emailMetrics;
    }

    @Override
    public boolean getShouldSaveLog() {
        return true;
    }

    @Override
    public @NotNull List<Class<?>> getModuleAnnotatedClasses() {
        return List.of(
                EmailVerificationEntity.class,
                SentEmails.class
        );
    }

    private MapValue testDatabase() {
        long start = System.nanoTime();
        try (var session = sessionFactory().openSession()) {
            session.createNativeQuery("SELECT 1").getSingleResult();
            long latency = (System.nanoTime() - start) / 1_000_000;

            return new MapValueBuilder()
                    .put("ok", true)
                    .put("latency_ms", latency)
                    .build();
        }
        catch (Exception e) {
            return new MapValueBuilder()
                    .put("ok", false)
                    .put("error", e.getMessage())
                    .build();
        }
    }
    private MapValue testEmailDao() {
        try {
            savedEmailDao.findAll().size(); // or existsById(UUID.randomUUID())
            return new MapValueBuilder()
                    .put("ok", true)
                    .build();
        }
        catch (Exception e) {
            return new MapValueBuilder()
                    .put("ok", false)
                    .put("error", e.getMessage())
                    .build();
        }
    }
    private MapValue testSmtp() {
        try {
            testConnection(
                    "localhost",
                    1025,
                    null, null
            );
            return new MapValueBuilder().put("ok", true).build();
        }
        catch (Exception e) {
            return new MapValueBuilder()
                    .put("ok", false)
                    .put("error", e.getMessage())
                    .build();
        }
    }

    @Override
    public @NotNull Message handle(@NotNull Message msg) {

        getLogger().print("[RECEIVED] type=" + msg.getType() +
                " from=" + msg.getFrom(), false);

        if (!isActive) {
            getLogger().print("[ERROR] Service inactive. Rejecting: " + msg.getType(), true);
            return new Message(getName(), msg.getFrom(), "error",
                    new MapValueBuilder()
                            .put("reason", "Service is inactive")
                            .build());
        }

        // Extract correlation early for logs
        var corr = (StringValue) msg.getPayload().get().get("__corrId");

        if (msg.getType().equalsIgnoreCase("ping")) {

            var builder = new MapValueBuilder()
                    .put("reply", new StringValue("PONG"));

            if (corr != null) builder.put("__corrId", corr);

            var rawChecks = ResponseHelper.INSTANCE.getList(msg.getPayload(), "checks");

            Set<String> checksRequested = rawChecks == null
                    ? Set.of()
                    : rawChecks.stream()
                    .map(Object::toString) // or ((StringValue)v).get()
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());

            boolean runAll = checksRequested.isEmpty()
                    || checksRequested.contains("*");


            var checksResult = new MapValueBuilder();

            if (runAll || checksRequested.contains("database")) {
                checksResult.put("database", testDatabase());
            }
            if (runAll || checksRequested.contains("email_dao")) {
                checksResult.put("email_dao", testEmailDao());
            }
            if (runAll || checksRequested.contains("smtp")) {
                checksResult.put("smtp", testSmtp());
            }

            builder.put("checks", checksResult.build());

            return new Message(getName(), msg.getFrom(), "PONG", builder.build());
        }

        else if (msg.getType().equalsIgnoreCase("send_mail")) {

            getLogger().print("[MAIL] Processing send_mail request...", false);

            var system_name = ResponseHelper.INSTANCE.getString(msg.getPayload(), "system");
            var systemEmail = ResponseHelper.INSTANCE.getString(msg.getPayload(), "sender");
            var password = ResponseHelper.INSTANCE.getString(msg.getPayload(), "password");
            var receiverEmail = ResponseHelper.INSTANCE.getString(msg.getPayload(), "receiver");
            EmailTemplate emailTemplate = ResponseHelper.INSTANCE.getObject(msg.getPayload(), "template", JvmClassMappingKt.getKotlinClass(EmailTemplate.class));
            var subjectMap = ResponseHelper.INSTANCE.getMapValue(msg.getPayload(), "subject_map");
            var bodyMap = ResponseHelper.INSTANCE.getMapValue(msg.getPayload(), "body_map");

            var builder = new MapValueBuilder().put("reply", new StringValue("mail_result"));
            if (corr != null) builder.put("__corrId", corr);

            if (eitherIsNull(receiverEmail, emailTemplate, subjectMap, bodyMap)) {

                String missing = String.join(", ", whichAreNull(
                        new Pair<>("receiverEmail", receiverEmail),
                        new Pair<>("emailTemplate", emailTemplate),
                        new Pair<>("subjectMap", subjectMap),
                        new Pair<>("bodyMap", bodyMap)
                ));

                getLogger().print("[MAIL][ERROR] Missing params: " + missing, true);

                builder.put("was_sent", false)
                        .put("reason", "Missing: {" + missing + "}");

                return new Message(getName(), msg.getFrom(), "send_mail", builder.build());
            }

            getLogger().print("[MAIL] Attempting to send email to: " + receiverEmail, false);

            boolean emailSent;
            if (systemEmail == null || password == null) {
                try {
                    emailSent = sendEmail(toMap(bodyMap, ReplacementValue.Text::new),
                            toMap(subjectMap, ReplacementValue.Text::new),
                            emailTemplate, receiverEmail);
                }
                catch (Exception e) {
                    return new Message(getName(), msg.getFrom(), "error",
                            new MapValueBuilder()
                                    .put("reason", "Failed to deliver mail. Internal server error")
                                    .build());
                }
            }
            else {
                try {
                    emailSent = sendEmail(toMap(bodyMap, ReplacementValue.Text::new),
                            toMap(subjectMap, ReplacementValue.Text::new),
                            emailTemplate, receiverEmail,
                            systemEmail, password);
                }
                catch (Exception e) {
                    return new Message(getName(), msg.getFrom(), "error",
                            new MapValueBuilder()
                                    .put("reason", "Failed to deliver mail. Internal server error")
                                    .build());
                }
            }

            if (emailSent) {
                getLogger().print("[MAIL][SUCCESS] Delivered to " + receiverEmail, false);

                SentEmails sentEntity = new SentEmails(
                        receiverEmail, system_name,
                        emailTemplate.renderSubject(toMap(subjectMap, ReplacementValue.Text::new)) + "\n" + emailTemplate.renderBody(toMap(bodyMap, ReplacementValue.Text::new))
                );
                savedEmailDao.save(sentEntity);

                builder.put("was_sent", true)
                        .put("message", "The email was sent successfully");
            } else {
                getLogger().print("[MAIL][FAIL] Delivery failed for " + receiverEmail, true);

                builder.put("was_sent", false)
                        .put("reason", "Email failed to send. Invalid parameters?");
            }

            return new Message(getName(), msg.getFrom(), "send_mail", builder.build());
        }

        else if (msg.getType().equalsIgnoreCase("save_email_code")) {
            getLogger().print("[CODE] Saving email verification code...", false);

            var code = ResponseHelper.INSTANCE.getString(msg.getPayload(), "code");
            var email = ResponseHelper.INSTANCE.getString(msg.getPayload(), "email");
            var provider = ResponseHelper.INSTANCE.getString(msg.getPayload(), "provider");
            var duration = ResponseHelper.INSTANCE.getLong(msg.getPayload(), "duration");

            var builder = new MapValueBuilder()
                    .put("reply", new StringValue("email_code_saved"));

            if (corr != null) builder.put("__corrId", corr);

            if (eitherIsNull(email, code, duration)) {
                getLogger().print("[CODE][ERROR] Missing email, code or duration", true);
                return new Message(getName(), msg.getFrom(), "error",
                        new MapValueBuilder()
                                .put("reason", "Email, duration or code not passed")
                                .build());
            }

            EmailVerificationEntity entity = new EmailVerificationEntity(email, provider + "_" + msg.getFrom(), code, duration);
            emailVerificationDao.save(entity);

            getLogger().print("[CODE][SUCCESS] Code saved for " + email, false);

            return new Message(getName(), msg.getFrom(), "save_email_code", builder.build());
        }

        else if (msg.getType().equalsIgnoreCase("get_email_code")) {
            getLogger().print("[CODE] Fetching email verification code...", false);

            var email = ResponseHelper.INSTANCE.getString(msg.getPayload(), "email");
            var provider = ResponseHelper.INSTANCE.getString(msg.getPayload(), "provier");

            var builder = new MapValueBuilder()
                    .put("reply", new StringValue("email_code_result"));

            if (corr != null) builder.put("__corrId", corr);

            if (eitherIsNull(email, provider)) {
                getLogger().print("[CODE][ERROR] Email or Provider was null", true);
                return new Message(getName(), msg.getFrom(), "error",
                        new MapValueBuilder()
                                .put("reason", "Email or Provider was null...It is needed")
                                .build());
            }

            var verf = emailVerificationDao.findByEmail(email, provider + "_" + msg.getFrom());
            if (verf == null || !Objects.equals(verf.systemName, provider + "_" + msg.getFrom())) {
                getLogger().print("[CODE][ERROR] No code found for email: " + email, true);
                return new Message(getName(), msg.getFrom(), "error",
                        new MapValueBuilder()
                                .put("code_reason", CodeReason.NO_CODE)
                                .put("reason", "Email hasn't requested for code")
                                .build());
            }

            getLogger().print("[CODE][SUCCESS] Code retrieved for " + email, false);

            if (isExpired(verf)) {
                getLogger().print("[CODE][SUCCESS] Deleting expired code for " + email, false);
                emailVerificationDao.delete(verf);
                return new Message(getName(), msg.getFrom(), "error",
                        new MapValueBuilder()
                                .put("code_reason", CodeReason.EXPIRED)
                                .put("reason", "That email verification code is expired")
                                .build());
            }

            builder.put("code", verf.verificationCode);
            builder.put("is_expired", isExpired(verf));
            return new Message(getName(), msg.getFrom(), "get_email_code", builder.build());
        }

        getLogger().print("[ERROR] Unknown message type: " + msg.getType(), true);

        return new Message(getName(), msg.getFrom(), "error",
                new MapValueBuilder()
                        .put("reason", "Unknown message sent")
                        .build());
    }

    @Entity
    @Table(
        name = "email_verification",
        uniqueConstraints = @UniqueConstraint(columnNames = {"email", "system_name"})
    )
    public static class EmailVerificationEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @NaturalId
        @Column(name = "email")
        public String email;

        @NaturalId
        @Column(name = "system_name")
        public String systemName;

        @Column(name = "code")
        public String verificationCode;

        @Column(name = "created_at")
        private LocalDateTime createdAt;

        @Column(name = "duration")
        private Long duration;

        public EmailVerificationEntity() {}

        public EmailVerificationEntity(String email, String systemName, String verificationCode, Long duration) {
            this.email = email;
            this.systemName = systemName;
            this.verificationCode = verificationCode;
            this.createdAt = LocalDateTime.now();
            this.duration = duration;
        }

        public String getEmail() {
            return email;
        }

        public String getVerificationCode() {
            return verificationCode;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public void setVerificationCode(String verificationCode) {
            this.verificationCode = verificationCode;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public Long getDuration() {
            return duration;
        }

        public void setDuration(Long duration) {
            this.duration = duration;
        }

        public String getSystemName() {
            return systemName;
        }

        public void setSystemName(String system_name) {
            this.systemName = system_name;
        }

        public UUID getId() {
            return id;
        }
    }

    @Entity
    @Table(name = "sent_emails")
    public static class SentEmails {
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @NaturalId
        public String email;

        @Column(name = "system_name")
        public String systemName;

        @Column(name = "message")
        public String message;

        public SentEmails() {}

        public SentEmails(String email, String systemName, String message) {
            this.email = email;
            this.systemName = systemName;
            this.message = message;
        }

        public String getEmail() {
            return email;
        }

        public String getMessage() {
            return message;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public UUID getId() {
            return id;
        }

        public String getSystemName() {
            return systemName;
        }

        public void setSystemName(String systemName) {
            this.systemName = systemName;
        }
    }

    public static class Utils {
        public static boolean isValidEmail(String email) {
            try {
                new InternetAddress(email).validate();
                return true;
            } catch (Exception ex){
                return false;
            }
        }
    }
}
