package com.mycompany.gymbooking.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Builds the branded HTML version of an email from its plain text, so both versions always say the
 * same thing. A few patterns in the text get their own look:
 * <pre>
 *   "  Trainer:   Sara Haddad"       indented "Label: value" lines become a table of details
 *   "  1. Open the app."             indented numbered lines become a numbered list
 *   "Your invite code is: 123456"    the code is shown large, easy to read and copy
 * </pre>
 * Everything else becomes paragraphs, separated by blank lines. Styles are inline because many mail
 * apps ignore style sheets.
 */
@Component
public class EmailLayout {

    private static final Pattern DETAIL = Pattern.compile("^ {2}([A-Za-z][A-Za-z ]{0,19}):\\s+(\\S.*)$");
    private static final Pattern STEP = Pattern.compile("^ {2}\\d+\\.\\s+(\\S.*)$");
    private static final Pattern CODE = Pattern.compile("^(.*\\bcode is):\\s*(\\d{6})$", Pattern.CASE_INSENSITIVE);

    private static final String BRAND_COLOR = "#FF6B2C";   // the app's seed color
    private static final String FONT = "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;";

    private enum Kind { TEXT, DETAIL, STEP, CODE, BLANK }

    private final String brand;

    public EmailLayout(@Value("${app.mail.from-name}") String brand) {
        this.brand = brand;
    }

    public String html(String subject, String text) {
        StringBuilder content = new StringBuilder();
        List<String> group = new ArrayList<>();
        Kind groupKind = Kind.BLANK;
        for (String line : text.split("\\R")) {
            Kind kind = kindOf(line);
            // Lines of the same kind form one paragraph, table or list; each code stands alone.
            if (kind != groupKind || kind == Kind.CODE) {
                appendGroup(content, groupKind, group);
                group.clear();
                groupKind = kind;
            }
            group.add(line);
        }
        appendGroup(content, groupKind, group);

        return "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n<head>\n"
                + "<meta charset=\"UTF-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
                + "<meta name=\"color-scheme\" content=\"light\">\n"
                + "<title>" + escape(subject) + "</title>\n"
                + "</head>\n"
                + "<body style=\"margin:0;padding:0;background:#f4f1ee;\">\n"
                // Hidden text that mail apps show next to the subject in the inbox
                + "<div style=\"display:none;max-height:0;overflow:hidden;\">" + escape(preview(text)) + "</div>\n"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f4f1ee;\">\n"
                + "<tr><td align=\"center\" style=\"padding:24px 12px;\">\n"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:560px;"
                + "background:#ffffff;border-radius:12px;" + FONT + "color:#1f1b18;\">\n"
                + "<tr><td style=\"background:" + BRAND_COLOR + ";border-radius:12px 12px 0 0;padding:20px 28px;"
                + "color:#ffffff;font-size:20px;font-weight:700;\">" + escape(brand) + "</td></tr>\n"
                + "<tr><td style=\"padding:28px 28px 12px;font-size:16px;line-height:1.5;\">\n"
                + content
                + "</td></tr>\n"
                + "</table>\n"
                + "<p style=\"margin:16px 0 0;" + FONT + "font-size:12px;line-height:1.5;color:#8a817b;\">"
                + "You're receiving this email because of your " + escape(brand) + " account.</p>\n"
                + "</td></tr>\n"
                + "</table>\n"
                + "</body>\n</html>\n";
    }

    private static Kind kindOf(String line) {
        if (line.isBlank()) {
            return Kind.BLANK;
        }
        if (DETAIL.matcher(line).matches()) {
            return Kind.DETAIL;
        }
        if (STEP.matcher(line).matches()) {
            return Kind.STEP;
        }
        return CODE.matcher(line.strip()).matches() ? Kind.CODE : Kind.TEXT;
    }

    private static void appendGroup(StringBuilder html, Kind kind, List<String> lines) {
        switch (kind) {
            case TEXT -> html.append("<p style=\"margin:0 0 16px;\">")
                    .append(String.join("<br>\n", lines.stream().map(line -> escape(line.strip())).toList()))
                    .append("</p>\n");
            case DETAIL -> {
                html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                        + "style=\"margin:0 0 16px;background:#faf6f3;border-radius:8px;\">\n");
                for (String line : lines) {
                    Matcher detail = DETAIL.matcher(line);
                    detail.matches();
                    html.append("<tr><td style=\"padding:8px 0 8px 16px;color:#6b625c;vertical-align:top;white-space:nowrap;\">")
                            .append(escape(detail.group(1)))
                            .append("</td><td style=\"padding:8px 16px;font-weight:600;\">")
                            .append(escape(detail.group(2).strip()))
                            .append("</td></tr>\n");
                }
                html.append("</table>\n");
            }
            case STEP -> {
                html.append("<ol style=\"margin:0 0 16px;padding-left:24px;\">\n");
                for (String line : lines) {
                    Matcher step = STEP.matcher(line);
                    step.matches();
                    html.append("<li style=\"margin:0 0 4px;\">").append(escape(step.group(1).strip())).append("</li>\n");
                }
                html.append("</ol>\n");
            }
            case CODE -> {
                Matcher code = CODE.matcher(lines.get(0).strip());
                code.matches();
                html.append("<p style=\"margin:0 0 8px;\">").append(escape(code.group(1))).append(":</p>\n")
                        // The extra left padding balances the letter spacing after the last digit.
                        .append("<div style=\"margin:0 0 16px;padding:16px 16px 16px 24px;background:#fff1ea;border-radius:8px;"
                                + "text-align:center;font-family:'SF Mono',Menlo,Consolas,monospace;font-size:32px;"
                                + "font-weight:700;letter-spacing:8px;color:#c2410c;\">")
                        .append(code.group(2))
                        .append("</div>\n");
            }
            case BLANK -> {
                // nothing: blank lines only separate groups
            }
        }
    }

    /** The first line after the greeting, e.g. "Your session is confirmed." */
    private static String preview(String text) {
        for (String line : text.split("\\R")) {
            Kind kind = kindOf(line);
            if ((kind == Kind.TEXT || kind == Kind.CODE) && !line.startsWith("Hi ")) {
                String preview = line.strip();
                return preview.length() > 140 ? preview.substring(0, 140) : preview;
            }
        }
        return "";
    }

    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text, "UTF-8");
    }
}
