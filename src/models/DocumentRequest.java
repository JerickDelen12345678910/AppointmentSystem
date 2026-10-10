package models;

public class DocumentRequest {
    private final String doc;
    private final String year;
    private final String sem;

    public DocumentRequest(String doc, String year, String sem) {
        this.doc = doc;
        this.year = year;
        this.sem = sem;
    }

    public String getDoc() { return doc; }
    public String getYear() { return year; }
    public String getSem() { return sem; }

    /** Full document name, e.g. COE -> Certificate of Enrollment. */
    public String getFullName() {
        switch (doc) {
            case "COG": return "Certificate of Grades";
            case "COR": return "Certificate of Registration";
            case "COE": return "Certificate of Enrollment";
            default:    return doc;
        }
    }

    /** Used by the admin dashboard: Certificate of Enrollment (2nd Year, 1st Sem) */
    public String getDisplayName() {
        return getFullName() + " (" + year + ", " + sem + ")";
    }

    /** Used by the student cart: COR - 1st Year, 1st Sem */
    @Override
    public String toString() {
        return doc + " - " + year + ", " + sem;
    }

    /** Compact form saved inside the CSV. */
    public String toStorageString() {
        return doc + "~" + year + "~" + sem;
    }

    public static DocumentRequest fromStorageString(String s) {
        String[] p = s.split("~", -1);
        if (p.length != 3) return null;
        return new DocumentRequest(p[0], p[1], p[2]);
    }
}