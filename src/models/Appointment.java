package models;

import java.time.LocalDate;
import java.util.List;

public class Appointment {
    /** The day the student is booked to come in (this is what the 50-per-day limit counts). */
    private final LocalDate date;
    /** The day the student actually filled in the form. */
    private final LocalDate dateSubmitted;
    private final String timeSubmitted;
    private final String idNumber;
    private final String name;
    private final String courseAndYear;
    private final String purpose;
    private final List<DocumentRequest> documents;
    private String status;

    public Appointment(LocalDate date, LocalDate dateSubmitted, String timeSubmitted, String idNumber,
                       String name, String courseAndYear, String purpose,
                       List<DocumentRequest> documents, String status) {
        this.date = date;
        this.dateSubmitted = dateSubmitted;
        this.timeSubmitted = timeSubmitted;
        this.idNumber = idNumber;
        this.name = name;
        this.courseAndYear = courseAndYear;
        this.purpose = purpose;
        this.documents = documents;
        this.status = status;
    }

    /** Older rows have no separate submitted date, so it is the same as the appointment date. */
    public Appointment(LocalDate date, String timeSubmitted, String idNumber, String name,
                       String courseAndYear, String purpose,
                       List<DocumentRequest> documents, String status) {
        this(date, date, timeSubmitted, idNumber, name, courseAndYear, purpose, documents, status);
    }

    public LocalDate getDate() { return date; }
    public LocalDate getDateSubmitted() { return dateSubmitted; }
    public String getTimeSubmitted() { return timeSubmitted; }
    public String getIdNumber() { return idNumber; }
    public String getName() { return name; }
    public String getCourseAndYear() { return courseAndYear; }
    public String getPurpose() { return purpose; }
    public List<DocumentRequest> getDocuments() { return documents; }
    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }
}
