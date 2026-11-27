import com.hamza.studyhub.imports.SchoolExcerpt;

public final class SchoolExcerptSmoke {
    public static void main(String[] args) {
        SchoolExcerpt german = SchoolExcerpt.review("Classroom", "  Brüche  ", " Bearbeite Aufgabe zwei bis Freitag. ", "");
        check("Brüche".equals(german.title) && german.dueDate == null, "never infer Freitag");
        check("Bearbeite Aufgabe zwei bis Freitag.".equals(german.excerpt), "retain edited text only");
        SchoolExcerpt arabic = SchoolExcerpt.review("Teams", "واجب", "أكمل التمرين الثالث", "2028-02-29");
        check(Integer.valueOf(20280229).equals(arabic.dueDate) && "Teams".equals(arabic.source), "ISO date and source");
        reject("date", "Classroom", "A", "B", "03.04.28");
        reject("date", "Classroom", "A", "B", "2027-02-29");
        reject("excerpt", "Teams", "A", "Von: x\nAufgabe B", "");
        reject("excerpt", "Teams", "A", "Schreibe an x@y.example", "");
        reject("excerpt", "Teams", "A", "Text https://example.invalid", "");
        reject("source", "Other", "A", "B", "");
        reject("title", "Teams", "A\nB", "C", "");
        System.out.println("Study Hub excerpt contract: 10 assertions passed.");
    }
    private static void reject(String expected, String source, String title, String text, String date) {
        try {
            SchoolExcerpt.review(source, title, text, date);
            throw new AssertionError("Accepted " + expected);
        } catch (IllegalArgumentException actual) {
            check(expected.equals(actual.getMessage()), "wrong rejection");
        }
    }
    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
