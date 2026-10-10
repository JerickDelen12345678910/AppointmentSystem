package utils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads the school logo once and hands it out at any size.
 * Looks for logo.png (or logo.jpg) in the project folder, in src/, or on the classpath.
 * If no file is found the screens simply show a "LOGO" placeholder box.
 */
public final class LogoLoader {
    private static final String[] FILE_NAMES = {"logo.png", "logo.jpg"};
    private static final String[] FOLDERS = {"", "src/", "resources/"};

    private static BufferedImage source;
    private static boolean searched = false;
    private static final Map<Integer, ImageIcon> cache = new HashMap<>();

    private LogoLoader() {}

    /** The logo scaled to fit a size x size box, or null if there is no logo file. */
    public static synchronized ImageIcon icon(int size) {
        BufferedImage src = source();
        if (src == null) return null;
        return cache.computeIfAbsent(size, s -> new ImageIcon(scale(src, s)));
    }

    /** A label showing the logo at the given size (or a placeholder box of the same size). */
    public static JLabel label(int size) {
        ImageIcon icon = icon(size);
        JLabel label;
        if (icon != null) {
            label = new JLabel(icon);
        } else {
            label = new JLabel("LOGO", SwingConstants.CENTER);
            label.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        }
        Dimension d = new Dimension(size, size);
        label.setPreferredSize(d);
        label.setMinimumSize(d);
        label.setMaximumSize(d);
        return label;
    }

    /** Uses the logo as the window's title-bar / taskbar icon. */
    public static void applyWindowIcon(Window window) {
        ImageIcon icon = icon(64);
        if (icon != null) window.setIconImage(icon.getImage());
    }

    // ---------------------------------------------------------------- loading
    private static BufferedImage source() {
        if (searched) return source;
        searched = true;

        for (String name : FILE_NAMES) {
            for (String folder : FOLDERS) {
                File f = new File(folder + name);
                if (f.isFile() && (source = read(f)) != null) return source;
            }
            URL url = LogoLoader.class.getResource("/" + name);
            if (url != null) {
                try {
                    source = ImageIO.read(url);
                    if (source != null) return source;
                } catch (IOException ignored) { }
            }
        }
        return null;
    }

    private static BufferedImage read(File f) {
        try {
            return ImageIO.read(f);
        } catch (IOException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- scaling
    /** Shrinks in halving steps, then a final bicubic step, so the seal stays crisp. */
    private static BufferedImage scale(BufferedImage src, int size) {
        double ratio = Math.min((double) size / src.getWidth(), (double) size / src.getHeight());
        int tw = Math.max(1, (int) Math.round(src.getWidth() * ratio));
        int th = Math.max(1, (int) Math.round(src.getHeight() * ratio));

        BufferedImage img = src;
        int w = img.getWidth(), h = img.getHeight();
        while (w / 2 >= tw && h / 2 >= th) {
            w /= 2;
            h /= 2;
            img = draw(img, w, h);
        }
        return draw(img, tw, th);
    }

    private static BufferedImage draw(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }
}
