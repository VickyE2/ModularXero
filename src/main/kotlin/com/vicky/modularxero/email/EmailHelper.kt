package com.vicky.modularxero.email

import com.vicky.modularxero.modules.email.EmailModule
import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

val SYSTEM_MAIL = System.getenv("SYSTEM_MAIL")
val SYSTEM_PASSWORD = System.getenv("SYSTEM_PASSWORD")



/**
 * Simple color scheme for email templates (not tied to Android Compose).
 */
data class ColorScheme(
    val primary: String = "#eeeeee",
    val onPrimary: String = "#120014",
    val secondary: String = "#ffccc8",
    val onSecondary: String = "#07030f",
    val background: String = "#08040f",
    val onBackground: String = "#EDE8F6",
    val surface: String = "#0D0410",
    val onSurface: String = "#EDE8F6",
    val outline: String = "#860019"
)

/**
 * Replacement value wrapper: either text (will be HTML-escaped) or Raw HTML (used as-is).
 */
sealed class ReplacementValue {
    data class Text(val value: String) : ReplacementValue()
    data class RawHtml(val value: String) : ReplacementValue()
}

/**
 * EmailTemplate:
 * - subjectTemplate: plain text with placeholders like {{code}} or {{username}}.
 * - bodyTemplate: HTML string. Use placeholders the same way.
 *
 * Use renderSubject/renderBody with a map of replacements.
 */
class EmailTemplate(
    val subjectTemplate: String,
    val bodyTemplate: String
)
{
    companion object {
        private val defaultDateFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"))

        private fun inlineStylesFor(scheme: ColorScheme): String {
            // returns base inline CSS for header/footer sections — small and self-contained
            return """
                background-color: ${scheme.primary};
                color: ${scheme.onPrimary};
                font-family: 'Helvetica Neue', Arial, sans-serif;
                padding: 18px 24px;
                border-radius: 8px 8px 0 0;
                text-align: left;
            """.trimIndent()
        }

        private fun containerStyle(scheme: ColorScheme): String {
            return """
                background-color: ${scheme.background};
                color: ${scheme.onBackground};
                padding: 24px;
            """.trimIndent()
        }

        private fun cardStyle(scheme: ColorScheme): String {
            return """
                background-color: ${scheme.surface};
                color: ${scheme.onSurface};
                border-radius: 8px;
                padding: 18px;
                margin: 12px 0;
            """.trimIndent()
        }

        // --------------- DEFAULT TEMPLATES ---------------

        fun verificationCodeTemplate(scheme: ColorScheme) = EmailTemplate(
            subjectTemplate = "{{appName}} — Your verification code",
            bodyTemplate = """
            <div style="margin:0;padding:0;background:${scheme.background};color:${scheme.onBackground};">
              <div style="max-width:680px;margin:32px auto;">
                <div style="${inlineStylesFor(scheme)}">
                  <h2 style="margin:0;font-size:20px;">{{appName}}</h2>
                </div>
                <div style="${containerStyle(scheme)}">
                  <div style="${cardStyle(scheme)}">
                    <p>Hi {{username}},</p>
                    <p>Your verification code is:</p>
                    <h1 style="letter-spacing:6px;color:${scheme.primary};background:${scheme.onPrimary};display:inline-block;padding:8px 14px;border-radius:6px;">{{code}}</h1>
                    <p>This code will expire in {{expiryMinutes}} minutes.</p>
                    <hr style="border:none;border-top:1px solid ${scheme.outline};margin:18px 0;">
                    <p>If you didn't request this, ignore this email or contact <a href="mailto:{{supportEmail}}" style="color:${scheme.secondary}">{{supportEmail}}</a>.</p>
                  </div>
                  <p style="font-size:12px;color:${scheme.onBackground};opacity:0.85;margin-top:12px;">Sent at {{sentAt}}</p>
                </div>
              </div>
            </div>
            """.trimIndent()
        )

        fun urgentMessageTemplate(scheme: ColorScheme) = EmailTemplate(
            subjectTemplate = "[URGENT] {{title}} — {{appName}}",
            bodyTemplate = """
            <div style="margin:0;padding:0;background:${scheme.background};color:${scheme.onBackground};">
              <div style="max-width:720px;margin:24px auto;">
                <div style="${inlineStylesFor(scheme)}">
                  <h2 style="margin:0;font-size:20px;">{{appName}} — Urgent</h2>
                </div>
                <div style="${containerStyle(scheme)}">
                  <div style="${cardStyle(scheme)}">
                    <h3 style="margin-top:0;color:${scheme.secondary};">{{title}}</h3>
                    <p>{{message}}</p>
                    <p style="font-weight:600;">Action required: {{action}}</p>
                    <hr style="border:none;border-top:1px solid ${scheme.outline};margin:18px 0;">
                    <p style="font-size:13px;color:${scheme.onBackground};opacity:0.9;">Contact <a href="mailto:{{supportEmail}}" style="color:${scheme.primary}">{{supportEmail}}</a> for support.</p>
                  </div>
                </div>
              </div>
            </div>
            """.trimIndent()
        )

        fun accountDeletionTemplate(scheme: ColorScheme) = EmailTemplate(
            subjectTemplate = "{{appName}} — Account Deletion Scheduled",
            bodyTemplate = """
            <div style="margin:0;padding:0;background:${scheme.background};color:${scheme.onBackground};">
              <div style="max-width:720px;margin:24px auto;">
                <div style="${inlineStylesFor(scheme)}">
                  <h2 style="margin:0;font-size:20px;">{{appName}} — Account Deletion</h2>
                </div>
                <div style="${containerStyle(scheme)}">
                  <div style="${cardStyle(scheme)}">
                    <p>Hello {{username}},</p>
                    <p>We're emailing to confirm your account is scheduled for deletion on <strong>{{deletionDate}}</strong>.</p>
                    <p>If you did not request deletion, please contact us immediately at <a href="mailto:{{supportEmail}}" style="color:${scheme.secondary}">{{supportEmail}}</a>.</p>
                    <hr style="border:none;border-top:1px solid ${scheme.outline};margin:18px 0;">
                    <p style="font-size:13px;color:${scheme.onBackground};opacity:0.9;">This action is irreversible after {{deletionDate}}.</p>
                  </div>
                </div>
              </div>
            </div>
            """.trimIndent()
        )

        fun successfulRegistrationTemplate(scheme: ColorScheme) = EmailTemplate(
            subjectTemplate = "Welcome to {{appName}}, {{username}}!",
            bodyTemplate = """
            <div style="margin:0;padding:0;background:${scheme.background};color:${scheme.onBackground};">
              <div style="max-width:720px;margin:20px auto;">
                <div style="${inlineStylesFor(scheme)}">
                  <h2 style="margin:0;font-size:20px;">Welcome — {{appName}}</h2>
                </div>
                <div style="${containerStyle(scheme)}">
                  <div style="${cardStyle(scheme)}">
                    <p>Hi {{username}},</p>
                    <p>Thanks for registering. You can log in at <a href="{{loginUrl}}" style="color:${scheme.primary}">{{loginUrl}}</a>.</p>
                    <p>We're excited to have you on board.</p>
                  </div>
                  <p style="font-size:12px;color:${scheme.onBackground};opacity:0.85;margin-top:12px;">Need help? Contact <a href="mailto:{{supportEmail}}" style="color:${scheme.secondary}">{{supportEmail}}</a></p>
                </div>
              </div>
            </div>
            """.trimIndent()
        )

        fun passwordResetTemplate(scheme: ColorScheme) = EmailTemplate(
            subjectTemplate = "{{appName}} — Password reset request",
            bodyTemplate = """
            <div style="margin:0;padding:0;background:${scheme.background};color:${scheme.onBackground};">
              <div style="max-width:720px;margin:20px auto;">
                <div style="${inlineStylesFor(scheme)}">
                  <h2 style="margin:0;font-size:20px;">{{appName}} — Password Reset</h2>
                </div>
                <div style="${containerStyle(scheme)}">
                  <div style="${cardStyle(scheme)}">
                    <p>Hello {{username}},</p>
                    <p>We received a request to reset your password. Click the button below to reset it:</p>
                    <p style="text-align:center;margin:18px 0;">
                      <a href="{{resetLink}}" style="display:inline-block;padding:10px 18px;border-radius:6px;background:${scheme.primary};color:${scheme.onPrimary};text-decoration:none;">Reset password</a>
                    </p>
                    <p>This link expires in {{expiryMinutes}} minutes.</p>
                    <hr style="border:none;border-top:1px solid ${scheme.outline};margin:18px 0;">
                    <p style="font-size:12px;color:${scheme.onBackground};opacity:0.85;">If you didn't request this change, ignore this email or contact <a href="mailto:{{supportEmail}}" style="color:${scheme.secondary}">{{supportEmail}}</a>.</p>
                  </div>
                </div>
              </div>
            </div>
            """.trimIndent()
        )
    }

    /**
     * Render subject by replacing placeholders. Plain text. Escapes replacements when provided as Text.
     */
    fun renderSubject(replacements: Map<String, ReplacementValue>): String =
        replaceAll(subjectTemplate, replacements)

    /**
     * Render body HTML. All text replacements get HTML-escaped by default. Use ReplacementValue.RawHtml to inject HTML.
     */
    fun renderBody(replacements: Map<String, ReplacementValue>): String {
        // automatically add sentAt if not provided
        val withSentAt = if (replacements.containsKey("sentAt")) replacements else {
            replacements + ("sentAt" to ReplacementValue.Text(defaultDateFormatter.format(Instant.now())))
        }
        return replaceAll(bodyTemplate, withSentAt)
    }

    private fun replaceAll(template: String, replacements: Map<String, ReplacementValue>): String {
        var out = template
        replacements.forEach { (k, v) ->
            val value = when (v) {
                is ReplacementValue.RawHtml -> v.value
                is ReplacementValue.Text -> htmlEscape(v.value)
            }
            // regex to match {{ key }} ignoring casing and spaces around key
            val regex = Regex("\\{\\{\\s*${Regex.escape(k)}\\s*\\}\\}", RegexOption.IGNORE_CASE)
            out = out.replace(regex, value)
        }
        return out
    }

    private fun htmlEscape(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
    }
}

fun generateVerificationCode(length: Int = 6): String {
    val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    return (1..length)
        .map { chars.random() }
        .joinToString("")
}

@JvmOverloads
fun sendEmail(
    bodyMap: Map<String, ReplacementValue.Text> = mapOf(
        "appName" to ReplacementValue.Text("SystemEmailService"),
        "username" to ReplacementValue.Text("Modular-X"),
        "code" to ReplacementValue.Text("THIS-IS-A-DEFAULT"),
        "expiryMinutes" to ReplacementValue.Text("10"),
        "supportEmail" to ReplacementValue.Text("modular_xero@gmail.com")
    ),
    subjectMap: Map<String, ReplacementValue.Text> = mapOf(
        "appName" to ReplacementValue.Text("Modular-Xero-System")
    ),
    template: EmailTemplate = EmailTemplate
        .successfulRegistrationTemplate(ColorScheme()),
    toEmail: String,
    fromEmail: String = SYSTEM_MAIL,
    password : String = SYSTEM_PASSWORD,
) : Boolean {
    val props = Properties()
    props["mail.smtp.auth"] = "true"
    props["mail.smtp.starttls.enable"] = "true"
    props["mail.smtp.host"] = "smtp.gmail.com"
    props["mail.smtp.port"] = "587"

    val session = Session.getInstance(props, object : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication {
            return PasswordAuthentication(fromEmail, password)
        }
    })

    try {
        val message = MimeMessage(session)
        message.setFrom(InternetAddress(fromEmail))
        message.setRecipients(
            Message.RecipientType.TO,
            InternetAddress.parse(toEmail)
        )

        val subject = template.renderSubject(subjectMap)
        val body = template.renderBody(bodyMap)

        message.subject = subject

        // ← Important: tell JavaMail this is HTML
        message.setContent(body, "text/html; charset=utf-8")

        Transport.send(message)
        return true
    } catch (e: MessagingException) {
        e.printStackTrace()
        return false
    }
}

fun EmailModule.EmailVerificationEntity.isExpired() =
    this.createdAt.plusSeconds(this.duration) < LocalDateTime.now()

fun testConnection(
    host: String,
    port: Int,
    username: String?,
    password: String?
) {
    val props = Properties().apply {
        put("mail.smtp.auth", "true")
        put("mail.smtp.starttls.enable", "true")
        put("mail.smtp.starttls.required", "true")
        put("mail.smtp.host", host)
        put("mail.smtp.port", port.toString())
        put("mail.smtp.connectiontimeout", "3000")
        put("mail.smtp.timeout", "3000")
        put("mail.smtp.writetimeout", "3000")
    }

    val session = Session.getInstance(props)
    val transport = session.getTransport("smtp")

    transport.use {
        it.connect(host, port, username, password)
    }
}