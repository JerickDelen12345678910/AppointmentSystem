package views;

import utils.LogoLoader;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class GatewayScreen extends JFrame {
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "admin123";

    // Same orange as the dashboard, flat and a bit deeper than the old glossy one.
    private static final Color ORANGE      = new Color(217, 101, 14);
    private static final Color ORANGE_DARK = new Color(184, 82, 6);
    private static final Color PAGE_BG     = new Color(243, 242, 240);   // neutral, not bluish
    private static final Color CARD_BORDER = new Color(214, 211, 206);
    private static final Color FIELD_BORDER = new Color(176, 172, 165);
    private static final Color TEXT        = new Color(33, 31, 28);
    private static final Color MUTED       = new Color(105, 100, 93);

    // One sans family for everything (Segoe UI on Windows), serif only for the school name.
    private static final String SANS = pickSans();

    private JButton loginBtn;

    public GatewayScreen() {
        setTitle("City College of Calapan - Appointment System");
        setSize(480, 670);
        setMinimumSize(new Dimension(380, 620));
        LogoLoader.applyWindowIcon(this);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        add(buildContent(), BorderLayout.CENTER);
        getRootPane().setDefaultButton(loginBtn);
    }

    private JComponent buildContent() {
        JPanel background = new JPanel(new GridBagLayout());
        background.setBackground(PAGE_BG);

        // Main login card
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(new CompoundBorder(
                new LineBorder(CARD_BORDER, 1, false),
                new EmptyBorder(20, 25, 20, 25)
        ));
        card.setPreferredSize(new Dimension(380, 560));
        card.setMaximumSize(new Dimension(380, 560));

        // ----- Logo + Header -----
        card.add(Box.createRigidArea(new Dimension(0, 10)));
        JLabel logoLabel = LogoLoader.label(120);
        logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(logoLabel);
        card.add(Box.createRigidArea(new Dimension(0, 14)));

        JLabel schoolLabel = new JLabel("City College of Calapan");
        schoolLabel.setFont(new Font(Font.SERIF, Font.BOLD, 20));
        schoolLabel.setForeground(TEXT);
        schoolLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(schoolLabel);
        card.add(Box.createRigidArea(new Dimension(0, 4)));

        JLabel systemLabel = new JLabel("Registrar Appointment System");
        systemLabel.setFont(sans(Font.PLAIN, 13));
        systemLabel.setForeground(MUTED);
        systemLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(systemLabel);
        card.add(Box.createRigidArea(new Dimension(0, 26)));

        // ----- Student section -----
        JButton studentBtn = createButton("Continue as Student");
        studentBtn.setPreferredSize(new Dimension(260, 38));
        studentBtn.setMaximumSize(new Dimension(260, 38));
        studentBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        studentBtn.addActionListener(e -> {
            new StudentForm().setVisible(true);
            dispose();
        });
        card.add(studentBtn);
        card.add(Box.createRigidArea(new Dimension(0, 18)));

        // ----- Divider -----
        card.add(buildDivider());
        card.add(Box.createRigidArea(new Dimension(0, 18)));

        // ----- Registrar section -----
        JLabel adminTitle = new JLabel("Registrar Login");
        adminTitle.setFont(sans(Font.BOLD, 14));
        adminTitle.setForeground(TEXT);
        adminTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(adminTitle);
        card.add(Box.createRigidArea(new Dimension(0, 12)));

        // ----- Login form inputs -----
        JTextField userField = new JTextField();
        JPasswordField passField = new JPasswordField();
        styleField(userField);
        styleField(passField);
        Dimension fieldSize = new Dimension(170, 30);
        userField.setPreferredSize(fieldSize);
        passField.setPreferredSize(fieldSize);

        JPanel loginForm = new JPanel(new GridBagLayout());
        loginForm.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();

        c.gridx = 0; c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 0, 4, 10);
        loginForm.add(formLabel("Username"), c);

        c.gridx = 1;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 4, 0);
        loginForm.add(userField, c);

        c.gridx = 0; c.gridy = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(4, 0, 4, 10);
        loginForm.add(formLabel("Password"), c);

        c.gridx = 1;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 4, 0);
        loginForm.add(passField, c);

        loginForm.setMaximumSize(new Dimension(260, loginForm.getPreferredSize().height));
        loginForm.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(loginForm);
        card.add(Box.createRigidArea(new Dimension(0, 16)));

        // ----- Login button -----
        loginBtn = createButton("Login");
        loginBtn.setPreferredSize(new Dimension(150, 34));
        loginBtn.setMaximumSize(new Dimension(150, 34));
        loginBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginBtn.addActionListener(e -> doLogin(userField, passField));
        card.add(loginBtn);

        background.add(card);
        return background;
    }

    private void doLogin(JTextField userField, JPasswordField passField) {
        String user = userField.getText().trim();
        String pass = new String(passField.getPassword());

        // 1. Kapag walang input sa Username pero may Password
        if (user.isEmpty() && !pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please input username.",
                    "Input Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 2. Kapag may Username pero walang Password
        if (!user.isEmpty() && pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please input password.",
                    "Input Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 3. Credentials checking and error handling
        boolean isUserCorrect = user.equals(ADMIN_USER);
        boolean isPassCorrect = pass.equals(ADMIN_PASS);

        if (isUserCorrect && isPassCorrect) {
            new AdminDashboard(user).setVisible(true);
            dispose();
        } else if (!isUserCorrect && !isPassCorrect && !user.isEmpty() && !pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Invalid Credentials",
                    "Login Failed", JOptionPane.ERROR_MESSAGE);
            passField.setText("");
        } else if (!isUserCorrect && !user.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please input correct username.",
                    "Login Failed", JOptionPane.ERROR_MESSAGE);
        } else if (!isPassCorrect && !pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please input correct password.",
                    "Login Failed", JOptionPane.ERROR_MESSAGE);
            passField.setText("");
        } else {
            JOptionPane.showMessageDialog(this, "Invalid Credentials",
                    "Login Failed", JOptionPane.ERROR_MESSAGE);
            passField.setText("");
        }
    }

    // ------------------------------------------------------------------ Helpers
    /** Plain flat button: solid orange, small corner radius, no gradient or shadow. */
    private JButton createButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean pressed = getModel().isPressed();
                boolean hover = getModel().isRollover();
                g2.setColor(pressed || hover ? ORANGE_DARK : ORANGE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(sans(Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setRolloverEnabled(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JLabel formLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(sans(Font.PLAIN, 13));
        l.setForeground(TEXT);
        return l;
    }

    /** Plain input: grey outline that turns orange while it has focus. */
    private void styleField(JTextComponent field) {
        field.setFont(sans(Font.PLAIN, 13));
        field.setForeground(TEXT);
        field.setBackground(Color.WHITE);
        field.setCaretColor(TEXT);
        field.setBorder(fieldBorder(FIELD_BORDER));
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { field.setBorder(fieldBorder(ORANGE)); }
            @Override public void focusLost(FocusEvent e)   { field.setBorder(fieldBorder(FIELD_BORDER)); }
        });
    }

    private static CompoundBorder fieldBorder(Color line) {
        return new CompoundBorder(new LineBorder(line, 1, false), new EmptyBorder(3, 7, 3, 7));
    }

    private JComponent buildDivider() {
        JPanel divider = new JPanel(new GridBagLayout());
        divider.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        divider.add(rule(), c);

        c.weightx = 0;
        c.insets = new Insets(0, 10, 0, 10);
        JLabel orLabel = new JLabel("or");
        orLabel.setForeground(MUTED);
        orLabel.setFont(sans(Font.PLAIN, 12));
        divider.add(orLabel, c);

        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        divider.add(rule(), c);

        divider.setMaximumSize(new Dimension(260, 20));
        divider.setAlignmentX(Component.CENTER_ALIGNMENT);
        return divider;
    }

    private static JComponent rule() {
        JSeparator s = new JSeparator();
        s.setForeground(CARD_BORDER);
        return s;
    }

    private static String pickSans() {
        try {
            Set<String> names = new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String n : new String[]{"Segoe UI", "Helvetica Neue", "Arial"}) {
                if (names.contains(n)) return n;
            }
        } catch (Throwable ignored) {
        }
        return Font.SANS_SERIF;
    }

    private static Font sans(int style, int size) {
        return new Font(SANS, style, size);
    }
}