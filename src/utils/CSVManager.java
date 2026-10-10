package utils;

import models.Appointment;
import models.DocumentRequest;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Saves/loads appointments in appointments.csv.
 * Row format (every field quoted):
 * date,time,id,name,courseAndYear,purpose,documents,status,dateSubmitted
 * "date" is the appointment day. Older 8-column rows (no dateSubmitted) still load fine.
 */
public class CSVManager {
    /** The most appointments the registrar accepts for one day. */
    public static final int MAX_PER_DAY = 50;

    private static final String FILE_PATH = "appointments.csv";

    public static synchronized void saveAppointment(Appointment app) {
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(FILE_PATH, true)))) {
            out.println(toCsvLine(app));
        } catch (IOException e) {
            System.out.println("Error saving appointment: " + e.getMessage());
        }
    }

    /**
     * Saves the appointment only if its day still has room.
     * The check and the save happen together, so two students can't both grab the last slot.
     * @return false when the day is already full (nothing is saved)
     */
    public static synchronized boolean saveIfAvailable(Appointment app) {
        if (countForDate(app.getDate()) >= MAX_PER_DAY) return false;
        saveAppointment(app);
        return true;
    }

    /** How many appointments (pending + printed) are booked on the given day. */
    public static synchronized int countForDate(LocalDate date) {
        int n = 0;
        for (Appointment a : loadAllAppointments()) {
            if (a.getDate().equals(date)) n++;
        }
        return n;
    }

    /** Appointment day -> number of appointments booked on it. Days with none are absent. */
    public static synchronized Map<LocalDate, Integer> countByDate() {
        Map<LocalDate, Integer> counts = new HashMap<>();
        for (Appointment a : loadAllAppointments()) counts.merge(a.getDate(), 1, Integer::sum);
        return counts;
    }

    public static synchronized List<Appointment> loadAllAppointments() {
        List<Appointment> records = new ArrayList<>();
        File file = new File(FILE_PATH);
        if (!file.exists()) return records;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                List<String> f = parseLine(line);
                if (f.size() != 8 && f.size() != 9) continue; // skip invalid rows

                try {
                    List<DocumentRequest> docs = new ArrayList<>();
                    for (String d : f.get(6).split("\\|")) {
                        DocumentRequest req = DocumentRequest.fromStorageString(d);
                        if (req != null) docs.add(req);
                    }
                    LocalDate date = LocalDate.parse(f.get(0));
                    LocalDate submitted = f.size() == 9 ? LocalDate.parse(f.get(8)) : date;
                    records.add(new Appointment(date, submitted, f.get(1), f.get(2),
                            f.get(3), f.get(4), f.get(5), docs, f.get(7)));
                } catch (DateTimeParseException ex) {
                    // skip rows with a bad date
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading appointments: " + e.getMessage());
        }
        return records;
    }

    /** Changes the matching PENDING appointment to PRINTED. */
    public static synchronized void markAsPrinted(Appointment target) {
        List<Appointment> all = loadAllAppointments();
        boolean changed = false;

        for (Appointment a : all) {
            if (a.getDate().equals(target.getDate())
                    && a.getTimeSubmitted().equals(target.getTimeSubmitted())
                    && a.getIdNumber().equals(target.getIdNumber())
                    && "PENDING".equals(a.getStatus())) {
                a.setStatus("PRINTED");
                changed = true;
                break;
            }
        }
        if (changed) writeAll(all);
    }

    // ---------- helpers ----------

    private static void writeAll(List<Appointment> list) {
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(FILE_PATH, false)))) {
            for (Appointment a : list) out.println(toCsvLine(a));
        } catch (IOException e) {
            System.out.println("Error updating status: " + e.getMessage());
        }
    }

    private static String toCsvLine(Appointment a) {
        StringBuilder docs = new StringBuilder();
        for (int i = 0; i < a.getDocuments().size(); i++) {
            if (i > 0) docs.append("|");
            docs.append(a.getDocuments().get(i).toStorageString());
        }
        return quote(a.getDate().toString()) + "," + quote(a.getTimeSubmitted()) + ","
                + quote(a.getIdNumber()) + "," + quote(a.getName()) + ","
                + quote(a.getCourseAndYear()) + "," + quote(a.getPurpose()) + ","
                + quote(docs.toString()) + "," + quote(a.getStatus()) + ","
                + quote(a.getDateSubmitted().toString());
    }

    private static String quote(String s) {
        if (s == null) s = "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    /** Splits one CSV line, respecting quoted fields and "" escapes. */
    private static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(ch);
                }
            } else {
                if (ch == '"') inQuotes = true;
                else if (ch == ',') { fields.add(cur.toString()); cur.setLength(0); }
                else cur.append(ch);
            }
        }
        fields.add(cur.toString());
        return fields;
    }
}
