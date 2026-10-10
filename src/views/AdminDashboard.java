package views;

import models.Appointment;
import models.DocumentRequest;
import utils.CSVManager;
import utils.LogoLoader;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.font.TextAttribute;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Registrar Dashboard for City College of Calapan.
 *
 * The dashboard itself (orange sidebar, top bar, summary cards, appointment table) is a
 * modern, card-based layout in the college's orange. The appointment detail view -- the
 * "Appointment and Document Request Form" sheet -- keeps its original ink-on-paper design
 * exactly as it was, since it mirrors the printed form.
 */
public class AdminDashboard extends JFrame {

    private static final boolean SEND_TO_PRINTER = false;

    private static final DateTimeFormatter DATE_LABEL =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter LONG_DATE =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    // ----- Palette (shared with GatewayScreen / StudentForm) -----
    private static final Color COLOR_PRIMARY     = new Color(217, 101, 14);  // City College orange — actions only
    private static final Color COLOR_INK         = new Color(26, 24, 22);    // body text
    private static final Color COLOR_MUTED       = new Color(96, 90, 82);    // hints, fine print
    private static final Color COLOR_RULE        = new Color(74, 68, 60);    // heading / divider rules
    private static final Color COLOR_FIELD_LINE  = new Color(176, 169, 156); // table grid, light borders
    private static final Color COLOR_PAPER       = new Color(250, 248, 244); // page background
    private static final Color COLOR_DANGER      = new Color(150, 32, 24);   // already printed
    private static final Color COLOR_OK          = new Color(45, 106, 60);   // printed status, slots free
    private static final Color COLOR_ROW_TINT    = new Color(253, 231, 212); // selected row / pending tag

    // ----- Dashboard palette (orange-led) -----
    private static final Color SIDEBAR_TOP       = new Color(206, 93, 10);
    private static final Color SIDEBAR_BOTTOM    = new Color(156, 60, 4);
    private static final Color SIDEBAR_SOFT_TEXT = new Color(255, 228, 202);
    private static final Color NAV_ACTIVE_TEXT   = new Color(176, 70, 4);
    private static final Color ORANGE_HOVER      = new Color(196, 87, 8);
    private static final Color ORANGE_WASH       = new Color(255, 247, 240);
    private static final Color CARD_BORDER       = new Color(234, 227, 216);
    private static final Color CONTROL_BORDER    = new Color(222, 209, 192);
    private static final Color ROW_LINE          = new Color(243, 238, 229);
    private static final Color ROW_HOVER         = new Color(255, 250, 245);
    private static final Color ROW_SELECT        = new Color(255, 238, 224);
    private static final Color AMBER             = new Color(196, 124, 4);
    private static final Color AMBER_TINT        = new Color(255, 241, 208);
    private static final Color GREEN_TINT        = new Color(223, 240, 228);
    private static final Color BROWN             = new Color(124, 84, 52);
    private static final Color BROWN_TINT        = new Color(242, 232, 222);
    private static final Color TRACK             = new Color(241, 234, 223);

    private static final String SANS_FAMILY = pickSans();

    private static final int COL_DOCUMENTS = 3;
    private static final int COL_PURPOSE = 4;
    private static final int COL_STATUS = 5;

    private enum Filter { ALL, PENDING, PRINTED }

    private final String adminName;

    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel listTitle = new JLabel();
    private final JLabel listSub = new JLabel();
    private final JLabel footerCount = new JLabel();
    private final JLabel emptyTitle = new JLabel();
    private final JLabel emptySub = new JLabel();
    private final JLabel pageTitle = new JLabel();
    private final JLabel pageSubtitle = new JLabel();
    private final JPanel detailsPanel = new JPanel(new GridBagLayout());
    private final JButton printBtn = new JButton("Print Request Form");

    private final CardLayout mainCardLayout = new CardLayout();
    private final JPanel contentContainer = new JPanel(mainCardLayout);
    private final CardLayout tableCards = new CardLayout();
    private final JPanel tableHolder = new JPanel(tableCards);

    private final FlatButton[] chips = new FlatButton[3];
    private FlatButton backBtn;
    private FlatButton dateBtn;
    private SearchField searchField;
    private StatCard statBooked;
    private StatCard statPending;
    private StatCard statPrinted;
    private StatCard statDocs;

    private List<Appointment> allAppointments = new ArrayList<>();
    private final List<Appointment> visibleRows = new ArrayList<>();
    private Appointment selected = null;
    private LocalDate selectedDate = LocalDate.now();
    private Filter statusFilter = Filter.ALL;
    private int hoverRow = -1;

    public AdminDashboard(String adminName) {
        this.adminName = adminName;
        configureLookAndFeel();

        setTitle("City College of Calapan - Registrar Portal");
        setSize(1280, 820);
        setMinimumSize(new Dimension(1080, 700));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(COLOR_PAPER);
        LogoLoader.applyWindowIcon(this);

        // ----- Table Setup -----
        String[] columns = {"Time", "Student ID", "Student Name", "Requested Documents", "Purpose / Reason", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };

        table = new JTable(tableModel) {
            @Override
            public String getToolTipText(MouseEvent e) {
                int row = rowAtPoint(e.getPoint());
                int col = columnAtPoint(e.getPoint());
                if (row < 0 || row >= visibleRows.size()) return null;

                Appointment a = visibleRows.get(row);
                if (col == COL_DOCUMENTS) {
                    StringBuilder sb = new StringBuilder("<html>");
                    int n = 1;
                    for (DocumentRequest d : a.getDocuments()) {
                        if (n > 1) sb.append("<br>");
                        sb.append(n++).append(". ").append(escapeHtml(d.getDisplayName()));
                    }
                    return sb.append("</html>").toString();
                }
                if (col == COL_PURPOSE) {
                    return "<html><body style='width:300px'>" + escapeHtml(a.getPurpose()) + "</body></html>";
                }
                return null;
            }
        };
        styleTable();

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = table.getSelectedRow();
            if (row >= 0 && row < visibleRows.size()) {
                Appointment a = visibleRows.get(row);
                showDetails(a);
                setTopBarMode(true, a);
                mainCardLayout.show(contentContainer, "DETAILS");
            }
        });

        // Print Request Form Button Styling Default
        stylePrintButton(false);
        printBtn.setFocusPainted(false);
        printBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        printBtn.setPreferredSize(new Dimension(0, 44));
        printBtn.addActionListener(e -> printSelected());

        // Views Assembly
        contentContainer.setBackground(COLOR_PAPER);
        contentContainer.add(buildDashboard(), "TABLE");
        contentContainer.add(buildDetailsBox(), "DETAILS");

        JPanel mainArea = new JPanel(new BorderLayout());
        mainArea.setBackground(COLOR_PAPER);
        mainArea.add(buildTopBar(), BorderLayout.NORTH);
        mainArea.add(contentContainer, BorderLayout.CENTER);

        add(buildSidebarNav(), BorderLayout.WEST);
        add(mainArea, BorderLayout.CENTER);

        setTopBarMode(false, null);
        loadTableData();
    }

    private void configureLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }

    // Dynamic Print Button Style Handler — filled orange when actionable, outlined when
    // already done, following the same fill-vs-outline hierarchy used throughout the app.
    private void stylePrintButton(boolean isPrinted) {
        if (isPrinted) {
            printBtn.setText("Already Printed");
            printBtn.setBackground(Color.WHITE);
            printBtn.setForeground(COLOR_DANGER);
            printBtn.setFont(new Font(Font.SERIF, Font.BOLD, 13));
            printBtn.setBorder(new LineBorder(COLOR_DANGER, 1, false));
            printBtn.setBorderPainted(true);
            printBtn.setContentAreaFilled(true);
            printBtn.setEnabled(false);
        } else {
            printBtn.setText("Print Request Form");
            printBtn.setBackground(COLOR_PRIMARY);
            printBtn.setForeground(Color.black);
            printBtn.setFont(new Font(Font.SERIF, Font.BOLD, 13));
            printBtn.setBorder(new LineBorder(COLOR_PRIMARY, 1, false));
            printBtn.setBorderPainted(true);
            printBtn.setContentAreaFilled(true);
            printBtn.setEnabled(true);
        }
    }

    // ---------------------------------------------------------------- Left Sidebar Navigation
    private JComponent buildSidebarNav() {
        JPanel sidebar = new SidebarPanel();
        sidebar.setLayout(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(248, 0));

        // ---- Brand block: white seal badge + school name
        JPanel badge = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 0, 0, 28));
                g2.fillOval(1, 3, getWidth() - 2, getHeight() - 3);
                g2.setColor(Color.WHITE);
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 2);
                g2.dispose();
            }
        };
        badge.setOpaque(false);
        badge.setPreferredSize(new Dimension(58, 60));
        badge.add(LogoLoader.label(42));

        JLabel n1 = new JLabel("City College");
        JLabel n2 = new JLabel("of Calapan");
        for (JLabel l : new JLabel[]{n1, n2}) {
            l.setFont(serif(Font.BOLD, 17));
            l.setForeground(Color.WHITE);
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        JLabel n3 = new JLabel("REGISTRAR PORTAL");
        n3.setFont(tracked(sans(Font.BOLD, 10), 0.12f));
        n3.setForeground(SIDEBAR_SOFT_TEXT);
        n3.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel brandText = new JPanel();
        brandText.setOpaque(false);
        brandText.setLayout(new BoxLayout(brandText, BoxLayout.Y_AXIS));
        brandText.add(n1);
        brandText.add(n2);
        brandText.add(Box.createRigidArea(new Dimension(0, 5)));
        brandText.add(n3);

        JPanel brandBox = new JPanel(new BorderLayout(14, 0));
        brandBox.setOpaque(false);
        brandBox.setBorder(new EmptyBorder(24, 20, 22, 16));
        brandBox.add(badge, BorderLayout.WEST);
        brandBox.add(centerV(brandText), BorderLayout.CENTER);

        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(brandBox, BorderLayout.CENTER);
        north.add(translucentRule(), BorderLayout.SOUTH);
        sidebar.add(north, BorderLayout.NORTH);

        // ---- Menu
        JPanel menu = new JPanel();
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        menu.setOpaque(false);
        menu.setBorder(new EmptyBorder(22, 14, 18, 14));

        FlatButton navDashboard = navButton("Dashboard", GlyphKind.DASHBOARD, true);
        navDashboard.addActionListener(e -> showDashboard());

        FlatButton navCalendar = navButton("Year Calendar", GlyphKind.CALENDAR, false);
        navCalendar.addActionListener(e -> openCalendar());

        FlatButton navToday = navButton("Today's Schedule", GlyphKind.CLOCK, false);
        navToday.addActionListener(e -> {
            selectedDate = LocalDate.now();
            loadTableData();
            showDashboard();
        });

        FlatButton navRefresh = navButton("Refresh Data", GlyphKind.REFRESH, false);
        navRefresh.addActionListener(e -> {
            loadTableData();
            showDashboard();
        });

        FlatButton navLimit = navButton("Daily Limit", GlyphKind.LIMIT, false);
        navLimit.addActionListener(e -> openLimitDialog());

        menu.add(sectionLabel("MENU"));
        menu.add(navDashboard);
        menu.add(Box.createRigidArea(new Dimension(0, 24)));
        menu.add(sectionLabel("SCHEDULE"));
        menu.add(navCalendar);
        menu.add(Box.createRigidArea(new Dimension(0, 4)));
        menu.add(navToday);
        menu.add(Box.createRigidArea(new Dimension(0, 4)));
        menu.add(navRefresh);
        menu.add(Box.createRigidArea(new Dimension(0, 4)));
        menu.add(navLimit);
        menu.add(Box.createVerticalGlue());

        FlatButton logoutBtn = new FlatButton("Log Out");
        logoutBtn.setFont(sans(Font.BOLD, 13));
        logoutBtn.glyph(GlyphKind.LOGOUT, 18).arc(12);
        logoutBtn.colors(new Color(255, 255, 255, 26), new Color(255, 255, 255, 60),
                new Color(255, 255, 255, 140), Color.WHITE, Color.WHITE);
        logoutBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        logoutBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        logoutBtn.setPreferredSize(new Dimension(10, 42));
        logoutBtn.addActionListener(e -> goToGateway());
        menu.add(logoutBtn);

        JLabel office = new JLabel("Office of the Registrar");
        office.setFont(sans(Font.PLAIN, 11));
        office.setForeground(SIDEBAR_SOFT_TEXT);
        office.setBorder(new EmptyBorder(12, 4, 0, 0));
        office.setAlignmentX(Component.LEFT_ALIGNMENT);
        menu.add(office);

        sidebar.add(menu, BorderLayout.CENTER);
        return sidebar;
    }

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(tracked(sans(Font.BOLD, 10), 0.14f));
        l.setForeground(SIDEBAR_SOFT_TEXT);
        l.setBorder(new EmptyBorder(0, 14, 8, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private FlatButton navButton(String text, GlyphKind glyph, boolean active) {
        FlatButton b = new FlatButton(text);
        b.setFont(sans(Font.BOLD, 14));
        b.glyph(glyph, 20).arc(12).left(16);
        if (active) {
            b.colors(Color.WHITE, Color.WHITE, null, NAV_ACTIVE_TEXT, NAV_ACTIVE_TEXT);
        } else {
            b.colors(null, new Color(255, 255, 255, 44), null, Color.WHITE, Color.WHITE);
        }
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        b.setPreferredSize(new Dimension(10, 44));
        return b;
    }

    private JComponent translucentRule() {
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(new Color(255, 255, 255, 55));
                g.fillRect(18, 0, getWidth() - 36, 1);
            }
        };
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(10, 1));
        return p;
    }

    // ---------------------------------------------------------------- Top Header Bar
    private JComponent buildTopBar() {
        pageTitle.setFont(serif(Font.BOLD, 21));
        pageTitle.setForeground(COLOR_INK);
        pageSubtitle.setFont(sans(Font.PLAIN, 12));
        pageSubtitle.setForeground(COLOR_MUTED);

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        pageTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        pageSubtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleBlock.add(pageTitle);
        titleBlock.add(Box.createRigidArea(new Dimension(0, 2)));
        titleBlock.add(pageSubtitle);

        backBtn = new FlatButton("Back to list");
        backBtn.setFont(sans(Font.BOLD, 13));
        backBtn.glyph(GlyphKind.CHEVRON_LEFT, 16).arc(10);
        backBtn.colors(Color.WHITE, ORANGE_WASH, CONTROL_BORDER, COLOR_INK, COLOR_PRIMARY);
        backBtn.setPreferredSize(new Dimension(122, 36));
        backBtn.addActionListener(e -> showDashboard());
        backBtn.setVisible(false);

        Avatar avatar = new Avatar(initialOf(adminName));
        JLabel userName = new JLabel(displayName());
        userName.setFont(sans(Font.BOLD, 13));
        userName.setForeground(COLOR_INK);
        JLabel userRole = new JLabel("Registrar");
        userRole.setFont(sans(Font.PLAIN, 11));
        userRole.setForeground(COLOR_MUTED);
        JPanel userText = new JPanel();
        userText.setOpaque(false);
        userText.setLayout(new BoxLayout(userText, BoxLayout.Y_AXIS));
        userText.add(userName);
        userText.add(userRole);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        right.add(backBtn);
        right.add(Box.createRigidArea(new Dimension(18, 0)));
        right.add(avatar);
        right.add(Box.createRigidArea(new Dimension(10, 0)));
        right.add(userText);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(Color.WHITE);
        topBar.setPreferredSize(new Dimension(0, 76));
        topBar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, CARD_BORDER),
                new EmptyBorder(0, 28, 0, 28)
        ));
        topBar.add(centerV(titleBlock), BorderLayout.WEST);
        topBar.add(centerV(right), BorderLayout.EAST);
        return topBar;
    }

    private void setTopBarMode(boolean details, Appointment a) {
        backBtn.setVisible(details);
        if (details && a != null) {
            pageTitle.setText("Request Details");
            pageSubtitle.setText(safe(a.getName()) + "  \u00B7  Student ID " + safe(a.getIdNumber()));
        } else {
            pageTitle.setText("Registrar Dashboard");
            pageSubtitle.setText(greeting() + ", " + displayName() + "  \u00B7  "
                    + LocalDate.now().format(LONG_DATE));
        }
    }

    private static JComponent centerV(JComponent c) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        p.add(c, new GridBagConstraints());
        return p;
    }

    private String displayName() {
        return (adminName == null || adminName.trim().isEmpty()) ? "Registrar" : adminName.trim();
    }

    private static String initialOf(String name) {
        if (name == null || name.trim().isEmpty()) return "R";
        return name.trim().substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private static String greeting() {
        int h = LocalTime.now().getHour();
        return h < 12 ? "Good morning" : (h < 18 ? "Good afternoon" : "Good evening");
    }

    private static String safe(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    // ----------------------------------------------------------- Dashboard (summary + table)
    private JComponent buildDashboard() {
        // ---- Summary cards
        statBooked  = new StatCard("Booked Today");
        statPending = new StatCard("Pending Print");
        statPrinted = new StatCard("Printed");
        statDocs    = new StatCard("Documents");

        JPanel stats = new JPanel(new GridLayout(1, 4, 16, 0));
        stats.setOpaque(false);
        stats.setPreferredSize(new Dimension(0, 124));
        stats.add(statBooked);
        stats.add(statPending);
        stats.add(statPrinted);
        stats.add(statDocs);

        // ---- Appointment list card
        RoundedPanel card = new RoundedPanel(16);
        card.setLayout(new BorderLayout());
        card.add(buildToolbar(), BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new EmptyBorder(0, 0, 0, 0));   // non-null, so JTable keeps its hands off
        scroll.setViewportBorder(null);
        scroll.getViewport().setBackground(Color.WHITE);
        scroll.getVerticalScrollBar().setUI(new SlimScrollBarUI());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        JPanel corner = new JPanel();
        corner.setBackground(ORANGE_WASH);
        scroll.setCorner(JScrollPane.UPPER_RIGHT_CORNER, corner);

        tableHolder.setBackground(Color.WHITE);
        tableHolder.add(scroll, "ROWS");
        tableHolder.add(buildEmptyState(), "EMPTY");
        card.add(tableHolder, BorderLayout.CENTER);

        footerCount.setFont(sans(Font.PLAIN, 12));
        footerCount.setForeground(COLOR_MUTED);
        JLabel hint = new JLabel("Click a row to open its request form");
        hint.setFont(sans(Font.PLAIN, 12));
        hint.setForeground(COLOR_MUTED);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, ROW_LINE),
                new EmptyBorder(11, 22, 11, 22)));
        footer.add(footerCount, BorderLayout.WEST);
        footer.add(hint, BorderLayout.EAST);
        card.add(footer, BorderLayout.SOUTH);

        JPanel dashboard = new JPanel(new BorderLayout(0, 18));
        dashboard.setBackground(COLOR_PAPER);
        dashboard.setBorder(new EmptyBorder(22, 28, 22, 28));
        dashboard.add(stats, BorderLayout.NORTH);
        dashboard.add(card, BorderLayout.CENTER);
        return dashboard;
    }

    private JComponent buildToolbar() {
        // Row 1: list title (day navigation lives in the sidebar: Year Calendar / Today's Schedule)
        listTitle.setFont(serif(Font.BOLD, 18));
        listTitle.setForeground(COLOR_INK);
        listSub.setFont(sans(Font.PLAIN, 12));
        listSub.setForeground(COLOR_MUTED);
        listTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        listSub.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.add(listTitle);
        titleBlock.add(Box.createRigidArea(new Dimension(0, 3)));
        titleBlock.add(listSub);

        JPanel row1 = new JPanel(new BorderLayout());
        row1.setOpaque(false);
        row1.add(titleBlock, BorderLayout.WEST);

        // Row 2: search (left) + status filter chips (right)
        searchField = new SearchField("Search name, student ID or document");
        searchField.setPreferredSize(new Dimension(360, 38));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { populateTable(); }
            @Override public void removeUpdate(DocumentEvent e) { populateTable(); }
            @Override public void changedUpdate(DocumentEvent e) { populateTable(); }
        });

        Box chipBox = Box.createHorizontalBox();
        Filter[] filters = Filter.values();
        for (int i = 0; i < filters.length; i++) {
            final Filter f = filters[i];
            FlatButton chip = new FlatButton("");
            chip.setFont(sans(Font.BOLD, 12));
            chip.arc(38);
            chip.setPreferredSize(new Dimension(i == 0 ? 82 : 104, 34));
            chip.addActionListener(e -> {
                statusFilter = f;
                populateTable();
            });
            chips[i] = chip;
            if (i > 0) chipBox.add(Box.createRigidArea(new Dimension(8, 0)));
            chipBox.add(chip);
        }
        updateChipStyles();

        JPanel row2 = new JPanel(new BorderLayout());
        row2.setOpaque(false);
        row2.add(centerV(searchField), BorderLayout.WEST);
        row2.add(centerV(chipBox), BorderLayout.EAST);

        JPanel toolbar = new JPanel(new BorderLayout(0, 14));
        toolbar.setOpaque(false);
        toolbar.setBorder(new EmptyBorder(20, 22, 16, 22));
        toolbar.add(row1, BorderLayout.NORTH);
        toolbar.add(row2, BorderLayout.CENTER);
        return toolbar;
    }

    private void updateChipStyles() {
        Filter[] filters = Filter.values();
        for (int i = 0; i < filters.length; i++) {
            if (filters[i] == statusFilter) {
                chips[i].colors(COLOR_PRIMARY, ORANGE_HOVER, COLOR_PRIMARY, Color.WHITE, Color.WHITE);
            } else {
                chips[i].colors(Color.WHITE, ORANGE_WASH, CONTROL_BORDER, COLOR_INK, COLOR_PRIMARY);
            }
        }
    }

    private JComponent buildEmptyState() {
        emptyTitle.setFont(serif(Font.BOLD, 17));
        emptyTitle.setForeground(COLOR_INK);
        emptyTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        emptySub.setFont(sans(Font.PLAIN, 13));
        emptySub.setForeground(COLOR_MUTED);
        emptySub.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.add(emptyTitle);
        inner.add(Box.createRigidArea(new Dimension(0, 5)));
        inner.add(emptySub);

        JPanel wrap = new JPanel(new GridBagLayout());
        wrap.setBackground(Color.WHITE);
        wrap.add(inner);
        return wrap;
    }

    // ----------------------------------------------------------- Table styling
    private void styleTable() {
        table.setFont(sans(Font.PLAIN, 13));
        table.setRowHeight(50);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setShowGrid(false);
        table.setFillsViewportHeight(true);
        table.setBackground(Color.WHITE);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setSelectionBackground(ROW_SELECT);
        table.setSelectionForeground(COLOR_INK);
        table.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(0, 42));
        final Font headerFont = tracked(sans(Font.BOLD, 11), 0.05f);
        header.setDefaultRenderer((t, value, sel, focus, row, col) -> {
            JLabel l = new JLabel(String.valueOf(value).toUpperCase(Locale.ROOT));
            l.setOpaque(true);
            l.setFont(headerFont);
            l.setForeground(new Color(128, 96, 66));
            l.setBackground(ORANGE_WASH);
            l.setHorizontalAlignment(col == COL_STATUS ? SwingConstants.CENTER : SwingConstants.LEFT);
            l.setBorder(new CompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(240, 206, 176)),
                    new EmptyBorder(0, 14, 0, 14)));
            return l;
        });

        table.getColumnModel().getColumn(0).setPreferredWidth(96);
        table.getColumnModel().getColumn(1).setPreferredWidth(124);
        table.getColumnModel().getColumn(2).setPreferredWidth(200);
        table.getColumnModel().getColumn(COL_DOCUMENTS).setPreferredWidth(300);
        table.getColumnModel().getColumn(COL_PURPOSE).setPreferredWidth(180);
        table.getColumnModel().getColumn(COL_STATUS).setPreferredWidth(124);
        table.getColumnModel().getColumn(0).setMinWidth(88);
        table.getColumnModel().getColumn(1).setMinWidth(116);
        table.getColumnModel().getColumn(COL_STATUS).setMinWidth(116);

        RowRenderer text = new RowRenderer();
        for (int i = 0; i < COL_STATUS; i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(text);
        }
        table.getColumnModel().getColumn(COL_DOCUMENTS).setCellRenderer(new DocRenderer());
        table.getColumnModel().getColumn(COL_STATUS).setCellRenderer(new StatusRenderer());

        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int r = table.rowAtPoint(e.getPoint());
                if (r != hoverRow) {
                    hoverRow = r;
                    table.repaint();
                }
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                hoverRow = -1;
                table.repaint();
            }
        });
    }

    /** Plain text cells: soft row rules, hover wash, selected tint. */
    private class RowRenderer extends DefaultTableCellRenderer {
        private final Border cellBorder = new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ROW_LINE),
                new EmptyBorder(0, 14, 0, 14));
        private final Font regular = sans(Font.PLAIN, 13);
        private final Font bold = sans(Font.BOLD, 13);

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean sel,
                                                       boolean focus, int row, int col) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(t, value, false, false, row, col);
            l.setOpaque(true);
            l.setBorder(cellBorder);
            l.setHorizontalAlignment(SwingConstants.LEFT);
            l.setFont(col == 2 ? bold : regular);
            l.setForeground(col == 0 || col == COL_PURPOSE ? COLOR_MUTED : COLOR_INK);
            l.setBackground(sel ? ROW_SELECT : (row == hoverRow ? ROW_HOVER : Color.WHITE));
            return l;
        }
    }

    /** Documents column: first document name (ellipsized) plus a "+N" badge that never gets clipped. */
    private class DocRenderer implements TableCellRenderer {
        private final JPanel panel = new JPanel(new BorderLayout(8, 0));
        private final JLabel name = new JLabel();
        private final PlusBadge badge = new PlusBadge();

        DocRenderer() {
            panel.setOpaque(true);
            panel.setBorder(new CompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, ROW_LINE),
                    new EmptyBorder(0, 14, 0, 10)));
            name.setFont(sans(Font.PLAIN, 13));
            name.setForeground(COLOR_INK);
            panel.add(name, BorderLayout.CENTER);
            panel.add(badge, BorderLayout.EAST);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean sel,
                                                       boolean focus, int row, int col) {
            DocSummary d = value instanceof DocSummary ? (DocSummary) value : null;
            name.setText(d != null ? d.first : safe(value));
            int extra = d != null ? d.extra : 0;
            badge.setCount(extra);
            badge.setVisible(extra > 0);
            panel.setBackground(sel ? ROW_SELECT : (row == hoverRow ? ROW_HOVER : Color.WHITE));
            return panel;
        }
    }

    private static class PlusBadge extends JComponent {
        private static final Font FONT = sans(Font.BOLD, 11);
        private int count;

        void setCount(int n) {
            this.count = n;
            FontMetrics fm = getFontMetrics(FONT);
            setPreferredSize(new Dimension(fm.stringWidth("+" + n) + 18, 22));
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (count <= 0) return;
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            int h = 20, y = (getHeight() - h) / 2;
            g2.setColor(COLOR_ROW_TINT);
            g2.fillRoundRect(0, y, getWidth() - 1, h, h, h);
            g2.setFont(FONT);
            g2.setColor(NAV_ACTIVE_TEXT);
            String s = "+" + count;
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(s, (getWidth() - fm.stringWidth(s)) / 2, y + (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** Status column: rounded pill, green when printed, orange while pending. */
    private class StatusRenderer implements TableCellRenderer {
        private final StatusCell cell = new StatusCell();

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean sel,
                                                       boolean focus, int row, int col) {
            cell.set(String.valueOf(value), "PRINTED".equals(value),
                    sel ? ROW_SELECT : (row == hoverRow ? ROW_HOVER : Color.WHITE));
            return cell;
        }
    }

    private static class StatusCell extends JComponent {
        private String text = "";
        private boolean printed;
        private Color rowBg = Color.WHITE;

        void set(String text, boolean printed, Color rowBg) {
            this.text = text == null ? "" : text.toUpperCase(Locale.ROOT);
            this.printed = printed;
            this.rowBg = rowBg;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(rowBg);
            g2.fillRect(0, 0, w, h);
            g2.setColor(ROW_LINE);
            g2.fillRect(0, h - 1, w, 1);

            Font f = tracked(sans(Font.BOLD, 11), 0.06f);
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics();
            int pillW = fm.stringWidth(text) + 18 + 16 + 8;
            int pillH = 26;
            int x = (w - pillW) / 2;
            int y = (h - 1 - pillH) / 2;
            Color accent = printed ? COLOR_OK : COLOR_PRIMARY;
            g2.setColor(printed ? GREEN_TINT : COLOR_ROW_TINT);
            g2.fillRoundRect(x, y, pillW, pillH, pillH, pillH);
            g2.setColor(accent);
            g2.fillOval(x + 11, y + (pillH - 7) / 2, 7, 7);
            g2.drawString(text, x + 24, y + (pillH - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    // ----------------------------------------------------------- Navigation helpers
    private void showDashboard() {
        table.clearSelection();
        setTopBarMode(false, null);
        mainCardLayout.show(contentContainer, "TABLE");
    }

    // ----------------------------------------------------------- Appointment Information Box
    private JComponent buildDetailsBox() {
        detailsPanel.setBackground(Color.WHITE);
        detailsPanel.setBorder(new EmptyBorder(20, 24, 20, 24));

        JScrollPane detailsScroll = new JScrollPane(detailsPanel);
        detailsScroll.setBorder(null);
        detailsScroll.getViewport().setBackground(Color.WHITE);

        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setOpaque(false);
        // A rule above the button, so it reads as the form's closing action rather than
        // a block floating below empty space when a request has few details.
        bottomBar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_RULE),
                new EmptyBorder(14, 0, 0, 0)
        ));
        bottomBar.add(printBtn, BorderLayout.CENTER);

        // The request-form sheet itself: a plain white page with a square hairline
        // border, same as the student form's page — not a rounded SaaS card.
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(new CompoundBorder(
                new LineBorder(COLOR_RULE, 1, false),
                new EmptyBorder(16, 16, 16, 16)
        ));
        card.add(detailsScroll, BorderLayout.CENTER);
        card.add(bottomBar, BorderLayout.SOUTH);

        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(COLOR_PAPER);
        outer.setBorder(new EmptyBorder(16, 16, 16, 16));
        outer.add(card, BorderLayout.CENTER);

        return outer;
    }

    private void openCalendar() {
        LocalDate picked = YearCalendarDialog.choose(this, YearCalendarDialog.Mode.ADMIN, selectedDate);
        if (picked != null) {
            selectedDate = picked;
            loadTableData();
            showDashboard();
        }
    }

    /**
     * Lets the registrar change how many appointments are accepted per day.
     * The new limit is saved by CSVManager, then the dashboard reloads so the
     * "slots booked / slots left" text, the Booked card, and its progress bar all update at once.
     */
    private void openLimitDialog() {
        int current = CSVManager.getMaxPerDay();

        JSpinner spinner = new JSpinner(new SpinnerNumberModel(
                current, CSVManager.MIN_LIMIT, CSVManager.MAX_LIMIT, 1));
        spinner.setFont(sans(Font.BOLD, 16));
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(5);

        JLabel info = new JLabel("<html>Maximum appointments accepted per day.<br>"
                + "Allowed: " + CSVManager.MIN_LIMIT + " to " + CSVManager.MAX_LIMIT
                + " &middot; Default: " + CSVManager.DEFAULT_MAX_PER_DAY + "<br><br>"
                + "Existing bookings are never deleted. A day that already has more<br>"
                + "bookings than the new limit will show as full.</html>");
        info.setFont(sans(Font.PLAIN, 12));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JLabel rowLabel = new JLabel("Appointments per day:   ");
        rowLabel.setFont(sans(Font.BOLD, 13));
        row.add(rowLabel);
        row.add(spinner);

        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.add(info, BorderLayout.NORTH);
        panel.add(row, BorderLayout.CENTER);

        Object[] options = {"Save", "Use default (" + CSVManager.DEFAULT_MAX_PER_DAY + ")", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this, panel, "Daily Appointment Limit",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);

        int newLimit;
        if (choice == 0) {
            try {
                spinner.commitEdit(); // accept a typed number that wasn't confirmed with Enter
            } catch (java.text.ParseException ex) {
                JOptionPane.showMessageDialog(this,
                        "Please enter a whole number from " + CSVManager.MIN_LIMIT
                                + " to " + CSVManager.MAX_LIMIT + ".",
                        "Invalid Limit", JOptionPane.WARNING_MESSAGE);
                return;
            }
            newLimit = ((Number) spinner.getValue()).intValue();
        } else if (choice == 1) {
            newLimit = CSVManager.DEFAULT_MAX_PER_DAY;
        } else {
            return; // Cancel or closed the dialog
        }

        if (newLimit == current) return;

        if (!CSVManager.setMaxPerDay(newLimit)) {
            JOptionPane.showMessageDialog(this,
                    "The new limit could not be saved. Check that the app can write to its folder.",
                    "Save Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        loadTableData(); // recomputes slots booked / left and the progress bar
        showDashboard();
    }

    private void goToGateway() {
        new GatewayScreen().setVisible(true);
        dispose();
    }

    // ---------------------------------------------------------------- Logic & Data
    private void loadTableData() {
        allAppointments = CSVManager.loadAllAppointments();
        populateTable();
    }

    private static boolean isPrinted(Appointment a) {
        return "PRINTED".equals(a.getStatus());
    }

    private static int countDocuments(Appointment a) {
        int n = 0;
        for (DocumentRequest ignored : a.getDocuments()) n++;
        return n;
    }

    private boolean matchesFilters(Appointment a) {
        boolean printed = isPrinted(a);
        if (statusFilter == Filter.PENDING && printed) return false;
        if (statusFilter == Filter.PRINTED && !printed) return false;

        String q = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return true;

        StringBuilder hay = new StringBuilder();
        hay.append(safe(a.getIdNumber())).append(' ').append(safe(a.getName())).append(' ')
                .append(safe(a.getPurpose())).append(' ').append(safe(a.getCourseAndYear())).append(' ');
        for (DocumentRequest d : a.getDocuments()) hay.append(safe(d.getDisplayName())).append(' ');
        return hay.toString().toLowerCase(Locale.ROOT).contains(q);
    }

    private void populateTable() {
        LocalDate day = selectedDate;

        List<Appointment> dayList = new ArrayList<>();
        for (Appointment a : allAppointments) {
            if (a.getDate().equals(day)) dayList.add(a);
        }

        int printed = 0;
        int docs = 0;
        for (Appointment a : dayList) {
            if (isPrinted(a)) printed++;
            docs += countDocuments(a);
        }

        visibleRows.clear();
        tableModel.setRowCount(0);
        for (Appointment a : dayList) {
            if (!matchesFilters(a)) continue;
            visibleRows.add(a);
            tableModel.addRow(new Object[]{
                    a.getTimeSubmitted(),
                    a.getIdNumber(),
                    a.getName(),
                    documentSummary(a),
                    a.getPurpose(),
                    a.getStatus()
            });
        }
        hoverRow = -1;

        int booked = dayList.size();
        int max = CSVManager.getMaxPerDay();
        boolean full = booked >= max;
        int left = Math.max(0, max - booked);
        int pending = booked - printed;
        boolean isToday = day.equals(LocalDate.now());

        // Heading + date control
        listTitle.setText(isToday ? "Today's Appointments" : "Appointments");
        listSub.setText(day.format(LONG_DATE) + "  \u00B7  " + booked + " of " + max + " slots booked");
        if (dateBtn != null) dateBtn.setText(day.format(SHORT_DATE));

        // Summary cards
        statBooked.setLabel(isToday ? "Booked Today" : "Booked");
        statBooked.set(String.valueOf(booked),
                full ? "Fully booked" : left + " of " + max + " slots left",
                max > 0 ? booked / (double) max : 0,
                full ? COLOR_DANGER : COLOR_PRIMARY);
        statPending.set(String.valueOf(pending),
                booked == 0 ? "No requests yet" : (pending == 0 ? "All forms printed" : "Awaiting print"),
                -1, null);
        statPrinted.set(String.valueOf(printed),
                booked == 0 ? "No requests yet" : Math.round(100.0 * printed / booked) + "% of requests",
                -1, null);
        statDocs.set(String.valueOf(docs),
                booked == 0 ? "No requests yet" : "across " + booked + (booked == 1 ? " student" : " students"),
                -1, null);

        // Filter chips with counts
        chips[0].setText("All  " + booked);
        chips[1].setText("Pending  " + pending);
        chips[2].setText("Printed  " + printed);
        updateChipStyles();

        // Footer + empty state
        if (booked == 0) {
            footerCount.setText("No appointments for this date");
        } else if (visibleRows.size() == booked) {
            footerCount.setText("Showing all " + booked + (booked == 1 ? " appointment" : " appointments"));
        } else {
            footerCount.setText("Showing " + visibleRows.size() + " of " + booked + " appointments");
        }

        if (visibleRows.isEmpty()) {
            if (booked == 0) {
                emptyTitle.setText("No appointments on this date");
                emptySub.setText("Nothing is scheduled for " + day.format(DATE_LABEL) + ".");
            } else {
                emptyTitle.setText("No matching appointments");
                emptySub.setText("Try a different search or status filter.");
            }
            tableCards.show(tableHolder, "EMPTY");
        } else {
            tableCards.show(tableHolder, "ROWS");
        }

        showEmptyDetails();
    }

    /** First document by name, plus how many more were requested; the tooltip lists them all. */
    private static DocSummary documentSummary(Appointment a) {
        String first = "";
        int n = 0;
        for (DocumentRequest d : a.getDocuments()) {
            if (n == 0) first = String.valueOf(d);
            n++;
        }
        return new DocSummary(first, Math.max(0, n - 1));
    }

    /** Table value for the documents column. */
    private static class DocSummary {
        final String first;
        final int extra;

        DocSummary(String first, int extra) {
            this.first = first;
            this.extra = extra;
        }

        @Override
        public String toString() {
            return first;
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void showEmptyDetails() {
        selected = null;
        stylePrintButton(false);
        printBtn.setEnabled(false);

        detailsPanel.removeAll();
        JLabel hint = new JLabel("Select an appointment row from the table to view details.", SwingConstants.CENTER);
        hint.setFont(new Font(Font.SERIF, Font.ITALIC, 12));
        hint.setForeground(COLOR_MUTED);

        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.CENTER;
        c.weightx = 1; c.weighty = 1;
        detailsPanel.add(hint, c);
        detailsPanel.revalidate();
        detailsPanel.repaint();
    }

    /** Width of the on-screen request form: a compact slip, not a full-width page. */
    private static final int SHEET_WIDTH = 480;

    private void showDetails(Appointment a) {
        selected = a;
        detailsPanel.removeAll();

        // The sheet keeps a fixed width so it reads like a small paper form centred in the
        // panel, and every section is stacked in its own row so nothing can overlap.
        JPanel sheet = new JPanel(new GridBagLayout()) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(SHEET_WIDTH, super.getPreferredSize().height);
            }
        };
        sheet.setBackground(Color.WHITE);
        sheet.setBorder(new CompoundBorder(
                new LineBorder(COLOR_RULE, 1, false),
                new EmptyBorder(14, 16, 18, 16)));

        int row = 0;
        row = addSheetRow(sheet, row, formHeader(), 0);
        row = addSheetRow(sheet, row, createRule(2), 8);

        // ---- Student information table (label above value, boxed cells)
        JPanel info = new JPanel(new GridBagLayout());
        info.setOpaque(false);
        info.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, COLOR_RULE));
        int ir = 0;
        ir = addSheetRow(info, ir, formRow(
                formCell("Student ID", a.getIdNumber(), 1, 0),
                formCell("Appointment Date", a.getDate().format(DATE_LABEL), 1, 0)), 0);
        ir = addSheetRow(info, ir, formRow(
                formCell("Student Name", a.getName(), 2, 0)), 0);
        ir = addSheetRow(info, ir, formRow(
                formCell("Course & Year", a.getCourseAndYear(), 2, 0)), 0);
        ir = addSheetRow(info, ir, formRow(
                formCell("Date Submitted", a.getDateSubmitted().format(SHORT_DATE) + "  " + a.getTimeSubmitted(), 2, 0)), 0);
        ir = addSheetRow(info, ir, formRow(
                formCell("Purpose / Reason", a.getPurpose(), 2, 34)), 0);
        row = addSheetRow(sheet, row, info, 12);

        // ---- Requested documents table
        JPanel docs = new JPanel(new GridBagLayout());
        docs.setOpaque(false);
        docs.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, COLOR_RULE));
        int dr = 0;
        dr = addSheetRow(docs, dr, docTableRow("No.", "Requested Documents", true), 0);
        int n = 1;
        for (DocumentRequest d : a.getDocuments()) {
            dr = addSheetRow(docs, dr, docTableRow(String.valueOf(n++), d.getDisplayName(), false), 0);
        }
        row = addSheetRow(sheet, row, docs, 12);

        // ---- Signature lines
        row = addSheetRow(sheet, row, signatureBlock(), 8);

        GridBagConstraints sc = new GridBagConstraints();
        sc.gridx = 0; sc.gridy = 0;
        sc.weightx = 1; sc.weighty = 1;
        sc.anchor = GridBagConstraints.NORTH;
        sc.fill = GridBagConstraints.NONE;
        detailsPanel.add(sheet, sc);

        boolean isPrinted = "PRINTED".equals(a.getStatus());
        stylePrintButton(isPrinted);

        detailsPanel.revalidate();
        detailsPanel.repaint();
    }

    /** Adds a full-width component as the next row of a vertical GridBag stack. */
    private static int addSheetRow(JPanel parent, int row, JComponent comp, int topGap) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = row;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(topGap, 0, 0, 0);
        parent.add(comp, c);
        return row + 1;
    }

    /** Letterhead: seal + school name / form title, with the office line right-aligned below. */
    private JComponent formHeader() {
        JLabel logo = LogoLoader.label(46);

        JLabel schoolName = new JLabel("CITY COLLEGE OF CALAPAN");
        schoolName.setFont(serif(Font.BOLD, 15));
        schoolName.setForeground(COLOR_INK);

        JLabel formTitle = new JLabel("Appointment and Document Request Form");
        formTitle.setFont(serif(Font.PLAIN, 11));
        formTitle.setForeground(COLOR_MUTED);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        schoolName.setAlignmentX(Component.LEFT_ALIGNMENT);
        formTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(schoolName);
        text.add(Box.createRigidArea(new Dimension(0, 3)));
        text.add(formTitle);

        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setOpaque(false);
        top.add(logo, BorderLayout.WEST);
        top.add(leftV(text), BorderLayout.CENTER);

        // Its own line, so it can never collide with the school name or the form title.
        JLabel office = new JLabel("Registrar of City College of Calapan", SwingConstants.RIGHT);
        office.setFont(serif(Font.ITALIC, 11));
        office.setForeground(COLOR_MUTED);
        office.setBorder(new EmptyBorder(6, 0, 0, 0));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(top, BorderLayout.CENTER);
        header.add(office, BorderLayout.SOUTH);
        return header;
    }

    /** Vertically centres a component but keeps it pinned to the left edge. */
    private static JComponent leftV(JComponent c) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.WEST;
        gc.weightx = 1;
        p.add(c, gc);
        return p;
    }

    /** One table row: its cells share the row width equally. */
    private JComponent formRow(JComponent... cells) {
        JPanel r = new JPanel(new GridLayout(1, cells.length));
        r.setOpaque(false);
        for (JComponent cell : cells) r.add(cell);
        return r;
    }

    /**
     * Boxed field: small caption on top, value underneath. {@code span} is how many table
     * columns the cell covers (1 = half the row, 2 = the full row); it only sizes the
     * wrapped value so the row height is right -- the text itself always wraps at the
     * cell's real width, so it can never be cut off.
     */
    private JComponent formCell(String caption, String value, int span, int minValueHeight) {
        JLabel cap = new JLabel(caption.toUpperCase());
        cap.setFont(serif(Font.BOLD, 9.5f));
        cap.setForeground(COLOR_MUTED);

        int tableWidth = SHEET_WIDTH - 34;                 // sheet width minus its border + padding
        int textWidth = (span >= 2 ? tableWidth : tableWidth / 2) - 17 - 4;

        String safe = (value == null || value.trim().isEmpty()) ? " " : value;
        JTextArea val = new JTextArea(safe);
        val.setFont(serif(Font.PLAIN, 13));
        val.setForeground(COLOR_INK);
        val.setEditable(false);
        val.setFocusable(false);
        val.setOpaque(false);
        val.setBorder(null);
        val.setLineWrap(true);
        val.setWrapStyleWord(true);
        val.setSize(new Dimension(textWidth, Short.MAX_VALUE));
        int h = Math.max(val.getPreferredSize().height, minValueHeight);
        val.setPreferredSize(new Dimension(textWidth, h));

        JPanel cell = new JPanel(new BorderLayout(0, 3));
        cell.setBackground(Color.WHITE);
        cell.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 0, 0, COLOR_RULE),
                new EmptyBorder(5, 8, 6, 8)));
        cell.add(cap, BorderLayout.NORTH);
        cell.add(val, BorderLayout.CENTER);
        return cell;
    }

    /** Row of the requested-documents table: narrow "No." column + document name. */
    private JComponent docTableRow(String no, String name, boolean header) {
        Color bg = header ? new Color(244, 241, 236) : Color.WHITE;
        int style = header ? Font.BOLD : Font.PLAIN;
        float size = header ? 11 : 13;

        JLabel num = new JLabel(no, SwingConstants.CENTER);
        num.setFont(serif(style, size));
        num.setForeground(COLOR_INK);
        num.setOpaque(true);
        num.setBackground(bg);
        num.setPreferredSize(new Dimension(46, 26));
        num.setBorder(BorderFactory.createMatteBorder(1, 1, 0, 0, COLOR_RULE));

        JLabel label = new JLabel(name);
        label.setFont(serif(style, size));
        label.setForeground(COLOR_INK);
        label.setOpaque(true);
        label.setBackground(bg);
        label.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 0, 0, COLOR_RULE),
                new EmptyBorder(4, 8, 4, 8)));

        JPanel r = new JPanel(new BorderLayout());
        r.setOpaque(false);
        r.add(num, BorderLayout.WEST);
        r.add(label, BorderLayout.CENTER);
        return r;
    }

    /** Two signature lines with captions, as found on a standard school form. */
    private JComponent signatureBlock() {
        JPanel block = new JPanel(new GridLayout(1, 2, 28, 0));
        block.setOpaque(false);
        block.setBorder(new EmptyBorder(30, 0, 0, 0));
        for (String caption : new String[]{"Student's Signature", "Registrar / Authorized Staff"}) {
            JLabel l = new JLabel(caption, SwingConstants.CENTER);
            l.setFont(serif(Font.PLAIN, 10.5f));
            l.setForeground(COLOR_MUTED);
            l.setBorder(new CompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_RULE),
                    new EmptyBorder(4, 0, 0, 0)));
            block.add(l);
        }
        return block;
    }

    // ---------------------------------------------------------------- Design Helpers
    // A plain ruled line, used for the letterhead divider and the sidebar's section break —
    // the same device used throughout the gateway and student form screens.
    private JComponent createRule(int thickness) {
        JPanel rule = new JPanel();
        rule.setBackground(COLOR_RULE);
        rule.setPreferredSize(new Dimension(10, thickness));
        rule.setMaximumSize(new Dimension(Integer.MAX_VALUE, thickness));
        rule.setAlignmentX(Component.LEFT_ALIGNMENT);
        return rule;
    }

    // ---------------------------------------------------------------- Dashboard UI toolkit
    private static String pickSans() {
        try {
            Set<String> names = new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String n : new String[]{"Segoe UI", "Helvetica Neue", "Inter", "Arial"}) {
                if (names.contains(n)) return n;
            }
        } catch (Throwable ignored) {
        }
        return Font.SANS_SERIF;
    }

    private static Font sans(int style, float size) {
        return new Font(SANS_FAMILY, style, 1).deriveFont(size);
    }

    private static Font serif(int style, float size) {
        return new Font(Font.SERIF, style, 1).deriveFont(size);
    }

    private static Font tracked(Font f, float tracking) {
        Map<TextAttribute, Object> attrs = new HashMap<>();
        attrs.put(TextAttribute.TRACKING, tracking);
        return f.deriveFont(attrs);
    }

    private static void smooth(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    // ---- Vector icons (24 x 24 design grid, scaled to any size)
    private enum GlyphKind {
        DASHBOARD, CALENDAR, CLOCK, REFRESH, LOGOUT, CHEVRON_LEFT, CHEVRON_RIGHT,
        SEARCH, DOCUMENT, PRINTER, LIMIT
    }

    private static void paintGlyph(Graphics2D g, GlyphKind kind, int x, int y, int size, Color color) {
        Graphics2D g2 = (Graphics2D) g.create();
        smooth(g2);
        g2.translate(x, y);
        g2.scale(size / 24.0, size / 24.0);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Double p = new Path2D.Double();

        switch (kind) {
            case DASHBOARD:
                g2.draw(new RoundRectangle2D.Double(3.5, 3.5, 7, 7, 2.5, 2.5));
                g2.draw(new RoundRectangle2D.Double(13.5, 3.5, 7, 7, 2.5, 2.5));
                g2.draw(new RoundRectangle2D.Double(3.5, 13.5, 7, 7, 2.5, 2.5));
                g2.draw(new RoundRectangle2D.Double(13.5, 13.5, 7, 7, 2.5, 2.5));
                break;
            case CALENDAR:
                g2.draw(new RoundRectangle2D.Double(3.5, 5, 17, 15.5, 3.5, 3.5));
                g2.draw(new Line2D.Double(3.5, 10.5, 20.5, 10.5));
                g2.draw(new Line2D.Double(8, 2.5, 8, 6.5));
                g2.draw(new Line2D.Double(16, 2.5, 16, 6.5));
                break;
            case CLOCK:
                g2.draw(new Ellipse2D.Double(3, 3, 18, 18));
                p.moveTo(12, 7.5);
                p.lineTo(12, 12);
                p.lineTo(15.5, 14);
                g2.draw(p);
                break;
            case REFRESH:
                g2.draw(new Arc2D.Double(4.5, 4.5, 15, 15, 90, 265, Arc2D.OPEN));
                p.moveTo(16.2, 11.5);
                p.lineTo(22.8, 11.5);
                p.lineTo(19.5, 6.5);
                p.closePath();
                g2.fill(p);
                break;
            case LOGOUT:
                p.moveTo(10, 3.5);
                p.lineTo(6, 3.5);
                p.quadTo(3.5, 3.5, 3.5, 6);
                p.lineTo(3.5, 18);
                p.quadTo(3.5, 20.5, 6, 20.5);
                p.lineTo(10, 20.5);
                g2.draw(p);
                g2.draw(new Line2D.Double(9.5, 12, 20.5, 12));
                Path2D.Double head = new Path2D.Double();
                head.moveTo(16.5, 8);
                head.lineTo(20.5, 12);
                head.lineTo(16.5, 16);
                g2.draw(head);
                break;
            case CHEVRON_LEFT:
                p.moveTo(15, 5);
                p.lineTo(8, 12);
                p.lineTo(15, 19);
                g2.draw(p);
                break;
            case CHEVRON_RIGHT:
                p.moveTo(9, 5);
                p.lineTo(16, 12);
                p.lineTo(9, 19);
                g2.draw(p);
                break;
            case SEARCH:
                g2.draw(new Ellipse2D.Double(3.5, 3.5, 12.5, 12.5));
                g2.draw(new Line2D.Double(14.8, 14.8, 20.5, 20.5));
                break;
            case DOCUMENT:
                p.moveTo(6.5, 3);
                p.lineTo(14, 3);
                p.lineTo(19, 8);
                p.lineTo(19, 20.5);
                p.lineTo(6.5, 20.5);
                p.closePath();
                g2.draw(p);
                Path2D.Double fold = new Path2D.Double();
                fold.moveTo(14, 3);
                fold.lineTo(14, 8);
                fold.lineTo(19, 8);
                g2.draw(fold);
                g2.draw(new Line2D.Double(9.5, 13, 15.5, 13));
                g2.draw(new Line2D.Double(9.5, 16.5, 15.5, 16.5));
                break;
            case PRINTER:
                g2.draw(new RoundRectangle2D.Double(7, 3.5, 10, 5, 1, 1));
                g2.draw(new RoundRectangle2D.Double(3.5, 8.5, 17, 8, 2.5, 2.5));
                g2.draw(new RoundRectangle2D.Double(7, 13.5, 10, 7, 1, 1));
                g2.fill(new Ellipse2D.Double(16, 10.4, 1.6, 1.6));
                break;
            case LIMIT:
                // three slider tracks, each with a round knob
                double[] knobX = {15.5, 8.5, 13.5};
                double[] trackY = {6, 12, 18};
                for (int i = 0; i < 3; i++) {
                    g2.draw(new Line2D.Double(3.5, trackY[i], knobX[i] - 2.8, trackY[i]));
                    g2.draw(new Line2D.Double(knobX[i] + 2.8, trackY[i], 20.5, trackY[i]));
                    g2.draw(new Ellipse2D.Double(knobX[i] - 2.3, trackY[i] - 2.3, 4.6, 4.6));
                }
                break;
            default:
                break;
        }
        g2.dispose();
    }

    /** Sidebar background: brand orange gradient with two soft decorative circles. */
    private static class SidebarPanel extends JPanel {
        SidebarPanel() { setOpaque(true); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setPaint(new GradientPaint(0, 0, SIDEBAR_TOP, 0, getHeight(), SIDEBAR_BOTTOM));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(new Color(255, 255, 255, 14));
            g2.fillOval(getWidth() - 130, getHeight() - 250, 280, 280);
            g2.setColor(new Color(255, 255, 255, 10));
            g2.fillOval(-90, getHeight() - 150, 200, 200);
            g2.dispose();
        }
    }

    /** White rounded card with a hairline border and a faint shadow. */
    private static class RoundedPanel extends JPanel {
        private final int arc;

        RoundedPanel(int arc) {
            this.arc = arc;
            setOpaque(false);
            setBorder(new EmptyBorder(1, 1, 5, 1));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            int w = getWidth(), h = getHeight() - 4;
            g2.setColor(new Color(90, 55, 15, 10));
            g2.fillRoundRect(1, 3, w - 2, h, arc, arc);
            g2.setColor(new Color(90, 55, 15, 14));
            g2.fillRoundRect(0, 1, w, h, arc, arc);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(CARD_BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.dispose();
        }
    }

    /** Summary card: label, big number, caption and an optional progress bar. */
    private static class StatCard extends JPanel {
        private String label;
        private String value = "0";
        private String sub = "";
        private double progress = -1;
        private Color progressColor = COLOR_PRIMARY;

        StatCard(String label) {
            this.label = label;
            setOpaque(false);
        }

        void setLabel(String label) {
            this.label = label;
            repaint();
        }

        void set(String value, String sub, double progress, Color progressColor) {
            this.value = value;
            this.sub = sub;
            this.progress = progress;
            if (progressColor != null) this.progressColor = progressColor;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            int w = getWidth(), h = getHeight() - 5;
            int arc = 16;
            g2.setColor(new Color(90, 55, 15, 10));
            g2.fillRoundRect(1, 3, w - 2, h, arc, arc);
            g2.setColor(new Color(90, 55, 15, 14));
            g2.fillRoundRect(0, 1, w, h, arc, arc);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(CARD_BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

            g2.setFont(sans(Font.BOLD, 12));
            g2.setColor(COLOR_MUTED);
            g2.drawString(label, 20, 35);

            g2.setFont(sans(Font.BOLD, 32));
            g2.setColor(COLOR_INK);
            g2.drawString(value, 20, 76);

            g2.setFont(sans(Font.PLAIN, 12));
            g2.setColor(COLOR_MUTED);
            g2.drawString(sub, 20, 98);

            if (progress >= 0) {
                int bx = 20, bw = w - 40, by = h - 15, bh = 6;
                g2.setColor(TRACK);
                g2.fillRoundRect(bx, by, bw, bh, bh, bh);
                int fw = (int) Math.round(bw * Math.min(1.0, progress));
                if (fw > 0) {
                    g2.setColor(progressColor);
                    g2.fillRoundRect(bx, by, Math.max(fw, bh), bh, bh, bh);
                }
            }
            g2.dispose();
        }
    }

    /** One flat, fully self-painted button used for nav, filters, dates and actions. */
    private static class FlatButton extends JButton {
        private Color fill, hoverFill, border, textColor = COLOR_INK, hoverText = COLOR_INK;
        private GlyphKind glyph;
        private int iconSize = 18;
        private int arc = 12;
        private int padX = 14;
        private boolean alignLeft;
        private boolean hover;

        FlatButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setMargin(new Insets(0, 0, 0, 0));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(sans(Font.BOLD, 13));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        FlatButton colors(Color fill, Color hoverFill, Color border, Color text, Color hoverText) {
            this.fill = fill;
            this.hoverFill = hoverFill;
            this.border = border;
            this.textColor = text;
            this.hoverText = hoverText;
            repaint();
            return this;
        }

        FlatButton glyph(GlyphKind kind, int size) {
            this.glyph = kind;
            this.iconSize = size;
            return this;
        }

        FlatButton arc(int arc) {
            this.arc = arc;
            return this;
        }

        FlatButton left(int padX) {
            this.alignLeft = true;
            this.padX = padX;
            return this;
        }

        @Override
        public Dimension getMinimumSize() {
            return isMinimumSizeSet() ? super.getMinimumSize() : getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return isMaximumSizeSet() ? super.getMaximumSize() : getPreferredSize();
        }

        @Override
        public Dimension getPreferredSize() {
            if (isPreferredSizeSet()) return super.getPreferredSize();
            FontMetrics fm = getFontMetrics(getFont());
            String t = getText() == null ? "" : getText();
            int w = padX * 2 + fm.stringWidth(t) + (glyph != null ? iconSize + (t.isEmpty() ? 0 : 8) : 0);
            return new Dimension(w, 36);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            int w = getWidth(), h = getHeight();
            boolean active = (hover || getModel().isPressed()) && isEnabled();

            Color bg = active && hoverFill != null ? hoverFill : fill;
            if (bg != null) {
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, w, h, arc, arc);
            }
            if (border != null) {
                g2.setColor(active && fill != null && hoverFill != null && !border.equals(COLOR_PRIMARY)
                        ? border : border);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            }

            Color fg = active ? hoverText : textColor;
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            String t = getText() == null ? "" : getText();
            int iconW = glyph != null ? iconSize : 0;
            int gap = (glyph != null && !t.isEmpty()) ? 9 : 0;
            int contentW = iconW + gap + fm.stringWidth(t);
            int x = alignLeft ? padX : (w - contentW) / 2;

            if (glyph != null) {
                paintGlyph(g2, glyph, x, (h - iconSize) / 2, iconSize, fg);
                x += iconW + gap;
            }
            if (!t.isEmpty()) {
                g2.setColor(fg);
                g2.drawString(t, x, (h - fm.getHeight()) / 2 + fm.getAscent());
            }
            g2.dispose();
        }
    }

    /** Rounded search box with a magnifier icon and placeholder text. */
    private static class SearchField extends JTextField {
        private final String placeholder;

        SearchField(String placeholder) {
            this.placeholder = placeholder;
            setOpaque(false);
            setFont(sans(Font.PLAIN, 13));
            setForeground(COLOR_INK);
            setCaretColor(COLOR_PRIMARY);
            setBorder(new EmptyBorder(0, 42, 0, 14));
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            int w = getWidth(), h = getHeight();
            g2.setColor(COLOR_PAPER);
            g2.fillRoundRect(0, 0, w, h, 12, 12);
            boolean focus = hasFocus();
            g2.setColor(focus ? COLOR_PRIMARY : CONTROL_BORDER);
            g2.setStroke(new BasicStroke(focus ? 1.6f : 1f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
            paintGlyph(g2, GlyphKind.SEARCH, 15, (h - 17) / 2, 17, focus ? COLOR_PRIMARY : new Color(150, 140, 126));
            g2.dispose();

            super.paintComponent(g);

            if (getText().isEmpty()) {
                Graphics2D g3 = (Graphics2D) g.create();
                smooth(g3);
                g3.setFont(getFont());
                g3.setColor(new Color(150, 140, 126));
                FontMetrics fm = g3.getFontMetrics();
                g3.drawString(placeholder, getInsets().left, (h - fm.getHeight()) / 2 + fm.getAscent());
                g3.dispose();
            }
        }
    }

    /** Circle with an initial, used for the signed-in user. */
    private static class Avatar extends JComponent {
        private final String initial;

        Avatar(String initial) {
            this.initial = initial;
            setPreferredSize(new Dimension(40, 40));
            setMinimumSize(new Dimension(40, 40));
            setMaximumSize(new Dimension(40, 40));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setPaint(new GradientPaint(0, 0, new Color(236, 126, 36), 0, getHeight(), COLOR_PRIMARY));
            g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
            g2.setFont(sans(Font.BOLD, 16));
            g2.setColor(Color.WHITE);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(initial, (getWidth() - fm.stringWidth(initial)) / 2,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** Slim, flat vertical scroll bar for the appointment table. */
    private static class SlimScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(226, 214, 198);
            trackColor = Color.WHITE;
        }

        @Override protected JButton createDecreaseButton(int orientation) { return zeroButton(); }
        @Override protected JButton createIncreaseButton(int orientation) { return zeroButton(); }

        private JButton zeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(Color.WHITE);
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(isThumbRollover() ? new Color(214, 196, 172) : thumbColor);
            g2.fillRoundRect(r.x + 3, r.y + 2, r.width - 6, r.height - 4, 8, 8);
            g2.dispose();
        }
    }

    // -------------------------------------------------------------- Printing Execution
    private void printSelected() {
        if (selected == null) return;

        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintable(new FormPrintable(selected));

        if (SEND_TO_PRINTER) {
            if (!job.printDialog()) return;
            try {
                job.print();
            } catch (PrinterException ex) {
                JOptionPane.showMessageDialog(this, "Printing failed: " + ex.getMessage(),
                        "Print Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        CSVManager.markAsPrinted(selected);
        JOptionPane.showMessageDialog(this, "Form printed successfully. Appointment status updated.", "Print Success", JOptionPane.INFORMATION_MESSAGE);
        loadTableData();
        showDashboard();
    }

    /**
     * Printable Document Form -- drawn at quarter-page size (half the page width and half
     * the page height, top-left corner) with the same layout as the on-screen form.
     */
    private static class FormPrintable implements Printable {
        private static final Color INK   = new Color(26, 24, 22);
        private static final Color MUTED = new Color(96, 90, 82);
        private static final Color LINE  = new Color(74, 68, 60);
        private static final Color SHADE = new Color(244, 241, 236);
        private static final int PAD = 10;
        private static final int CELL_H = 24;
        private static final int DOC_H = 15;

        private final Appointment a;

        FormPrintable(Appointment a) { this.a = a; }

        @Override
        public int print(Graphics g, PageFormat pf, int pageIndex) {
            if (pageIndex > 0) return NO_SUCH_PAGE;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(pf.getImageableX(), pf.getImageableY());
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(0.7f));

            int w = (int) (pf.getImageableWidth() / 2);   // quarter sheet
            int x0 = PAD, x1 = w - PAD, cw = x1 - x0;
            int half = cw / 2;
            int y = PAD;

            // ---- Letterhead
            ImageIcon logo = LogoLoader.icon(34);
            if (logo != null) g2.drawImage(logo.getImage(), x0, y, null);
            int tx = x0 + 42;
            g2.setColor(INK);
            g2.setFont(serif(Font.BOLD, 10.5f));
            g2.drawString("CITY COLLEGE OF CALAPAN", tx, y + 13);
            g2.setColor(MUTED);
            g2.setFont(serif(Font.PLAIN, 7.5f));
            g2.drawString("Appointment and Document Request Form", tx, y + 24);
            g2.setFont(serif(Font.ITALIC, 7.5f));
            String office = "Office of the Registrar";
            g2.drawString(office, x1 - g2.getFontMetrics().stringWidth(office), y + 36);
            y += 42;
            g2.setColor(LINE);
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawLine(x0, y, x1, y);
            g2.setStroke(new BasicStroke(0.7f));
            y += 9;

            // ---- Student information table
            cell(g2, x0, y, half, CELL_H, "Student ID", a.getIdNumber());
            cell(g2, x0 + half, y, cw - half, CELL_H, "Appointment Date", a.getDate().format(DATE_LABEL));
            y += CELL_H;
            y += wrapCell(g2, x0, y, cw, "Student Name", a.getName(), CELL_H);
            y += wrapCell(g2, x0, y, cw, "Course & Year", a.getCourseAndYear(), CELL_H);
            cell(g2, x0, y, half, CELL_H, "Date Submitted", a.getDateSubmitted().format(DATE_LABEL));
            cell(g2, x0 + half, y, cw - half, CELL_H, "Time Submitted", String.valueOf(a.getTimeSubmitted()));
            y += CELL_H;
            y += wrapCell(g2, x0, y, cw, "Purpose / Reason", a.getPurpose(), CELL_H + 8);
            y += 10;

            // ---- Requested documents table
            int numW = 24;
            g2.setColor(SHADE);
            g2.fillRect(x0, y, cw, DOC_H);
            g2.setColor(LINE);
            g2.drawRect(x0, y, cw, DOC_H);
            g2.drawLine(x0 + numW, y, x0 + numW, y + DOC_H);
            g2.setColor(INK);
            g2.setFont(serif(Font.BOLD, 7.5f));
            g2.drawString("No.", x0 + 5, y + 10);
            g2.drawString("Requested Documents", x0 + numW + 5, y + 10);
            y += DOC_H;

            g2.setFont(serif(Font.PLAIN, 8.5f));
            int n = 1;
            for (DocumentRequest d : a.getDocuments()) {
                g2.setColor(LINE);
                g2.drawRect(x0, y, cw, DOC_H);
                g2.drawLine(x0 + numW, y, x0 + numW, y + DOC_H);
                g2.setColor(INK);
                g2.drawString(String.valueOf(n++), x0 + 9, y + 10);
                g2.drawString(fit(g2.getFontMetrics(), d.getDisplayName(), cw - numW - 10),
                        x0 + numW + 5, y + 10);
                y += DOC_H;
            }

            // ---- Signature lines
            y += 34;
            int gap = 14;
            int sigW = (cw - gap) / 2;
            g2.setColor(LINE);
            g2.drawLine(x0, y, x0 + sigW, y);
            g2.drawLine(x1 - sigW, y, x1, y);
            g2.setColor(MUTED);
            g2.setFont(serif(Font.PLAIN, 6.5f));
            centerText(g2, "Student's Signature", x0, sigW, y + 9);
            centerText(g2, "Registrar / Authorized Staff", x1 - sigW, sigW, y + 9);
            y += 9 + PAD;

            // ---- Outer border of the quarter-size sheet
            g2.setColor(LINE);
            g2.setStroke(new BasicStroke(0.9f));
            g2.drawRect(0, 0, w, y);

            g2.dispose();
            return PAGE_EXISTS;
        }

        /** Full-width boxed field whose value wraps onto extra lines; returns the height used. */
        private static int wrapCell(Graphics2D g2, int x, int y, int cw, String caption, String value, int minH) {
            g2.setFont(serif(Font.PLAIN, 8.5f));
            List<String> lines = wrap(value, g2.getFontMetrics(), cw - 10);
            int h = Math.max(minH, 14 + lines.size() * 10);
            g2.setColor(LINE);
            g2.drawRect(x, y, cw, h);
            g2.setColor(MUTED);
            g2.setFont(serif(Font.BOLD, 6.5f));
            g2.drawString(caption.toUpperCase(), x + 5, y + 9);
            g2.setColor(INK);
            g2.setFont(serif(Font.PLAIN, 8.5f));
            int ty = y + 20;
            for (String line : lines) {
                g2.drawString(line, x + 5, ty);
                ty += 10;
            }
            return h;
        }

        /** Boxed field: small caption on top, value below, truncated if too long. */
        private static void cell(Graphics2D g2, int x, int y, int cw, int h, String caption, String value) {
            g2.setColor(LINE);
            g2.drawRect(x, y, cw, h);
            g2.setColor(MUTED);
            g2.setFont(serif(Font.BOLD, 6.5f));
            g2.drawString(caption.toUpperCase(), x + 5, y + 9);
            g2.setColor(INK);
            g2.setFont(serif(Font.PLAIN, 8.5f));
            g2.drawString(fit(g2.getFontMetrics(), value, cw - 10), x + 5, y + h - 6);
        }

        private static void centerText(Graphics2D g2, String text, int x, int width, int baseline) {
            g2.drawString(text, x + (width - g2.getFontMetrics().stringWidth(text)) / 2, baseline);
        }

        /** Shortens text with an ellipsis so it never runs past its box. */
        private static String fit(FontMetrics fm, String text, int maxW) {
            String t = text == null ? "" : text;
            if (fm.stringWidth(t) <= maxW) return t;
            while (t.length() > 1 && fm.stringWidth(t + "...") > maxW) {
                t = t.substring(0, t.length() - 1);
            }
            return t + "...";
        }

        /** Word-wraps by real pixel width, so long purposes stay inside the box. */
        private static List<String> wrap(String text, FontMetrics fm, int maxW) {
            List<String> lines = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            String safe = text == null ? "" : text;
            for (String word : safe.split(" ")) {
                String trial = cur.length() == 0 ? word : cur + " " + word;
                if (cur.length() > 0 && fm.stringWidth(trial) > maxW) {
                    lines.add(cur.toString());
                    cur.setLength(0);
                    trial = word;
                }
                cur.setLength(0);
                cur.append(trial);
            }
            if (cur.length() > 0) lines.add(cur.toString());
            if (lines.isEmpty()) lines.add("");
            return lines;
        }
    }
}