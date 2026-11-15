package me.amjadsmarzug.packobfscurer.ui;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Calls one Runnable on any text change. */
final class SimpleDocListener implements DocumentListener {
    private final Runnable action;

    SimpleDocListener(Runnable action) {
        this.action = action;
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        action.run();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        action.run();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        action.run();
    }
}
