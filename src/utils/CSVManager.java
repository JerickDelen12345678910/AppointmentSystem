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
import java.util.Properties;

/**
 * Saves/loads appointments in appointments.csv.
 * Row format (every field quoted):
 * date,time,id,name,courseAndYear,purpose,documents,status,dateSubmitted
 * "date" is the appointment day. Older 8-column rows (no dateSubmitted) still load fine.
 *
 * The daily appointment limit is configurable. It defaults to 50 and is stored in
 * settings.properties, so the registrar's choice survives restarts. getMaxPerDay() checks
 * that file every time it is called and picks up changes automatically, even when another
 * copy of the app (for example the registrar's PC) is the one that changed it.
 */
public class CSVManager {
    /** Limit used when settings.properties is missing or invalid. */
    public static final int DEFAULT_MAX_PER_DAY = 50;

    /** Smallest and largest limit the registrar is allowed to set. */
    public static final int MIN_LIMIT = 1;
    public static final int MAX_LIMIT = 500;

    /**
     * The most appointments the registrar accepts for one day (starts at the default, 50).
     * Read it with getMaxPerDay() (that call also refreshes it from settings.properties);
     * change it ONLY with setMaxPerDay() so the new value is saved.
     * The field keeps its old name so existing code that reads CSVManager.MAX_PER_DAY still compiles.
     */
    public static volatile int MAX_PER_DAY = DEFAULT_MAX_PER_DAY;

    private static final String FILE_PATH = "appointments.csv";
    private static final String SETTINGS_PATH = "settings.properties";
    private static final String KEY_MAX_PER_DAY = "maxPerDay";

    /** Last-modified time of settings.properties when we last read it (0 = file missing). */
    private static long settingsStamp = -1;

    static {
        refreshSettings();
    }

    // ---------- settings ----------

    /** The current daily appointment limit, always up to date with settings.properties. */
    public static int getMaxPerDay() {
        refreshSettings();
        return MAX_PER_DAY;
    }

    /**
     * Changes the daily limit and saves it to settings.properties.
     * Existing bookings are never deleted: a day that already has more bookings than the
     * new limit simply counts as full and accepts no more.
     * @return false if the number is outside MIN_LIMIT..MAX_LIMIT or could not be saved
     *         (the old limit stays in effect)
     */
    public static synchronized boolean setMaxPerDay(int limit) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) return false;

        Properties props = new Properties();
        props.setProperty(KEY_MAX_PER_DAY, String.valueOf(limit));
        try (OutputStream out = new FileOutputStream(SETTINGS_PATH)) {
            props.store(out, "Registrar settings");
        } catch (IOException e) {
            System.out.println("Error saving settings: " + e.getMessage());
            return false;
        }
        MAX_PER_DAY = limit;
        File saved = new File(SETTINGS_PATH);
        settingsStamp = saved.exists() ? saved.lastModified() : 0L;
        return true;
    }

    /**
     * Re-reads settings.properties, but only when the file changed (or appeared or disappeared)
     * since the last check, so calling this often is cheap. A missing, unreadable, or
     * out-of-range file means the default limit is used.
     */
    private static synchronized void refreshSettings() {
        File file = new File(SETTINGS_PATH);
        long stamp = file.exists() ? file.lastModified() : 0L;
        if (stamp == settingsStamp) return;
        settingsStamp = stamp;

        int limit = DEFAULT_MAX_PER_DAY;
        if (stamp != 0L) {
            Properties props = new Properties();
            try (InputStream in = new FileInputStream(file)) {
                props.load(in);
                int value = Integer.parseInt(props.getProperty(KEY_MAX_PER_DAY, "").trim());
                if (value >= MIN_LIMIT && value <= MAX_LIMIT) limit = value;
            } catch (IOException | NumberFormatException e) {
                System.out.println("Could not read settings, using default limit: " + e.getMessage());
            }
        }
        MAX_PER_DAY = limit;
    }

    // ---------- appointments ----------

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
        if (countForDate(app.getDate()) >= getMaxPerDay()) return false;
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