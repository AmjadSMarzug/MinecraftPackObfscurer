package me.amjadsmarzug.packobfscurer.ui;

import me.amjadsmarzug.packobfscurer.update.Installers;
import me.amjadsmarzug.packobfscurer.update.Release;
import me.amjadsmarzug.packobfscurer.update.UpdateChecker;
import me.amjadsmarzug.packobfscurer.update.UpdateService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

/**
 * The update window. It opens when a newer release exists. Update now downloads the installer, checks it,
 * and starts it, with the progress shown here.
 */
final class UpdateDialog extends JDialog {

    private final UpdateService.Status status;
    private final Release release;
    private final JTextArea log = new JTextArea(6, 46);
    private final JScrollPane logScroll = new JScrollPane(log);
    private final JProgressBar progress = new JProgressBar();
    private final JButton update = new Widgets.Button("Update now", true);
    private final JButton later = new Widgets.Button("Later", false);
    private final JButton page = new Widgets.Button("Release page", false);

    UpdateDialog(Frame owner, UpdateService.Status status) {
        super(owner, "Update available", false);
        this.status = status;
        this.release = status.latest().orElseThrow();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(Theme.BG);
        root.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));

        JPanel head = new JPanel(new GridLayout(2, 1, 0, 4));
        head.setOpaque(false);
        JLabel title = new JLabel("Pack Obfuscator " + release.version() + " is available");
        title.setFont(Theme.TITLE.deriveFont(18f));
        title.setForeground(Theme.TEXT);
        head.add(title);
        head.add(Widgets.muted("You have version " + status.current() + "."));
        root.add(head, BorderLayout.NORTH);

        JTextArea notes = new JTextArea(release.notes().isEmpty() ? "No release notes." : release.notes());
        notes.setEditable(false);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setFont(Theme.BODY);
        Widgets.styleField(notes);
        JScrollPane notesScroll = new JScrollPane(notes);
        notesScroll.setPreferredSize(new Dimension(520, 170));

        log.setEditable(false);
        log.setFont(Theme.MONO);
        log.setBackground(Theme.INPUT);
        log.setForeground(Theme.TEXT);
        log.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        logScroll.setVisible(false);

        progress.setIndeterminate(true);
        progress.setBorderPainted(false);
        progress.setVisible(false);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(notesScroll, BorderLayout.NORTH);
        center.add(logScroll, BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(page);
        buttons.add(later);
        buttons.add(update);
        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setOpaque(false);
        bottom.add(progress, BorderLayout.NORTH);
        bottom.add(buttons, BorderLayout.SOUTH);
        root.add(bottom, BorderLayout.SOUTH);

        update.addActionListener(e -> startUpdate());
        later.addActionListener(e -> dispose());
        page.addActionListener(e -> openPage());

        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
    }

    private void startUpdate() {
        Installers.Kind kind = Installers.detect();
        logScroll.setVisible(true);
        if (kind == Installers.Kind.OTHER) {
            appendLog("This install cannot update itself. Download the new archive from the release page.");
            pack();
            return;
        }
        switch (kind) {
            case WINDOWS -> appendLog("The installer will open. Finish it there.");
            case MACOS -> appendLog("The disk image will open. Drag the app into Applications.");
            case LINUX_RPM -> appendLog("You will be asked for your password to install the RPM.");
            default -> { }
        }
        update.setEnabled(false);
        later.setEnabled(false);
        progress.setVisible(true);
        pack();

        String token = UpdateChecker.tokenFromEnvironment();
        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                Path file = new UpdateService(UpdateService.currentVersion(), UpdateChecker.REPO, token)
                        .download(release, kind, msg -> publish(msg));
                publish("Starting the installer...");
                Installers.start(file, kind);
                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                chunks.forEach(UpdateDialog.this::appendLog);
            }

            @Override
            protected void done() {
                progress.setVisible(false);
                try {
                    get();
                    appendLog("Installer started. Restart PackObfuscator when it finishes.");
                    update.setText("Done");
                    later.setText("Close");
                    later.setEnabled(true);
                } catch (ExecutionException e) {
                    String message = e.getCause() == null || e.getCause().getMessage() == null
                            ? String.valueOf(e.getCause()) : e.getCause().getMessage();
                    appendLog("Update failed: " + message);
                    update.setEnabled(true);
                    later.setEnabled(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                pack();
            }
        };
        worker.execute();
    }

    private void openPage() {
        try {
            Desktop.getDesktop().browse(URI.create(release.pageUrl()));
        } catch (Exception e) {
            appendLog("Open this page to download the update: " + release.pageUrl());
            logScroll.setVisible(true);
            pack();
        }
    }

    private void appendLog(String line) {
        log.append(line + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }
}
