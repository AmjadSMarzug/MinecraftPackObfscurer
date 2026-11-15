package com.packobfscurer.ui;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/** Custom-painted components that give the app its rounded, dark look. */
final class Widgets {

    private Widgets() {
    }

    static Graphics2D quality(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g2;
    }

    /** A rounded card. */
    static class Card extends JPanel {
        private final int radius;
        private final Color fill;
        private final Color edge;

        Card() {
            this(Theme.CARD, Theme.BORDER, 16, 18);
        }

        Card(Color fill, Color edge, int radius, int pad) {
            this.fill = fill;
            this.edge = edge;
            this.radius = radius;
            setOpaque(false);
            setBorder(new EmptyBorder(pad, pad, pad, pad));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = quality(g);
            RoundRectangle2D shape = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1.0, getHeight() - 1.0, radius, radius);
            g2.setColor(fill);
            g2.fill(shape);
            g2.setColor(edge);
            g2.draw(shape);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** A card showing one number and its label. */
    static final class StatTile extends Card {
        private final JLabel value = new JLabel("-");
        private final JLabel label = new JLabel();

        StatTile(String title) {
            super(Theme.CARD, Theme.BORDER, 14, 14);
            setLayout(new java.awt.GridLayout(2, 1, 0, 2));
            value.setFont(Theme.BIG_NUMBER);
            label.setFont(Theme.SMALL);
            label.setForeground(Theme.MUTED);
            label.setText(title);
            add(value);
            add(label);
        }

        void set(String text, Color color) {
            value.setText(text);
            value.setForeground(color == null ? Theme.TEXT : color);
        }
    }

    /** A filled rounded button. The primary variant is the accent colour. */
    static final class Button extends JButton {
        private final boolean primary;

        Button(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(primary ? Theme.HEADING : Theme.BODY);
            setForeground(primary ? Color.WHITE : Theme.TEXT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(primary ? 12 : 8, primary ? 26 : 16, primary ? 12 : 8, primary ? 26 : 16));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = quality(g);
            RoundRectangle2D shape = new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 14, 14);
            if (!isEnabled()) {
                g2.setColor(primary ? Theme.ACCENT_DIM : Theme.CARD_HI);
                setForeground(Theme.MUTED);
            } else if (primary) {
                g2.setColor(getModel().isPressed() ? Theme.ACCENT_DIM : getModel().isRollover() ? Theme.ACCENT_HI : Theme.ACCENT);
            } else {
                g2.setColor(getModel().isPressed() ? Theme.BORDER : getModel().isRollover() ? Theme.CARD_HI : Theme.CARD);
            }
            g2.fill(shape);
            if (!primary) {
                g2.setColor(Theme.BORDER);
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 14, 14));
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Rounded checkbox with a custom box. */
    static final class CheckIcon implements Icon {
        private static final int SIZE = 18;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = quality(g);
            boolean on = c instanceof AbstractButton b && b.isSelected();
            RoundRectangle2D box = new RoundRectangle2D.Double(x + 1, y + 1, SIZE - 2, SIZE - 2, 6, 6);
            if (on) {
                g2.setColor(Theme.ACCENT);
                g2.fill(box);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D tick = new Path2D.Double();
                tick.moveTo(x + 5, y + 9.5);
                tick.lineTo(x + 8, y + 12.5);
                tick.lineTo(x + 13.5, y + 6);
                g2.draw(tick);
            } else {
                g2.setColor(Theme.INPUT);
                g2.fill(box);
                g2.setColor(Theme.BORDER);
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(box);
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }

    static void styleCheck(AbstractButton box) {
        box.setIcon(new CheckIcon());
        box.setSelectedIcon(new CheckIcon());
        box.setOpaque(false);
        box.setFocusPainted(false);
        box.setForeground(Theme.TEXT);
        box.setFont(Theme.BODY);
        box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    static void styleField(javax.swing.text.JTextComponent field) {
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        field.setBackground(Theme.INPUT);
        field.setForeground(Theme.TEXT);
        field.setCaretColor(Theme.TEXT);
        field.setSelectionColor(Theme.ACCENT_DIM);
        field.setSelectedTextColor(Theme.TEXT);
    }

    static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Theme.MUTED);
        l.setFont(Theme.SMALL);
        return l;
    }

    static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.HEADING);
        l.setForeground(Theme.TEXT);
        return l;
    }
}
