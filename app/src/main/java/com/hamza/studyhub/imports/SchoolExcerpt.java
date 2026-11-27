package com.hamza.studyhub.imports;

import java.time.LocalDate;
import java.util.regex.Pattern;

/** One caregiver-selected snapshot, never an account sync or an inferred deadline. */
public final class SchoolExcerpt {
    private static final Pattern CONTACT = Pattern.compile(
        "(?iu)https?://|www\\.|mailto:|[^\\s@]+@[^\\s@]+|(?:^|\\n)\\s*(?:from|to|cc|bcc|von|an|من|إلى):");
    public final String source;
    public final String title;
    public final String excerpt;
    public final Integer dueDate;

    private SchoolExcerpt(String source, String title, String excerpt, Integer dueDate) {
        this.source = source;
        this.title = title;
        this.excerpt = excerpt;
        this.dueDate = dueDate;
    }

    public static SchoolExcerpt review(String source, String title, String excerpt, String date) {
        if (!"Classroom".equals(source) && !"Teams".equals(source)) throw new IllegalArgumentException("source");
        title = title.trim();
        excerpt = excerpt.trim();
        date = date.trim();
        if (title.isEmpty() || title.length() > 100 || title.contains("\n") || title.contains("\r")) {
            throw new IllegalArgumentException("title");
        }
        if (excerpt.isEmpty() || excerpt.length() > 1200 || CONTACT.matcher(title).find() ||
                CONTACT.matcher(excerpt.replace('\r', '\n')).find()) {
            throw new IllegalArgumentException("excerpt");
        }
        Integer due = null;
        if (!date.isEmpty()) {
            if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException("date");
            try {
                LocalDate day = LocalDate.parse(date);
                if (day.getYear() < 2000 || day.getYear() > 2099) throw new IllegalArgumentException("date");
                due = day.getYear() * 10000 + day.getMonthValue() * 100 + day.getDayOfMonth();
            } catch (java.time.DateTimeException error) {
                throw new IllegalArgumentException("date");
            }
        }
        return new SchoolExcerpt(source, title, excerpt, due);
    }
}
