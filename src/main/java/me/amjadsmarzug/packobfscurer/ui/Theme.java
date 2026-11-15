package me.amjadsmarzug.packobfscurer.ui;

import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.Color;
import java.awt.Font;

/**
 * Black-and-white palette that follows the operating system's light or dark setting.
 *
 * Colours are chosen once, by {@link #install(Mode)}, before any component is built.
 * Only greys are used: black and white, with shades in between.
 */
final class Theme {

    enum Mode { LIGHT, DARK }

    // Colours. Assigned by install(); the defaults are the dark palette.
    static Color BG = rgb(0x000000);
    static Color CARD = rgb(0x111111);
    static Color CARD_HI = rgb(0x1C1C1C);
    static Color INPUT = rgb(0x0A0A0A);
    static Color BORDER = rgb(0x333333);
    static Color TEXT = rgb(0xF2F2F2);
    static Color MUTED = rgb(0x9A9A9A);
    /** Fill for primary buttons, ticked boxes and the drop-zone highlight. */
    static Color ACCENT = rgb(0xFFFFFF);
    static Color ACCENT_HI = rgb(0xD9D9D9);
    static Color ACCENT_DIM = rgb(0x3A3A3A);
    /** Text drawn on top of ACCENT. */
    static Color ON_ACCENT = rgb(0x000000);
    static Color OK = rgb(0xFFFFFF);
    static Color WARN = rgb(0xBDBDBD);
    static Color ERROR = rgb(0xFFFFFF);

    static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 22);
    static final Font HEADING = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    static final Font BIG_NUMBER = new Font(Font.SANS_SERIF, Font.BOLD, 20);
    static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private Theme() {
    }

    private static Color rgb(int v) {
        return new Color(v);
    }

    /** Sets the palette for the given mode. Call before building any component. */
    static void setMode(Mode mode) {
        if (mode == Mode.LIGHT) {
            BG = rgb(0xFFFFFF);
            CARD = rgb(0xF4F4F4);
            CARD_HI = rgb(0xE6E6E6);
            INPUT = rgb(0xFFFFFF);
            BORDER = rgb(0xBDBDBD);
            TEXT = rgb(0x000000);
            MUTED = rgb(0x5E5E5E);
            ACCENT = rgb(0x000000);
            ACCENT_HI = rgb(0x333333);
            ACCENT_DIM = rgb(0xCCCCCC);
            ON_ACCENT = rgb(0xFFFFFF);
            OK = rgb(0x000000);
            WARN = rgb(0x555555);
            ERROR = rgb(0x000000);
        } else {
            BG = rgb(0x000000);
            CARD = rgb(0x111111);
            CARD_HI = rgb(0x1C1C1C);
            INPUT = rgb(0x0A0A0A);
            BORDER = rgb(0x333333);
            TEXT = rgb(0xF2F2F2);
            MUTED = rgb(0x9A9A9A);
            ACCENT = rgb(0xFFFFFF);
            ACCENT_HI = rgb(0xD9D9D9);
            ACCENT_DIM = rgb(0x3A3A3A);
            ON_ACCENT = rgb(0x000000);
            OK = rgb(0xFFFFFF);
            WARN = rgb(0xBDBDBD);
            ERROR = rgb(0xFFFFFF);
        }
    }

    /** Installs the palette and the Swing defaults for the given mode. */
    static void install(Mode mode) {
        setMode(mode);
        try {
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (Exception ignored) {
            // Metal is always available; keep the default if this ever fails.
        }
        UIManager.put("Panel.background", BG);
        UIManager.put("Panel.foreground", TEXT);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Label.font", BODY);
        UIManager.put("CheckBox.background", CARD);
        UIManager.put("CheckBox.foreground", TEXT);
        UIManager.put("CheckBox.font", BODY);
        UIManager.put("TextField.background", INPUT);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", TEXT);
        UIManager.put("TextArea.background", INPUT);
        UIManager.put("TextArea.foreground", TEXT);
        UIManager.put("TextArea.caretForeground", TEXT);
        UIManager.put("ScrollPane.background", CARD);
        UIManager.put("Viewport.background", INPUT);
        UIManager.put("ScrollBar.background", CARD);
        UIManager.put("ScrollBar.thumb", BORDER);
        UIManager.put("ScrollBar.thumbHighlight", CARD_HI);
        UIManager.put("ScrollBar.thumbShadow", BORDER);
        UIManager.put("ScrollBar.track", CARD);
        UIManager.put("Button.background", CARD_HI);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.gradient", null);
        UIManager.put("OptionPane.background", CARD);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("OptionPane.messageFont", BODY);
        UIManager.put("OptionPane.buttonFont", BODY);
        UIManager.put("ProgressBar.background", INPUT);
        UIManager.put("ProgressBar.foreground", ACCENT);
        UIManager.put("ProgressBar.selectionBackground", TEXT);
        UIManager.put("ProgressBar.selectionForeground", TEXT);
        UIManager.put("PopupMenu.background", CARD_HI);
        UIManager.put("MenuItem.background", CARD_HI);
        UIManager.put("MenuItem.foreground", TEXT);
        UIManager.put("MenuItem.selectionBackground", ACCENT_DIM);
        UIManager.put("MenuItem.selectionForeground", TEXT);
        UIManager.put("ToolTip.background", CARD_HI);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("FileChooser.background", CARD);
        UIManager.put("ComboBox.background", INPUT);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", ACCENT_DIM);
        UIManager.put("ComboBox.selectionForeground", TEXT);
        UIManager.put("ComboBox.buttonBackground", CARD_HI);
        UIManager.put("ComboBox.buttonShadow", BORDER);
        UIManager.put("ComboBox.buttonDarkShadow", BORDER);
        UIManager.put("ComboBox.buttonHighlight", CARD_HI);
        UIManager.put("ComboBox.disabledBackground", CARD);
    }
}
