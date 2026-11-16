package me.amjadsmarzug.packobfscurer.ui;

import me.amjadsmarzug.packobfscurer.core.ObfuscationResult;
import me.amjadsmarzug.packobfscurer.core.PackObfuscator;
import me.amjadsmarzug.packobfscurer.core.PackBatch;
import me.amjadsmarzug.packobfscurer.update.Installers;
import me.amjadsmarzug.packobfscurer.update.Release;
import me.amjadsmarzug.packobfscurer.update.UpdateChecker;
import me.amjadsmarzug.packobfscurer.update.UpdateService;
import me.amjadsmarzug.packobfscurer.core.PackOptions;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

/** The main window. Work runs on a SwingWorker so the UI stays responsive. */
public final class MainWindow extends JFrame {

    private static final Preferences PREFS = Preferences.userNodeForPackage(MainWindow.class);
    private static final String PREF_INPUT = "input";
    private static final String PREF_OUTPUT = "output";
    private static final String PREF_SEED = "seed";
    private static final String PREF_DESC = "description";
    private static final String PREF_PROTECTED = "protected";
    private static final String PREF_PRESET = "preset";
    private static final String PREF_LAST_UPDATE_CHECK = "lastUpdateCheck";
    private static final long UPDATE_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000;
    private static final String CUSTOM = "Custom";

    private Path input;

    private final DropZone dropZone = new DropZone();
    private final JLabel inputName = new JLabel("No pack selected");
    private final JTextField outputField = new JTextField();
    private final JButton browseOutput = new Widgets.Button("Change", false);

    private final JCheckBox renameModels = new JCheckBox("Rename models");
    private final JCheckBox renameTextures = new JCheckBox("Rename textures");
    private final JCheckBox renameSounds = new JCheckBox("Rename sounds");
    private final JCheckBox renameNamespaces = new JCheckBox("Rename namespaces");
    private final JCheckBox renameCubes = new JCheckBox("Rename cube names");
    private final JCheckBox removeGroups = new JCheckBox("Delete groups");
    private final JCheckBox minifyJson = new JCheckBox("Minify JSON");
    private final JCheckBox stripJunk = new JCheckBox("Strip junk files");
    private final JCheckBox stripMetadata = new JCheckBox("Strip metadata");
    private final JCheckBox splitTextures = new JCheckBox("Split model textures");
    private final JComboBox<String> presetBox = new JComboBox<>(presetLabels());
    private boolean applyingPreset;
    private final JButton batchButton = new Widgets.Button("Batch folder...", false);
    private final JCheckBox writeMapping = new JCheckBox("Save name map");

    private final JTextField seedField = new JTextField();
    private final JTextField descriptionField = new JTextField();
    private final JTextArea protectedArea = new JTextArea(6, 20);
    private final JSpinner junkModels = new JSpinner(new SpinnerNumberModel(25, 0, PackOptions.Junk.MAX, 1));
    private final JSpinner junkTextures = new JSpinner(new SpinnerNumberModel(25, 0, PackOptions.Junk.MAX, 1));
    private final JSpinner junkSounds = new JSpinner(new SpinnerNumberModel(5, 0, PackOptions.Junk.MAX, 1));

    private final Widgets.StatTile tileFiles = new Widgets.StatTile("Files");
    private final Widgets.StatTile tileSize = new Widgets.StatTile("Size");
    private final Widgets.StatTile tileRenamed = new Widgets.StatTile("Renamed");
    private final Widgets.StatTile tileWarnings = new Widgets.StatTile("Warnings");

    private final JTextArea log = new JTextArea();
    private final JProgressBar progress = new JProgressBar();
    private final JLabel status = new JLabel("Pick a pack to begin.");
    private final JButton runButton = new Widgets.Button("Obfuscate pack", true);
    private final JButton openButton = new Widgets.Button("Open output folder", false);
    private final JButton clearButton = new Widgets.Button("Clear log", false);

    private Path lastOutput;

    private final JPanel updateBar = new Widgets.Card(Theme.CARD_HI, Theme.BORDER, 12, 10);
    private final JLabel updateText = Widgets.muted("");
    private final JButton updateButton = new Widgets.Button("Update", true);
    private final JButton laterButton = new Widgets.Button("Later", false);
    private Release availableRelease;
    private SwingWorker<?, String> worker;

    private MainWindow() {
        super("Pack Obfuscator");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (worker != null && !worker.isDone()) {
                    int choice = JOptionPane.showConfirmDialog(MainWindow.this,
                            "An obfuscation is still running. Quit anyway?", "Quit",
                            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (choice != JOptionPane.YES_OPTION) {
                        return;
                    }
                }
                savePrefs();
                dispose();
                System.exit(0);
            }
        });

        buildUi();
        loadPrefs();
        checkForUpdates();
        updateRunState();

        setMinimumSize(new Dimension(1000, 900));
        setSize(1180, 960);
        setLocationRelativeTo(null);
    }

    public static void open() {
        Theme.install(SystemTheme.detect());
        MainWindow w = new MainWindow();
        w.setVisible(true);
    }

    // ------------------------------------------------------------------ layout

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(Theme.BG);
        root.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));
        setContentPane(root);

        root.add(topBar(), BorderLayout.NORTH);

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, 0, 0, 0);

        // Left column: input and options
        JPanel left = new JPanel(new BorderLayout(0, 14));
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(380, 0));
        left.add(inputCard(), BorderLayout.NORTH);
        left.add(optionsCard(), BorderLayout.CENTER);
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        c.weighty = 1;
        body.add(left, c);

        // Right column: stats and log
        JPanel right = new JPanel(new BorderLayout(0, 14));
        right.setOpaque(false);
        JPanel tiles = new JPanel(new GridLayout(1, 4, 12, 0));
        tiles.setOpaque(false);
        tiles.add(tileFiles);
        tiles.add(tileSize);
        tiles.add(tileRenamed);
        tiles.add(tileWarnings);
        right.add(tiles, BorderLayout.NORTH);
        right.add(logCard(), BorderLayout.CENTER);
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 16, 0, 0);
        body.add(right, c);

        root.add(body, BorderLayout.CENTER);
        root.add(footer(), BorderLayout.SOUTH);

        // Output path and selected options are the only text inputs that need a listener.
        outputField.getDocument().addDocumentListener(new SimpleDocListener(this::updateRunState));
        seedField.getDocument().addDocumentListener(new SimpleDocListener(this::updateRunState));

        for (JCheckBox box : allCheckBoxes()) {
            Widgets.styleCheck(box);
            box.addItemListener(e -> markCustom());
        }
        for (JSpinner spinner : List.of(junkModels, junkTextures, junkSounds)) {
            spinner.addChangeListener(e -> markCustom());
        }
        presetBox.addActionListener(e -> {
            String choice = (String) presetBox.getSelectedItem();
            if (!applyingPreset && choice != null && !choice.equals(CUSTOM)) {
                applyPreset(PackOptions.Preset.fromName(choice));
            }
        });
        batchButton.addActionListener(e -> batchFolder());
        for (JTextField f : List.of(outputField, seedField, descriptionField)) {
            Widgets.styleField(f);
        }
        for (JSpinner spinner : List.of(junkModels, junkTextures, junkSounds)) {
            JTextField editor = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
            Widgets.styleField(editor);
            spinner.setBorder(BorderFactory.createEmptyBorder());
        }
        Widgets.styleField(protectedArea);
        protectedArea.setFont(Theme.MONO);
        protectedArea.setLineWrap(false);
        log.setEditable(false);
        log.setFont(Theme.MONO);
        log.setBackground(Theme.INPUT);
        log.setForeground(Theme.TEXT);
        log.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        log.setCaretColor(Theme.TEXT);
        progress.setBorderPainted(false);
        progress.setPreferredSize(new Dimension(220, 6));
        progress.setVisible(false);
        status.setFont(Theme.SMALL);
        status.setForeground(Theme.MUTED);
    }

    /** The update bar sits above the header. It stays hidden unless a newer release exists. */
    private JPanel topBar() {
        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        updateBar.setLayout(new BorderLayout(12, 0));
        updateBar.setVisible(false);
        updateText.setForeground(Theme.TEXT);
        updateBar.add(updateText, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        laterButton.setFont(Theme.SMALL);
        updateButton.setFont(Theme.SMALL);
        buttons.add(laterButton);
        buttons.add(updateButton);
        updateBar.add(buttons, BorderLayout.EAST);
        updateButton.addActionListener(e -> installUpdate());
        laterButton.addActionListener(e -> updateBar.setVisible(false));
        top.add(updateBar, BorderLayout.NORTH);
        top.add(header(), BorderLayout.CENTER);
        return top;
    }

    /** Checks GitHub at most once a day, in the background. Offline or failing checks stay silent. */
    private void checkForUpdates() {
        long last = PREFS.getLong(PREF_LAST_UPDATE_CHECK, 0);
        if (System.currentTimeMillis() - last < UPDATE_CHECK_INTERVAL_MS) {
            return;
        }
        String token = UpdateChecker.tokenFromEnvironment();
        SwingWorker<UpdateService.Status, Void> check = new SwingWorker<>() {
            @Override
            protected UpdateService.Status doInBackground() throws Exception {
                PREFS.putLong(PREF_LAST_UPDATE_CHECK, System.currentTimeMillis());
                return new UpdateService(UpdateService.currentVersion(), UpdateChecker.REPO, token).check();
            }

            @Override
            protected void done() {
                try {
                    UpdateService.Status status = get();
                    if (status.updateAvailable()) {
                        showUpdate(status.latest().orElseThrow());
                    }
                } catch (Exception ignored) {
                    // No network, or the repository is private and no token is set. Not worth a dialog.
                }
            }
        };
        check.execute();
    }

    private void showUpdate(Release release) {
        availableRelease = release;
        updateText.setText("Version " + release.version() + " is available. You have " + UpdateService.currentVersion() + ".");
        updateBar.setVisible(true);
        updateBar.revalidate();
    }

    private void installUpdate() {
        if (availableRelease == null) {
            return;
        }
        Installers.Kind kind = Installers.detect();
        if (kind == Installers.Kind.OTHER) {
            openReleasePage();
            return;
        }
        String how = switch (kind) {
            case WINDOWS -> "The installer will open. Finish it there.";
            case MACOS -> "The disk image will open. Drag the app into Applications.";
            case LINUX_RPM -> "You will be asked for your password to install the RPM.";
            default -> "";
        };
        int choice = JOptionPane.showConfirmDialog(this,
                "Download and install version " + availableRelease.version() + "?\n" + how,
                "Update", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        Release release = availableRelease;
        String token = UpdateChecker.tokenFromEnvironment();
        setBusy(true);
        appendLog("== Update to " + release.version());
        worker = new SwingWorker<java.nio.file.Path, String>() {
            @Override
            protected java.nio.file.Path doInBackground() throws Exception {
                return new UpdateService(UpdateService.currentVersion(), UpdateChecker.REPO, token)
                        .download(release, kind, msg -> publish(msg));
            }

            @Override
            protected void process(List<String> chunks) {
                chunks.forEach(MainWindow.this::appendLog);
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    Installers.start(get(), kind);
                    status.setText("Installer started. Restart PackObfuscator when it finishes.");
                    JOptionPane.showMessageDialog(MainWindow.this,
                            "The update to " + release.version() + " is ready. Restart PackObfuscator after it finishes.",
                            "Update", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    String message = e.getCause() != null && e.getCause().getMessage() != null
                            ? e.getCause().getMessage() : e.getMessage();
                    fail(message == null ? e.toString() : message);
                }
            }
        };
        worker.execute();
    }

    private void openReleasePage() {
        if (availableRelease == null) {
            return;
        }
        try {
            Desktop.getDesktop().browse(java.net.URI.create(availableRelease.pageUrl()));
        } catch (Exception e) {
            appendLog("Open this page to download the update: " + availableRelease.pageUrl());
        }
    }

    private JPanel header() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 2));
        titles.setOpaque(false);
        JLabel title = new JLabel("Pack Obfuscator");
        title.setFont(Theme.TITLE);
        title.setForeground(Theme.TEXT);
        titles.add(title);
        titles.add(Widgets.muted("Renames models, textures and sounds and rewrites every reference, so a shared pack reveals less."));
        p.add(titles, BorderLayout.WEST);
        JLabel version = Widgets.muted("v" + versionText());
        p.add(version, BorderLayout.EAST);
        return p;
    }

    private JPanel inputCard() {
        Widgets.Card card = new Widgets.Card();
        card.setLayout(new BorderLayout(0, 12));
        card.add(Widgets.heading("Resource pack"), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        dropZone.setPreferredSize(new Dimension(0, 110));
        dropZone.onPick(this::setInput);
        dropZone.setClickAction(this::chooseInput);
        center.add(dropZone, BorderLayout.NORTH);

        inputName.setFont(Theme.SMALL);
        inputName.setForeground(Theme.MUTED);
        center.add(inputName, BorderLayout.CENTER);

        JPanel outRow = new JPanel(new BorderLayout(8, 0));
        outRow.setOpaque(false);
        JLabel outLabel = Widgets.muted("Output zip");
        JPanel outTop = new JPanel(new BorderLayout());
        outTop.setOpaque(false);
        outTop.add(outLabel, BorderLayout.NORTH);
        outRow.add(outTop, BorderLayout.NORTH);
        JPanel outFields = new JPanel(new BorderLayout(8, 0));
        outFields.setOpaque(false);
        outFields.add(outputField, BorderLayout.CENTER);
        browseOutput.addActionListener(e -> chooseOutput());
        outFields.add(browseOutput, BorderLayout.EAST);
        outRow.add(outFields, BorderLayout.CENTER);
        center.add(outRow, BorderLayout.SOUTH);

        card.add(center, BorderLayout.CENTER);
        return card;
    }

    private JPanel optionsCard() {
        Widgets.Card card = new Widgets.Card();
        card.setLayout(new BorderLayout(0, 12));
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.add(Widgets.heading("Options"), BorderLayout.WEST);
        presetBox.setFont(Theme.SMALL);
        presetBox.setPreferredSize(new Dimension(130, 28));
        header.add(presetBox, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        JPanel form = new JPanel(new BorderLayout(0, 12));
        form.setOpaque(false);

        JPanel checks = new JPanel(new GridLayout(0, 2, 8, 6));
        checks.setOpaque(false);
        checks.add(renameModels);
        checks.add(renameTextures);
        checks.add(renameSounds);
        checks.add(renameNamespaces);
        checks.add(renameCubes);
        checks.add(removeGroups);
        checks.add(minifyJson);
        checks.add(stripJunk);
        checks.add(splitTextures);
        checks.add(stripMetadata);
        checks.add(writeMapping);
        form.add(checks, BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridLayout(1, 2, 12, 0));
        fields.setOpaque(false);
        fields.add(labelled("Seed (blank = random)", seedField));
        fields.add(labelled("Description (blank = keep)", descriptionField));

        JPanel junk = new JPanel(new GridLayout(1, 3, 12, 0));
        junk.setOpaque(false);
        junk.add(labelled("Junk models", junkModels));
        junk.add(labelled("Junk textures", junkTextures));
        junk.add(labelled("Junk sounds", junkSounds));

        JPanel top = new JPanel(new GridLayout(0, 1, 0, 10));
        top.setOpaque(false);
        top.add(fields);
        top.add(junk);

        JPanel middle = new JPanel(new BorderLayout(0, 12));
        middle.setOpaque(false);
        middle.add(top, BorderLayout.NORTH);

        JPanel protectedBox = new JPanel(new BorderLayout(0, 4));
        protectedBox.setOpaque(false);
        JPanel protectedHeader = new JPanel(new BorderLayout());
        protectedHeader.setOpaque(false);
        protectedHeader.add(Widgets.muted("Keep these asset folders (one per line)"), BorderLayout.WEST);
        Widgets.Button reset = new Widgets.Button("Reset", false);
        reset.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        reset.setFont(Theme.SMALL);
        reset.addActionListener(e -> protectedArea.setText(String.join("\n", PackOptions.DEFAULT_PROTECTED)));
        protectedHeader.add(reset, BorderLayout.EAST);
        protectedBox.add(protectedHeader, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(protectedArea);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
        scroll.getViewport().setBackground(Theme.INPUT);
        protectedBox.add(scroll, BorderLayout.CENTER);
        middle.add(protectedBox, BorderLayout.CENTER);

        form.add(middle, BorderLayout.CENTER);
        card.add(form, BorderLayout.CENTER);
        return card;
    }

    private static JPanel labelled(String text, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        p.add(Widgets.muted(text), BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    private JPanel logCard() {
        Widgets.Card card = new Widgets.Card();
        card.setLayout(new BorderLayout(0, 10));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(Widgets.heading("Activity"), BorderLayout.WEST);
        clearButton.setFont(Theme.SMALL);
        clearButton.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        clearButton.addActionListener(e -> log.setText(""));
        top.add(clearButton, BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(log);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
        scroll.getViewport().setBackground(Theme.INPUT);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private JPanel footer() {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(status);
        left.add(progress);
        p.add(left, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        openButton.addActionListener(e -> openOutputFolder());
        openButton.setEnabled(false);
        runButton.addActionListener(e -> run());
        batchButton.setFont(Theme.SMALL);
        buttons.add(batchButton);
        buttons.add(openButton);
        buttons.add(runButton);
        p.add(buttons, BorderLayout.EAST);
        return p;
    }

    // ------------------------------------------------------------------ actions

    private void chooseInput() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem folder = new JMenuItem("Choose a pack folder...");
        folder.addActionListener(e -> pickFolder());
        JMenuItem zip = new JMenuItem("Choose a .zip file...");
        zip.addActionListener(e -> pickZip());
        menu.add(folder);
        menu.add(zip);
        menu.show(dropZone, 20, dropZone.getHeight() - 10);
    }

    private void pickFolder() {
        JFileChooser fc = new JFileChooser(input == null ? null : input.getParent().toFile());
        fc.setDialogTitle("Choose a resource pack folder");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            setInput(fc.getSelectedFile().toPath());
        }
    }

    private void pickZip() {
        JFileChooser fc = new JFileChooser(input == null ? null : input.getParent().toFile());
        fc.setDialogTitle("Choose a resource pack .zip");
        fc.setFileFilter(new FileNameExtensionFilter("Resource pack (.zip)", "zip"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            setInput(fc.getSelectedFile().toPath());
        }
    }

    private void chooseOutput() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save obfuscated pack as");
        fc.setFileFilter(new FileNameExtensionFilter("Resource pack (.zip)", "zip"));
        fc.setSelectedFile(new File(outputPathOrDefault().getFileName().toString()));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            String name = fc.getSelectedFile().getName();
            if (!name.toLowerCase().endsWith(".zip")) {
                name += ".zip";
            }
            outputField.setText(fc.getSelectedFile().toPath().resolveSibling(name).toString());
        }
    }

    private void setInput(Path p) {
        if (Files.isDirectory(p)) {
            if (!Files.isRegularFile(p.resolve("pack.mcmeta"))) {
                warnNotAPack(p);
                return;
            }
        } else if (!(Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".zip"))) {
            JOptionPane.showMessageDialog(this, "Drop a pack folder or a .zip file.", "Not a pack", JOptionPane.WARNING_MESSAGE);
            return;
        }
        input = p.toAbsolutePath().normalize();
        inputName.setText(input.toString());
        inputName.setForeground(Theme.TEXT);
        outputField.setText(PackObfuscator.defaultOutput(input).toString());
        status.setText("Ready. Review the options, then obfuscate.");
        updateRunState();
    }

    private void warnNotAPack(Path p) {
        JOptionPane.showMessageDialog(this,
                "That folder has no pack.mcmeta file, so it is not a resource pack.\n" + p,
                "Not a pack", JOptionPane.WARNING_MESSAGE);
    }

    private Path outputPathOrDefault() {
        String text = outputField.getText().trim();
        if (!text.isEmpty()) {
            return Path.of(text);
        }
        return input == null ? Path.of("obfuscated.zip") : PackObfuscator.defaultOutput(input);
    }

    private void updateRunState() {
        boolean idle = worker == null || worker.isDone();
        runButton.setEnabled(idle && input != null);
        browseOutput.setEnabled(idle);
        openButton.setEnabled(idle && lastOutput != null);
    }

    private void run() {
        if (input == null) {
            return;
        }
        Long seed = null;
        String seedText = seedField.getText().trim();
        if (!seedText.isEmpty()) {
            try {
                seed = Long.parseLong(seedText);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "The seed must be a whole number, or blank for random names.",
                        "Seed", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        PackOptions.Junk junk = junkSettings();
        if (!renameModels.isSelected() && !renameTextures.isSelected() && !renameSounds.isSelected()
                && !renameNamespaces.isSelected() && !renameCubes.isSelected() && !removeGroups.isSelected()
                && !minifyJson.isSelected() && !stripJunk.isSelected() && !stripMetadata.isSelected()
                && junk.total() == 0) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Every option is off, so the output will be a plain copy of the pack. Continue?",
                    "Nothing to do", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }

        Path out = outputPathOrDefault();
        PackOptions opts = currentOptions(seed, junk);

        setBusy(true);
        appendLog("");
        appendLog("== " + DateFormat.getTimeInstance().format(new java.util.Date()) + "  " + input.getFileName() + " -> " + out.getFileName());
        final Path source = input;
        worker = new SwingWorker<>() {
            @Override
            protected ObfuscationResult doInBackground() throws IOException {
                return PackObfuscator.run(source, out, opts, msg -> publish(msg));
            }

            @Override
            protected void process(List<String> chunks) {
                for (String s : chunks) {
                    appendLog(s);
                }
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    showResult((ObfuscationResult) get());
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    fail(cause.getMessage() == null ? cause.toString() : cause.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    fail("Interrupted.");
                }
            }
        };
        worker.execute();
    }

    private PackOptions.Junk junkSettings() {
        return new PackOptions.Junk((Integer) junkModels.getValue(), (Integer) junkTextures.getValue(),
                (Integer) junkSounds.getValue());
    }

    private List<String> protectedList() {
        List<String> list = new ArrayList<>();
        for (String line : protectedArea.getText().split("\\R")) {
            String t = line.trim().replace('\\', '/');
            if (t.isEmpty()) {
                continue;
            }
            list.add(t.endsWith("/") ? t : t + "/");
        }
        return list;
    }

    private void setBusy(boolean busy) {
        progress.setIndeterminate(busy);
        progress.setVisible(busy);
        status.setText(busy ? "Obfuscating..." : status.getText());
        updateRunState();
    }

    private void showResult(ObfuscationResult r) {
        lastOutput = r.output();
        long reduced = r.inputBytes() == 0 ? 0 : Math.round(100.0 * (r.inputBytes() - r.outputBytes()) / r.inputBytes());
        tileFiles.set(r.inputFiles() + " → " + r.outputFiles(), null);
        tileSize.set(PackObfuscator.human(r.inputBytes()) + " → " + PackObfuscator.human(r.outputBytes()), null);
        tileRenamed.set(String.valueOf(r.renamedModels() + r.renamedTextures() + r.renamedSounds() + r.renamedCubes()), Theme.OK);
        tileWarnings.set(String.valueOf(r.warnings().size()), r.warnings().isEmpty() ? Theme.OK : Theme.WARN);
        status.setText("Done in " + r.durationMillis() + " ms. Saved " + reduced + "% of the size.");
        for (String w : r.warnings()) {
            appendLog("warning: " + w);
        }
        updateRunState();
    }

    private void fail(String message) {
        appendLog("ERROR: " + message);
        status.setText("Failed. See the activity log.");
        tileWarnings.set("!", Theme.ERROR);
        JOptionPane.showMessageDialog(this, message, "Obfuscation failed", JOptionPane.ERROR_MESSAGE);
        updateRunState();
    }

    private void openOutputFolder() {
        if (lastOutput == null) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(lastOutput.getParent().toFile());
            }
        } catch (IOException | UnsupportedOperationException e) {
            appendLog("Could not open the folder: " + e.getMessage());
        }
    }

    private void appendLog(String line) {
        log.append(line + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }

    // ------------------------------------------------------------------ prefs

    private void loadPrefs() {
        try {
            String in = PREFS.get(PREF_INPUT, "");
            if (!in.isBlank() && (Files.isDirectory(Path.of(in)) || Files.isRegularFile(Path.of(in)))) {
                setInput(Path.of(in));
            }
            String out = PREFS.get(PREF_OUTPUT, "");
            if (!out.isBlank()) {
                outputField.setText(out);
            }
            seedField.setText(PREFS.get(PREF_SEED, ""));
            descriptionField.setText(PREFS.get(PREF_DESC, ""));
            protectedArea.setText(PREFS.get(PREF_PROTECTED, String.join("\n", PackOptions.DEFAULT_PROTECTED)));
        } catch (Exception ignored) {
            // Preferences are a convenience. Start with defaults if they cannot be read.
            protectedArea.setText(String.join("\n", PackOptions.DEFAULT_PROTECTED));
        }
        PackOptions.Preset preset = PackOptions.Preset.STANDARD;
        try {
            preset = PackOptions.Preset.fromName(PREFS.get(PREF_PRESET, "standard"));
        } catch (IllegalArgumentException ignored) {
            // Keep Standard if the stored value is unknown.
        }
        applyPreset(preset);
    }

    private static String[] presetLabels() {
        List<String> labels = new ArrayList<>();
        for (PackOptions.Preset p : PackOptions.Preset.values()) {
            labels.add(p.label());
        }
        labels.add(CUSTOM);
        return labels.toArray(String[]::new);
    }

    private List<JCheckBox> allCheckBoxes() {
        return List.of(renameModels, renameTextures, renameSounds, renameNamespaces, renameCubes,
                removeGroups, minifyJson, stripJunk, splitTextures, stripMetadata, writeMapping);
    }

    /** Sets every option from a preset. Changing an option by hand afterwards switches the preset to Custom. */
    private void applyPreset(PackOptions.Preset preset) {
        PackOptions o = PackOptions.forPreset(preset);
        applyingPreset = true;
        try {
            renameModels.setSelected(o.renameModels());
            renameTextures.setSelected(o.renameTextures());
            renameSounds.setSelected(o.renameSounds());
            renameNamespaces.setSelected(o.renameNamespaces());
            renameCubes.setSelected(o.renameCubes());
            removeGroups.setSelected(o.removeGroups());
            minifyJson.setSelected(o.minifyJson());
            stripJunk.setSelected(o.stripJunk());
            stripMetadata.setSelected(o.stripMetadata());
            splitTextures.setSelected(o.splitTextures());
            writeMapping.setSelected(o.writeMapping());
            junkModels.setValue(o.junk().models());
            junkTextures.setValue(o.junk().textures());
            junkSounds.setValue(o.junk().sounds());
            presetBox.setSelectedItem(preset.label());
        } finally {
            applyingPreset = false;
        }
    }

    private void markCustom() {
        if (applyingPreset) {
            return;
        }
        applyingPreset = true;
        try {
            presetBox.setSelectedItem(CUSTOM);
        } finally {
            applyingPreset = false;
        }
    }

    private PackOptions currentOptions(Long seed, PackOptions.Junk junk) {
        return new PackOptions.Builder()
                .renameModels(renameModels.isSelected())
                .renameTextures(renameTextures.isSelected())
                .renameSounds(renameSounds.isSelected())
                .renameNamespaces(renameNamespaces.isSelected())
                .renameCubes(renameCubes.isSelected())
                .removeGroups(removeGroups.isSelected())
                .minifyJson(minifyJson.isSelected())
                .stripJunk(stripJunk.isSelected())
                .splitTextures(splitTextures.isSelected())
                .stripMetadata(stripMetadata.isSelected())
                .writeMapping(writeMapping.isSelected())
                .seed(seed)
                .protectedPrefixes(protectedList())
                .descriptionOverride(descriptionField.getText())
                .junk(junk)
                .build();
    }

    /** Runs every pack in a folder with the current options. Outputs go to a folder called "obfuscated" inside it. */
    private void batchFolder() {
        JFileChooser fc = new JFileChooser(input == null ? null : input.getParent().toFile());
        fc.setDialogTitle("Choose a folder that contains packs");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path dir = fc.getSelectedFile().toPath();
        Long seed = null;
        String seedText = seedField.getText().trim();
        if (!seedText.isEmpty()) {
            try {
                seed = Long.parseLong(seedText);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "The seed must be a whole number, or blank for random names.",
                        "Seed", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        PackOptions opts = currentOptions(seed, junkSettings());
        setBusy(true);
        appendLog("");
        appendLog("== Batch " + dir.getFileName());
        worker = new SwingWorker<List<PackBatch.Item>, String>() {
            @Override
            protected List<PackBatch.Item> doInBackground() throws IOException {
                return PackBatch.run(dir, dir.resolve("obfuscated"), opts, msg -> publish(msg));
            }

            @Override
            protected void process(List<String> chunks) {
                for (String s : chunks) {
                    appendLog(s);
                }
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    List<PackBatch.Item> items = get();
                    long failed = items.stream().filter(i -> i.error() != null).count();
                    status.setText("Batch done: " + (items.size() - failed) + " succeeded, " + failed + " failed.");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    fail(cause.getMessage() == null ? cause.toString() : cause.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    fail("Interrupted.");
                }
            }
        };
        worker.execute();
    }

    private void savePrefs() {
        try {
            PREFS.put(PREF_INPUT, input == null ? "" : input.toString());
            PREFS.put(PREF_OUTPUT, outputField.getText());
            PREFS.put(PREF_SEED, seedField.getText());
            PREFS.put(PREF_DESC, descriptionField.getText());
            PREFS.put(PREF_PROTECTED, protectedArea.getText());
            PREFS.put(PREF_PRESET, String.valueOf(presetBox.getSelectedItem()).toLowerCase());
            PREFS.flush();
        } catch (Exception ignored) {
            // Not fatal.
        }
    }

    private static String versionText() {
        String v = MainWindow.class.getPackage() == null ? null : MainWindow.class.getPackage().getImplementationVersion();
        return v == null ? "1.0.0" : v;
    }
}
