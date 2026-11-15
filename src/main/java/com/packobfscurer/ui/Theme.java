package com.packobfscurer.ui;

import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.Color;
import java.awt.Font;

/**
 * Colours and fonts for the app. Metal is used as the base look and feel so the window looks the
 * same on Windows, macOS and Linux. Only the colours and fonts above are customised.
 */
final class Theme {

    static final Color BG = new Color(0x0E1017);
    static final Color CARD = new Color(0x171A23);
    static final Color CARD_HI = new Color(0x1F2331);
    static final Color INPUT = new Color(0x0F1219);
    static final Color BORDER = new Color(0x2A2F3D);
    static final Color TEXT = new Color(0xE8EAF2);
    static final Color MUTED = new Color(0x8A92A6);
    static final Color ACCENT = new Color(0x7C6CFF);
    static final Color ACCENT_HI = new Color(0x9A8DFF);
    static final Color ACCENT_DIM = new Color(0x3A3370);
    static final Color OK = new Color(0x3DD68C);
    static final Color WARN = new Color(0xF5B942);
    static final Color ERROR = new Color(0xFF5C6C);

    static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 22);
    static final Font HEADING = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    static final Font BIG_NUMBER = new Font(Font.SANS_SERIF, Font.BOLD, 20);
    static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private Theme() {
    }

    static void install() {
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
