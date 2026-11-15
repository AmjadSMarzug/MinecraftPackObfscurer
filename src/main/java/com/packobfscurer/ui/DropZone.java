package com.packobfscurer.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** A dashed drop area that accepts a pack folder or .zip dragged from the file manager. */
final class DropZone extends Widgets.Card {

    private boolean hover;
    private Consumer<Path> onPick = p -> { };
    private Runnable onClick = () -> { };
    private final JLabel text = new JLabel("Drop a pack folder or .zip here", javax.swing.SwingConstants.CENTER);
    private final JLabel hint = new JLabel("or click to choose", javax.swing.SwingConstants.CENTER);

    DropZone() {
        super(Theme.INPUT, Theme.BORDER, 16, 10);
        setLayout(new java.awt.GridLayout(2, 1, 0, 4));
        text.setFont(Theme.HEADING);
        text.setForeground(Theme.TEXT);
        hint.setFont(Theme.SMALL);
        hint.setForeground(Theme.MUTED);
        add(text);
        add(hint);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        new DropTarget(this, new java.awt.dnd.DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetDragEvent e) {
                hover = true;
                repaint();
                e.acceptDrag(DnDConstants.ACTION_COPY);
            }

            @Override
            public void dragOver(DropTargetDragEvent e) {
                e.acceptDrag(DnDConstants.ACTION_COPY);
            }

            @Override
            public void dragExit(DropTargetEvent e) {
                hover = false;
                repaint();
            }

            @Override
            public void drop(DropTargetDropEvent e) {
                hover = false;
                repaint();
                try {
                    e.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) e.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) {
                        onPick.accept(files.get(0).toPath());
                    }
                    e.dropComplete(true);
                } catch (Exception ex) {
                    e.dropComplete(false);
                }
            }
        });
    }

    void onPick(Consumer<Path> handler) {
        this.onPick = handler;
    }

    void setClickAction(Runnable action) {
        this.onClick = action;
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                onClick.run();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = Widgets.quality(g);
        Color edge = hover ? Theme.ACCENT : Theme.BORDER;
        g2.setColor(hover ? Theme.ACCENT_DIM : Theme.INPUT);
        RoundRectangle2D shape = new RoundRectangle2D.Double(1, 1, getWidth() - 2, getHeight() - 2, 16, 16);
        g2.fill(shape);
        g2.setColor(edge);
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{7f, 5f}, 0f));
        g2.draw(shape);
        g2.dispose();
    }
}
