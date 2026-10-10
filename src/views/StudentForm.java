package views;

import models.Appointment;
import models.DocumentRequest;
import utils.CSVManager;
import utils.LogoLoader;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

/**
 * Student Appointment Form for City College of Calapan — laid out and ruled like the
 * paper registrar forms it replaces: a letterhead, numbered sections, and lined fields
 * instead of a dashboard-style stepper.
 */
public class StudentForm extends JFrame {

    private static final String[] YEARS = {"1st Year", "2nd Year", "3rd Year", "4th Year"};
    private static final String[] DOCUMENTS = {
            "Certificate of Registration (COR)",
            "Certificate of Enrollment / Bonafide Student",
            "Certificate of Year Standing",
            "Certificate of Grades (per term)",
            "Transcript of Records (TOR)",
            "Certificate of GWA",
            "Certificate of Units Earned",
            "Certificate of No Pending Disciplinary Case",
            "Certificate of No Contract / No Outstanding Obligation",
            "Honorable Dismissal / Transfer Credential",
            "Certificate of Candidacy for Graduation",
            "Certificate of Graduation / Completion",
            "Diploma / 2nd Copy of Diploma",
            "Certified True Copy (CTC) of any document",
            "Certificate of English as Medium of Instruction",
            "OJT / Internship Clearance",
            "Expected Date of Graduation"
    };
    private static final DateTimeFormatter DATE_LONG =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    // ----- Palette -----
    // Ink on paper. The school's orange appears only on the one action that submits the
    // form; everything else is black, muted ink, or a ruled line — the way a real form reads.
    private static final Color COLOR_PRIMARY    = new Color(217, 101, 14);  // City College orange — submit action only
    private static final Color COLOR_INK        = new Color(26, 24, 22);    // body text
    private static final Color COLOR_MUTED      = new Color(96, 90, 82);    // hints, counts, fine print
    private static final Color COLOR_RULE       = new Color(74, 68, 60);    // heading / divider rules
    private static final Color COLOR_FIELD_LINE = new Color(176, 169, 156); // the line under a blank
    private static final Color COLOR_PAPER      = new Color(250, 248, 244); // the sheet itself
    private static final Color COLOR_DANGER     = new Color(150, 32, 24);   // remove / correction
    private static final Color COLOR_OK         = new Color(45, 106, 60);   // slots-available note

    private final ArrayList<DocumentRequest> cart = new ArrayList<>();
    private final JPanel cartPanel = new JPanel();

    // Section I controls
    private final JTextField idField = createLinedField();
    private final JTextField nameField = createLinedField();
    private final JComboBox<String> courseBox = createStyledComboBox(new String[]{"Bachelor of Science in Hospitality Management", "Bachelor of Science in Tourism Management", "Bachelor of Library and Information Science", "Bachelor of Science in Information Systems", "Bachelor of Secondary Education, Major in Science","Bachelor of Secondary Education, Major in Mathematics","Bachelor of Special Needs Education","Bachelor of Public Administration"});
    private final JComboBox<String> yearBox = createStyledComboBox(YEARS);

    // Section II controls
    private final JComboBox<String> docBox = createStyledComboBox(DOCUMENTS);
    private final JComboBox<String> docYearBox = createStyledComboBox(YEARS);
    private final JComboBox<String> semBox = createStyledComboBox(new String[]{"1st Sem", "2nd Sem"});
    private final JTextArea purposeArea = new JTextArea(3, 20);

    // Section III controls
    private final JButton dateBtn = new JButton();
    private final JLabel dateInfo = new JLabel(" ", SwingConstants.CENTER);
    private LocalDate appointmentDate = firstAvailableDate();

    public StudentForm() {
        configureLookAndFeel();

        setTitle("City College of Calapan - Appointment Portal");
        setSize(520, 840);
        setMinimumSize(new Dimension(420, 700));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        LogoLoader.applyWindowIcon(this);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildFormArea(), BorderLayout.CENTER);
        add(buildBottomBar(), BorderLayout.SOUTH);

        refreshCart();
        updateDateLabel();

        // Keep the slot count live: every few seconds, pick up a changed daily limit
        // (or new bookings) without the student having to reopen the form.
        slotRefreshTimer = new javax.swing.Timer(5000, e -> {
            if (isShowing()) updateDateLabel();
        });
        slotRefreshTimer.start();
    }

    private javax.swing.Timer slotRefreshTimer;

    @Override
    public void dispose() {
        if (slotRefreshTimer != null) slotRefreshTimer.stop();
        super.dispose();
    }

    private void configureLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Fallback gracefully to default Swing rendering
        }
    }

    // ---------------------------------------------------------------- Letterhead
    private JComponent buildHeader() {
        JLabel logo = LogoLoader.label(46);
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel school = new JLabel("CITY COLLEGE OF CALAPAN");
        school.setFont(new Font(Font.SERIF, Font.BOLD, 16));
        school.setForeground(COLOR_INK);
        school.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel office = new JLabel("Office of the Registrar");
        office.setFont(new Font(Font.SERIF, Font.PLAIN, 11));
        office.setForeground(COLOR_MUTED);
        office.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel formTitle = new JLabel("Appointment and Document Request Form");
        formTitle.setFont(new Font(Font.SERIF, Font.BOLD, 13));
        formTitle.setForeground(COLOR_INK);
        formTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(COLOR_PAPER);
        header.setBorder(new EmptyBorder(14, 16, 12, 16));

        header.add(logo);
        header.add(Box.createRigidArea(new Dimension(0, 6)));
        header.add(school);
        header.add(Box.createRigidArea(new Dimension(0, 2)));
        header.add(office);
        header.add(Box.createRigidArea(new Dimension(0, 10)));
        header.add(createRule(2));
        header.add(Box.createRigidArea(new Dimension(0, 8)));
        header.add(formTitle);

        return header;
    }

    // ------------------------------------------------------------------ Form Sheet
    private JComponent buildFormArea() {
        JPanel sheet = new FormSheet();
        sheet.setLayout(new BoxLayout(sheet, BoxLayout.Y_AXIS));
        sheet.setBorder(new EmptyBorder(16, 20, 18, 20));

        sheet.add(buildSectionI());
        sheet.add(Box.createRigidArea(new Dimension(0, 16)));
        sheet.add(createRule(1));
        sheet.add(Box.createRigidArea(new Dimension(0, 16)));

        sheet.add(buildSectionII());
        sheet.add(Box.createRigidArea(new Dimension(0, 16)));
        sheet.add(createRule(1));
        sheet.add(Box.createRigidArea(new Dimension(0, 16)));

        sheet.add(buildSectionIII());

        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(COLOR_PAPER);
        outer.setBorder(new EmptyBorder(10, 16, 16, 16));
        outer.add(sheet, BorderLayout.CENTER);

        JScrollPane scrollPane = new JScrollPane(outer);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(COLOR_PAPER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scrollPane;
    }

    // ---------------------------------------------------------------- Section I
    private JComponent buildSectionI() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setOpaque(false);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        section.add(createSectionHeading("I", "Student Information"));
        section.add(Box.createRigidArea(new Dimension(0, 10)));

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        addFormRow(grid, 0, "Student ID No.", idField);
        addFormRow(grid, 1, "Full Name", nameField);

        // Course names are long, so Course gets the full width of the sheet (Year Level sits
        // underneath it) instead of half a row.
        styleCourseBox();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 0, 2, 0);
        grid.add(createLabeledGroup("Course", courseBox), gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 0, 2, 0);
        grid.add(createLabeledGroup("Year Level", yearBox), gbc);

        section.add(grid);
        return section;
    }

    // ---------------------------------------------------------------- Section II
    private JComponent buildSectionII() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setOpaque(false);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        section.add(createSectionHeading("II", "Document Request"));
        section.add(Box.createRigidArea(new Dimension(0, 10)));

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 8, 0);
        content.add(createLabeledGroup("Document Needed", docBox), gbc);

        JPanel termGrid = new JPanel(new GridLayout(1, 2, 12, 0));
        termGrid.setOpaque(false);
        termGrid.add(createLabeledGroup("School Year", docYearBox));
        termGrid.add(createLabeledGroup("Term", semBox));

        gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 10, 0);
        content.add(termGrid, gbc);

        JButton addBtn = createOutlineButton("Add to Request List", COLOR_INK);
        addBtn.addActionListener(e -> addDocument());

        gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 0, 14, 0);
        content.add(addBtn, gbc);

        JLabel cartLabel = createLabel("Requested Documents", true);
        gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 0, 4, 0);
        content.add(cartLabel, gbc);

        cartPanel.setLayout(new BoxLayout(cartPanel, BoxLayout.Y_AXIS));
        cartPanel.setBackground(Color.WHITE);
        JScrollPane cartScroll = new JScrollPane(cartPanel);
        cartScroll.setPreferredSize(new Dimension(280, 100));
        cartScroll.setBorder(new LineBorder(COLOR_FIELD_LINE, 1, false));

        gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        gbc.weightx = 1.0; gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 12, 0);
        content.add(cartScroll, gbc);

        JLabel purposeLabel = createLabel("Purpose of Request", true);
        gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 0, 4, 0);
        content.add(purposeLabel, gbc);

        purposeArea.setFont(new Font(Font.SERIF, Font.PLAIN, 12));
        purposeArea.setForeground(COLOR_INK);
        purposeArea.setCaretColor(COLOR_INK);
        purposeArea.setLineWrap(true);
        purposeArea.setWrapStyleWord(true);
        purposeArea.setBorder(new EmptyBorder(6, 8, 6, 8));

        JScrollPane purposeScroll = new JScrollPane(purposeArea);
        purposeScroll.setPreferredSize(new Dimension(280, 65));
        purposeScroll.setBorder(new LineBorder(COLOR_FIELD_LINE, 1, false));

        gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        content.add(purposeScroll, gbc);

        section.add(content);
        return section;
    }

    // ---------------------------------------------------------------- Section III
    private JComponent buildSectionIII() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setOpaque(false);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        section.add(createSectionHeading("III", "Appointment Schedule"));
        section.add(Box.createRigidArea(new Dimension(0, 10)));

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setAlignmentX(Component.LEFT_ALIGNMENT);

        dateBtn.setFocusPainted(false);
        dateBtn.setFont(new Font(Font.SERIF, Font.BOLD, 12));
        dateBtn.setForeground(COLOR_INK);
        dateBtn.setBackground(Color.WHITE);
        dateBtn.setBorder(new CompoundBorder(new LineBorder(COLOR_RULE, 1, false), new EmptyBorder(8, 12, 8, 12)));
        dateBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        dateBtn.addActionListener(e -> openDatePicker());

        JLabel dateLabel = createLabel("Preferred Date", true);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(4, 0, 4, 10);
        content.add(dateLabel, gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 1; gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        content.add(dateBtn, gbc);

        dateInfo.setFont(new Font(Font.SERIF, Font.PLAIN, 11));
        dateInfo.setHorizontalAlignment(SwingConstants.LEFT);

        gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 0, 2, 0);
        content.add(dateInfo, gbc);

        section.add(content);
        return section;
    }

    // ---------------------------------------------------------------- Design Helpers
    private JLabel createSectionHeading(String numeral, String title) {
        JLabel heading = new JLabel(numeral + ".  " + title);
        heading.setFont(new Font(Font.SERIF, Font.BOLD, 14));
        heading.setForeground(COLOR_INK);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        return heading;
    }

    // A plain ruled line — used as the letterhead divider and between the form's three
    // numbered sections, in place of separate floating cards with gaps and shadows.
    private JComponent createRule(int thickness) {
        JPanel rule = new JPanel();
        rule.setBackground(COLOR_RULE);
        rule.setPreferredSize(new Dimension(10, thickness));
        rule.setMaximumSize(new Dimension(Integer.MAX_VALUE, thickness));
        rule.setAlignmentX(Component.CENTER_ALIGNMENT);
        return rule;
    }

    private JPanel createLabeledGroup(String labelText, JComponent component) {
        JPanel group = new JPanel();
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setOpaque(false);

        JLabel label = createLabel(labelText, true);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.add(label);
        group.add(Box.createRigidArea(new Dimension(0, 4)));
        group.add(component);

        return group;
    }

    private void addFormRow(JPanel panel, int row, String labelText, JComponent inputComponent) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = row;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 0, 6, 12);

        JLabel label = createLabel(labelText, true);
        panel.add(label, c);

        c = new GridBagConstraints();
        c.gridx = 1; c.gridy = row;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 6, 0);
        panel.add(inputComponent, c);
    }

    private JLabel createLabel(String text, boolean bold) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.SERIF, bold ? Font.BOLD : Font.PLAIN, 12));
        label.setForeground(COLOR_INK);
        return label;
    }

    // Underline-only field — a blank to fill in, the way the paper version of this form
    // would present it, rather than a boxed input.
    private JTextField createLinedField() {
        JTextField field = new JTextField();
        field.setFont(new Font(Font.SERIF, Font.PLAIN, 13));
        field.setForeground(COLOR_INK);
        field.setCaretColor(COLOR_INK);
        field.setBackground(Color.WHITE);
        field.setPreferredSize(new Dimension(0, 28));
        field.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_FIELD_LINE),
                new EmptyBorder(2, 2, 4, 2)
        ));
        return field;
    }

    private JComboBox<String> createStyledComboBox(String[] items) {
        JComboBox<String> box = new JComboBox<>(items);
        box.setFont(new Font(Font.SERIF, Font.PLAIN, 12));
        box.setForeground(COLOR_INK);
        box.setBackground(Color.WHITE);
        box.setPreferredSize(new Dimension(0, 32));
        return box;
    }

    /**
     * Course dropdown: each course wraps onto as many lines as it needs inside the list, so
     * nothing is cut off and similar names (e.g. the two Secondary Education majors) can be
     * told apart. The closed box shows the selection on one line, with the full name as a tooltip.
     */
    private void styleCourseBox() {
        courseBox.setRenderer(new WrappingComboRenderer(courseBox));
        courseBox.setMaximumRowCount(courseBox.getItemCount());
        courseBox.setToolTipText(String.valueOf(courseBox.getSelectedItem()));
        courseBox.addActionListener(e -> courseBox.setToolTipText(String.valueOf(courseBox.getSelectedItem())));
    }

    private static class WrappingComboRenderer extends DefaultListCellRenderer {
        private static final Color ROW_SELECT = new Color(240, 232, 218);
        private static final Color ROW_LINE   = new Color(232, 226, 216);

        private final JComboBox<?> combo;

        WrappingComboRenderer(JComboBox<?> combo) {
            this.combo = combo;
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean hasFocus) {
            super.getListCellRendererComponent(list, value, index, selected, hasFocus);
            String text = value == null ? "" : value.toString();

            if (index < 0) {            // the closed box: single line, ellipsis if too long
                setText(text);
                return this;
            }

            int textWidth = Math.max(120, combo.getWidth() - 40);
            String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            setText("<html><body style='width: " + textWidth + "px'>" + safe + "</body></html>");
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, ROW_LINE),
                    new EmptyBorder(5, 8, 5, 8)));
            setBackground(selected ? ROW_SELECT : Color.WHITE);
            setForeground(COLOR_INK);
            return this;
        }
    }

    private JButton createOutlineButton(String text, Color color) {
        JButton btn = new JButton(text);
        btn.setFont(new Font(Font.SERIF, Font.BOLD, 11));
        btn.setForeground(color);
        btn.setBackground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new LineBorder(COLOR_RULE, 1, false));
        btn.setPreferredSize(new Dimension(0, 30));
        return btn;
    }

    // ---------------------------------------------------------------- Cart Logic
    private void addDocument() {
        DocumentRequest req = new DocumentRequest(
                String.valueOf(docBox.getSelectedItem()),
                String.valueOf(docYearBox.getSelectedItem()),
                String.valueOf(semBox.getSelectedItem()));

        for (DocumentRequest existing : cart) {
            if (existing.toString().equals(req.toString())) {
                JOptionPane.showMessageDialog(this, "That document is already in your request list.", "Duplicate Document", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        }
        cart.add(req);
        refreshCart();
    }

    private void refreshCart() {
        cartPanel.removeAll();

        if (cart.isEmpty()) {
            JLabel empty = new JLabel("No documents added yet.", SwingConstants.CENTER);
            empty.setFont(new Font(Font.SERIF, Font.ITALIC, 11));
            empty.setForeground(COLOR_MUTED);
            empty.setBorder(BorderFactory.createEmptyBorder(12, 6, 12, 6));
            cartPanel.add(empty);
        } else {
            for (int i = 0; i < cart.size(); i++) {
                final int index = i;

                JButton removeBtn = new JButton("remove");
                removeBtn.setForeground(COLOR_DANGER);
                removeBtn.setFont(new Font(Font.SERIF, Font.PLAIN, 11));
                removeBtn.setBorderPainted(false);
                removeBtn.setContentAreaFilled(false);
                removeBtn.setFocusable(false);
                removeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                removeBtn.setToolTipText("Remove this document from the request");
                removeBtn.addActionListener(e -> {
                    cart.remove(index);
                    refreshCart();
                });

                JPanel row = new JPanel(new BorderLayout(6, 0));
                row.setBackground(Color.WHITE);
                row.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_FIELD_LINE),
                        new EmptyBorder(4, 8, 4, 8)
                ));
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

                JLabel docLabel = createLabel((i + 1) + ". " + cart.get(i), false);
                row.add(docLabel, BorderLayout.CENTER);
                row.add(removeBtn, BorderLayout.EAST);
                cartPanel.add(row);
            }
        }

        cartPanel.add(Box.createVerticalGlue());
        cartPanel.revalidate();
        cartPanel.repaint();
    }

    // ---------------------------------------------------------------- Date Selection
    private static LocalDate firstAvailableDate() {
        Map<LocalDate, Integer> counts = CSVManager.countByDate();
        int max = CSVManager.getMaxPerDay();
        LocalDate day = LocalDate.now();
        for (int i = 0; i < 365; i++, day = day.plusDays(1)) {
            if (counts.getOrDefault(day, 0) < max) return day;
        }
        return null;
    }

    private void openDatePicker() {
        LocalDate picked = YearCalendarDialog.choose(this, YearCalendarDialog.Mode.STUDENT, appointmentDate);
        if (picked != null) {
            appointmentDate = picked;
            updateDateLabel();
        }
    }

    private void updateDateLabel() {
        if (appointmentDate == null) {
            dateBtn.setText("Click to choose an appointment date...");
            dateInfo.setText("No date selected.");
            dateInfo.setForeground(COLOR_MUTED);
            return;
        }
        dateBtn.setText(appointmentDate.format(DATE_LONG));

        int max = CSVManager.getMaxPerDay();
        int left = Math.max(0, max - CSVManager.countForDate(appointmentDate));
        if (left == 0) {
            dateInfo.setText("No slots left on this day (limit: " + max + ")");
            dateInfo.setForeground(COLOR_DANGER);
        } else {
            dateInfo.setText(left + " of " + max + " slots available on this day");
            // turns orange when 20% or less is left (that is 10 slots at the default limit of 50)
            dateInfo.setForeground(left <= Math.max(1, max / 5) ? COLOR_PRIMARY : COLOR_OK);
        }
    }

    // ------------------------------------------------------------ Bottom Bar
    private JComponent buildBottomBar() {
        JPanel bar = new JPanel(new GridLayout(2, 1, 0, 8));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_RULE),
                new EmptyBorder(12, 16, 14, 16)
        ));
        bar.setBackground(COLOR_PAPER);

        bar.add(buildSubmitButton());
        bar.add(buildBackButton());
        return bar;
    }

    private JButton buildBackButton() {
        JButton backBtn = createOutlineButton("Back to Main Menu", COLOR_MUTED);
        backBtn.addActionListener(e -> {
            boolean hasData = !idField.getText().trim().isEmpty()
                    || !nameField.getText().trim().isEmpty()
                    || !purposeArea.getText().trim().isEmpty()
                    || !cart.isEmpty();

            if (hasData) {
                int choice = JOptionPane.showConfirmDialog(this,
                        "Are you sure you want to go back?\nUnsubmitted information will be lost.",
                        "Confirm Navigation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) return;
            }
            new GatewayScreen().setVisible(true);
            dispose();
        });
        return backBtn;
    }

    private JButton buildSubmitButton() {
        JButton submitBtn = new JButton("Submit Appointment Request");
        submitBtn.setFont(new Font(Font.SERIF, Font.BOLD, 13));
        submitBtn.setBackground(COLOR_PRIMARY);
        submitBtn.setForeground(Color.black);
        submitBtn.setFocusPainted(false);
        submitBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        submitBtn.setPreferredSize(new Dimension(0, 36));
        submitBtn.addActionListener(e -> submit());
        return submitBtn;
    }

    // ---------------------------------------------------------------- Submit Logic
    private void submit() {
        String id = idField.getText().trim();
        String name = nameField.getText().trim();
        String purpose = purposeArea.getText().replaceAll("\\s+", " ").trim();

        if (id.isEmpty() || name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter your ID Number and Name.", "Missing Information", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!id.matches("\\d+(-\\d+)*")) {
            JOptionPane.showMessageDialog(this, "ID Number must contain digits and hyphens only (e.g. 2024-00123).", "Invalid Format", JOptionPane.WARNING_MESSAGE);
            idField.requestFocus();
            return;
        }
        if (cart.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please add at least one document to your request.", "Empty Request", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (purpose.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please specify the purpose/reason for your request.", "Missing Purpose", JOptionPane.WARNING_MESSAGE);
            purposeArea.requestFocus();
            return;
        }
        if (appointmentDate == null) {
            JOptionPane.showMessageDialog(this, "Please choose your appointment date.", "Missing Date", JOptionPane.WARNING_MESSAGE);
            openDatePicker();
            return;
        }
        if (appointmentDate.isBefore(LocalDate.now())) {
            JOptionPane.showMessageDialog(this, "The selected date has already passed. Please select a valid future date.", "Invalid Date", JOptionPane.WARNING_MESSAGE);
            openDatePicker();
            return;
        }

        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH));
        String courseAndYear = courseBox.getSelectedItem() + " - " + yearBox.getSelectedItem();

        Appointment app = new Appointment(appointmentDate, LocalDate.now(), time, id, name,
                courseAndYear, purpose, new ArrayList<>(cart), "PENDING");

        if (!CSVManager.saveIfAvailable(app)) {
            JOptionPane.showMessageDialog(this,
                    appointmentDate.format(DATE_LONG) + " is fully booked (" + CSVManager.getMaxPerDay() + " appointments limit reached).\nPlease pick another date.",
                    "Date Fully Booked", JOptionPane.WARNING_MESSAGE);
            appointmentDate = null;
            updateDateLabel();
            openDatePicker();
            return;
        }

        JOptionPane.showMessageDialog(this,
                "Appointment Submitted Successfully!\nYour scheduled date: " + appointmentDate.format(DATE_LONG),
                "Success", JOptionPane.INFORMATION_MESSAGE);
        new GatewayScreen().setVisible(true);
        dispose();
    }

    /**
     * The form page itself — a plain white sheet with a square hairline border, the way a
     * real printed or scanned registrar form reads. No rounded corners, no drop shadow.
     */
    private static class FormSheet extends JPanel {
        FormSheet() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(COLOR_RULE);
            g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}