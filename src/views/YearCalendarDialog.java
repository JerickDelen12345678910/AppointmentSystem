package views;

import utils.CSVManager;
import utils.LogoLoader;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;


public class YearCalendarDialog extends JDialog {
    public enum Mode { ADMIN, STUDENT }

    private static final int MAX = CSVManager.MAX_PER_DAY;
    private static final int ALMOST_FULL = (int) Math.ceil(MAX * 0.8);   // 40 of 50

    private static final Color OPEN_BG = Color.WHITE;
    private static final Color BOOKED_BG = new Color(255, 238, 170);
    private static final Color ALMOST_BG = new Color(255, 184, 77);
    private static final Color FULL_BG = new Color(220, 60, 60);
    private static final Color SELECTED_BG = new Color(25, 60, 150);
    private static final Color PAST_BG = new Color(238, 238, 238);
    private static final Color PAST_FG = new Color(165, 165, 165);
    private static final Color TODAY_RING = new Color(25, 60, 150);

    private static final DateTimeFormatter LONG_DATE =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    private final Mode mode;
    private final LocalDate today = LocalDate.now();
    private final LocalDate current;                 // the day that is currently selected (may be null)
    private final Map<LocalDate, Integer> counts;    // day -> appointments booked
    private final int minYear;
    private final int maxYear;

    private final JPanel monthsPanel = new JPanel(new GridLayout(3, 4, 10, 10));
    private final JLabel yearLabel = new JLabel("", SwingConstants.CENTER);
    private final JButton prevBtn = new JButton("\u2190");
    private final JButton nextBtn = new JButton("\u2192");

    private int year;
    private LocalDate result = null;

    public static LocalDate choose(Window owner, Mode mode, LocalDate current) {
        YearCalendarDialog dialog = new YearCalendarDialog(owner, mode, current);
        dialog.setVisible(true);   // modal: returns when the dialog is closed
        return dialog.result;
    }

    private YearCalendarDialog(Window owner, Mode mode, LocalDate current) {
        super(owner, mode == Mode.ADMIN ? "Year Calendar" : "Choose Appointment Date",
                Dialog.ModalityType.APPLICATION_MODAL);
        this.mode = mode;
        this.current = current;
        this.counts = CSVManager.countByDate();
        this.minYear = mode == Mode.STUDENT ? today.getYear() : 2000;
        this.maxYear = mode == Mode.STUDENT ? today.getYear() + 1 : 2100;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        LogoLoader.applyWindowIcon(this);
        setLayout(new BorderLayout());

        monthsPanel.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        JScrollPane scroll = new JScrollPane(monthsPanel);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        add(buildTop(), BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);

        int start = current != null ? current.getYear() : today.getYear();
        showYear(Math.max(minYear, Math.min(maxYear, start)));

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(getWidth(), screen.width - 40), Math.min(getHeight(), screen.height - 40));
        setLocationRelativeTo(owner);
    }

    private JComponent buildTop() {
        JLabel title = new JLabel(mode == Mode.ADMIN ? "SELECT A DATE" : "CHOOSE YOUR APPOINTMENT DATE");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));

        JLabel hint = new JLabel(mode == Mode.ADMIN
                ? "Click any day to see the students who booked it."
                : "Red days are full (" + MAX + " appointments max). Please pick another day.");
        hint.setForeground(Color.DARK_GRAY);

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(title);
        text.add(Box.createRigidArea(new Dimension(0, 3)));
        text.add(hint);

        yearLabel.setFont(yearLabel.getFont().deriveFont(Font.BOLD, 20f));
        yearLabel.setPreferredSize(new Dimension(90, 30));
        for (JButton b : new JButton[]{prevBtn, nextBtn}) {
            b.setFocusPainted(false);
            b.setFont(b.getFont().deriveFont(Font.BOLD, 14f));
        }
        prevBtn.setToolTipText("Previous year");
        nextBtn.setToolTipText("Next year");
        prevBtn.addActionListener(e -> showYear(year - 1));
        nextBtn.addActionListener(e -> showYear(year + 1));

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        nav.add(prevBtn);
        nav.add(yearLabel);
        nav.add(nextBtn);

        JPanel top = new JPanel(new BorderLayout());
        top.add(text, BorderLayout.WEST);
        top.add(nav, BorderLayout.EAST);
        top.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        return top;
    }

    private JComponent buildBottom() {
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        legend.add(legendItem(OPEN_BG, Color.GRAY, mode == Mode.ADMIN ? "No appointments" : "Open"));
        legend.add(legendItem(BOOKED_BG, Color.GRAY, mode == Mode.ADMIN ? "Has appointments" : "Some slots taken"));
        legend.add(legendItem(ALMOST_BG, Color.GRAY, "Almost full (" + ALMOST_FULL + "+)"));
        legend.add(legendItem(FULL_BG, Color.GRAY, "FULL (" + MAX + ")"));
        legend.add(legendItem(OPEN_BG, TODAY_RING, "Today"));
        legend.add(legendItem(SELECTED_BG, SELECTED_BG, "Selected"));

        JButton close = new JButton("CANCEL");
        close.setFocusPainted(false);
        close.addActionListener(e -> dispose());

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(legend, BorderLayout.CENTER);
        bottom.add(close, BorderLayout.EAST);
        bottom.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(8, 8, 8, 14)));
        return bottom;
    }

    private JComponent legendItem(Color fill, Color outline, String text) {
        JComponent box = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(fill);
                g.fillRect(0, 0, getWidth(), getHeight());
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(outline);
                g2.setStroke(new BasicStroke(outline == TODAY_RING ? 2f : 1f));
                g2.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
            }
        };
        box.setPreferredSize(new Dimension(16, 16));

        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        item.add(box);
        item.add(new JLabel(text));
        return item;
    }

    private void showYear(int newYear) {
        year = newYear;
        yearLabel.setText(String.valueOf(year));
        prevBtn.setEnabled(year > minYear);
        nextBtn.setEnabled(year < maxYear);

        monthsPanel.removeAll();
        for (Month m : Month.values()) monthsPanel.add(buildMonth(m));
        monthsPanel.revalidate();
        monthsPanel.repaint();
    }

    private JComponent buildMonth(Month month) {
        JLabel title = new JLabel(month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase(),
                SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));

        JPanel grid = new JPanel(new GridLayout(7, 7, 1, 1));
        grid.setOpaque(false);
        for (String d : new String[]{"S", "M", "T", "W", "T", "F", "S"}) {
            JLabel l = new JLabel(d, SwingConstants.CENTER);
            l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
            l.setForeground(Color.GRAY);
            grid.add(l);
        }

        YearMonth ym = YearMonth.of(year, month);
        int offset = ym.atDay(1).getDayOfWeek().getValue() % 7;   // Sunday = 0
        for (int i = 0; i < 42; i++) {
            int day = i - offset + 1;
            if (day < 1 || day > ym.lengthOfMonth()) grid.add(new JLabel());
            else grid.add(new DayCell(ym.atDay(day)));
        }

        JPanel panel = new JPanel(new BorderLayout(0, 3));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 210, 210)),
                BorderFactory.createEmptyBorder(5, 6, 6, 6)));
        panel.add(title, BorderLayout.NORTH);
        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    private void pick(LocalDate date) {
        result = date;
        dispose();
    }

    private class DayCell extends JComponent {
        private final LocalDate date;
        private final int count;
        private final boolean past;
        private final boolean full;
        private final boolean selectable;
        private boolean hover = false;

        DayCell(LocalDate date) {
            this.date = date;
            this.count = counts.getOrDefault(date, 0);
            this.past = date.isBefore(today);
            this.full = count >= MAX;
            this.selectable = mode == Mode.ADMIN || (!past && !full);
            setPreferredSize(new Dimension(28, 20));
            setToolTipText(tooltip());

            boolean blockedFull = mode == Mode.STUDENT && !past && full;
            if (selectable || blockedFull) {
                setCursor(Cursor.getPredefinedCursor(selectable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                    @Override public void mouseReleased(MouseEvent e) {
                        if (!contains(e.getPoint())) return;
                        if (selectable) {
                            pick(date);
                        } else {
                            JOptionPane.showMessageDialog(YearCalendarDialog.this,
                                    date.format(LONG_DATE) + " is already full (" + MAX
                                            + " appointments).\nPlease pick another day.",
                                    "Day Full", JOptionPane.WARNING_MESSAGE);
                        }
                    }
                });
            }
        }

        private String tooltip() {
            String head = "<html><b>" + date.format(LONG_DATE) + "</b><br>";
            if (mode == Mode.ADMIN) {
                if (count == 0) return head + "No appointments</html>";
                return head + count + " / " + MAX + " appointments" + (full ? " (FULL)" : "") + "</html>";
            }
            if (past) return head + "This date has already passed</html>";
            if (full) return head + "FULL - please pick another day</html>";
            int left = MAX - count;
            return head + left + (left == 1 ? " slot left" : " slots left") + "</html>";
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();

            boolean dimmed = mode == Mode.STUDENT && past;
            boolean isSelected = date.equals(current);

            Color bg, fg = Color.BLACK;
            if (isSelected) { bg = SELECTED_BG; fg = Color.WHITE; }
            else if (dimmed) { bg = PAST_BG; fg = PAST_FG; }
            else if (full) { bg = FULL_BG; fg = Color.WHITE; }
            else if (count >= ALMOST_FULL) bg = ALMOST_BG;
            else if (count > 0) bg = BOOKED_BG;
            else bg = OPEN_BG;

            g2.setColor(bg);
            g2.fillRoundRect(1, 1, w - 2, h - 2, 8, 8);

            if (date.equals(today)) {
                g2.setColor(TODAY_RING);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, 8, 8);
            } else if (hover) {
                g2.setColor(Color.DARK_GRAY);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, 8, 8);
            }

            Font f = getFont().deriveFont(isSelected || date.equals(today) ? Font.BOLD : Font.PLAIN, 11f);
            g2.setFont(f);
            g2.setColor(fg);
            String s = String.valueOf(date.getDayOfMonth());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(s, (w - fm.stringWidth(s)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }
}
