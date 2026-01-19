package com.example.logredact;

import net.neoforged.fml.common.Mod;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.rewrite.RewriteAppender;
import org.apache.logging.log4j.core.appender.rewrite.RewritePolicy;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;

import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.apache.logging.log4j.util.StringMap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Mod("logredact") // MUST match neoforge.mods.toml
public final class LogRedact {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final Pattern IPV4 =
            Pattern.compile("(?<!\\d)(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?!\\d)");
    private static final Pattern UUID =
            Pattern.compile("(?i)(?<![0-9a-f])[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?![0-9a-f])");
    private static final Pattern COORDS =
            Pattern.compile("\\(\\s*-?\\d+(?:\\.\\d+)?\\s*,\\s*-?\\d+(?:\\.\\d+)?\\s*,\\s*-?\\d+(?:\\.\\d+)?\\s*\\)");

    // Optional IPv6 masking (rough but practical)
    private static final Pattern IPV6 =
            Pattern.compile("(?i)\\b(?:[0-9a-f]{1,4}:){2,7}[0-9a-f]{1,4}\\b");

    private static volatile boolean installed = false;

    public LogRedact() {
        installOnce();
    }

    private static void installOnce() {
        if (installed) return;
        installed = true;

        try {
            final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            final Configuration config = ctx.getConfiguration();
            final LoggerConfig root = config.getRootLogger();

            // Snapshot existing appender attachments AND their per-ref threshold levels/filters.
            final AppenderRef[] existingRefs = root.getAppenderRefs().toArray(new AppenderRef[0]);

            // Snapshot current appenders map (used only for diagnostics; not relied on for levels).
            final Map<String, org.apache.logging.log4j.core.Appender> current =
                    new LinkedHashMap<>(root.getAppenders());

            if (existingRefs.length == 0 || current.isEmpty()) {
                LOGGER.warn("[logredact] No root appender refs found; nothing to wrap.");
                return;
            }

            final RewritePolicy policy = new RedactPolicy();

            for (AppenderRef ref : existingRefs) {
                final String origName = ref.getRef();
                if (origName == null || origName.isBlank()) continue;

                final String rewriteName = "LogRedact-" + origName;

                // RewriteAppender delegates to the original appender by name.
                final AppenderRef[] delegateRefs = new AppenderRef[]{
                        AppenderRef.createAppenderRef(origName, null, null)
                };

                final RewriteAppender rewriteAppender =
                        RewriteAppender.createAppender(
                                rewriteName,
                                "true", // ignoreExceptions
                                delegateRefs,
                                config,
                                policy,
                                null // filter
                        );

                rewriteAppender.start();
                config.addAppender(rewriteAppender);

                // Remove the original attachment from root to avoid double logging.
                root.removeAppender(origName);

                // Preserve original per-appender threshold level + filter (this is the key fix).
                final Level threshold = ref.getLevel();
                final Filter refFilter = ref.getFilter();

                root.addAppender(rewriteAppender, threshold, refFilter);
            }

            ctx.updateLoggers();
            LOGGER.info("[logredact] Installed redaction wrappers for {} appender(s).", existingRefs.length);
        } catch (Throwable t) {
            LOGGER.error("[logredact] Failed to install redaction. Logs will be unredacted.", t);
        }
    }

    private static final class RedactPolicy implements RewritePolicy {
        @Override
        public LogEvent rewrite(final LogEvent source) {
            if (source == null) return null;

            final var msg = source.getMessage();
            final String formatted = (msg == null) ? "" : msg.getFormattedMessage();
            final String redacted = redact(formatted);

            if (redacted.equals(formatted)) return source;

            return Log4jLogEvent.newBuilder()
                    .setLoggerName(source.getLoggerName())
                    .setLoggerFqcn(source.getLoggerFqcn())
                    .setLevel(source.getLevel())
                    .setMarker(source.getMarker())
                    .setThrown(source.getThrown())
                    .setContextData(copyContextData(source.getContextData()))
                    .setContextStack(source.getContextStack())
                    .setThreadName(source.getThreadName())
                    .setTimeMillis(source.getTimeMillis())
                    .setNanoTime(source.getNanoTime())
                    .setMessage(new SimpleMessage(redacted))
                    .build();
        }

        private static StringMap copyContextData(ReadOnlyStringMap ro) {
            SortedArrayStringMap m = new SortedArrayStringMap();
            if (ro != null) {
                m.putAll(ro);
            }
            return m;
        }

        private static String redact(String s) {
            if (s == null || s.isEmpty()) return s;
            s = IPV4.matcher(s).replaceAll("[REDACTED_IP]");
            s = IPV6.matcher(s).replaceAll("[REDACTED_IP]");
            s = UUID.matcher(s).replaceAll("[REDACTED_UUID]");
            s = COORDS.matcher(s).replaceAll("([REDACTED_COORDS])");
            return s;
        }
    }
}
