package com.minlish.desktop;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JWindow;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class VocabularyDesktopPlayerApp {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final String DEFAULT_BASE_URL = System.getenv().getOrDefault("MINLISH_API_BASE_URL", "https://api.minlish.site");
    private static final String DEFAULT_EMAIL = System.getenv().getOrDefault("MINLISH_EMAIL", "phamthanhtuan.developer@gmail.com");
    private static final String DEFAULT_SET_ID = System.getenv().getOrDefault("MINLISH_SET_ID", "");
    private static final String DEFAULT_PASSWORD = System.getenv().getOrDefault("MINLISH_PASSWORD", "123456");
    private static final long GOOGLE_CALLBACK_WAIT_SECONDS = 180;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new VocabularyDesktopPlayerApp().start());
    }

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();
    private final ExecutorService speechExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "minlish-speech");
        thread.setDaemon(true);
        return thread;
    });

    private AppWindow window;

    private void start() {
        AppConfig config = showConfigDialog();
        if (config == null) {
            return;
        }

        new Thread(() -> {
            try {
                LoadSession session = loadSession(config);
                SwingUtilities.invokeLater(() -> {
                    window = new AppWindow();
                    window.setCards(session.cards());
                    window.showOverlay();
                });
            } catch (RuntimeException ex) {
                // In lỗi chi tiết ra console để debug
                ex.printStackTrace();
                // Hiển thị thông báo lỗi cụ thể hơn cho người dùng
                SwingUtilities.invokeLater(() -> showError("Lỗi khi tải dữ liệu", ex.getMessage()));
            }
        }, "minlish-loader").start();
    }

    private AppConfig showConfigDialog() {
        JTextField baseUrlField = new JTextField(DEFAULT_BASE_URL, 28);
        JTextField emailField = new JTextField(DEFAULT_EMAIL, 28);
        JPasswordField passwordField = new JPasswordField(DEFAULT_PASSWORD, 28);
        JTextField setIdField = new JTextField(DEFAULT_SET_ID, 28);

        JRadioButton googleAuthRadio = new JRadioButton("Đăng nhập Google");
        JRadioButton passwordAuthRadio = new JRadioButton("Đăng nhập Email/Password", true);
        ButtonGroup authGroup = new ButtonGroup();
        authGroup.add(googleAuthRadio);
        authGroup.add(passwordAuthRadio);

        JPanel authPanel = new JPanel();
        authPanel.setLayout(new BoxLayout(authPanel, BoxLayout.X_AXIS));
        authPanel.add(googleAuthRadio);
        authPanel.add(Box.createHorizontalStrut(10));
        authPanel.add(passwordAuthRadio);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(4, 0, 4, 10);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.weightx = 1.0;
        fieldConstraints.insets = new Insets(4, 0, 4, 0);

        addConfigRow(panel, labelConstraints, fieldConstraints, 0, "API Base URL", baseUrlField);
        addConfigRow(panel, labelConstraints, fieldConstraints, 1, "Email", emailField);
        addConfigRow(panel, labelConstraints, fieldConstraints, 2, "Password", passwordField);
        addConfigRow(panel, labelConstraints, fieldConstraints, 3, "Set ID", setIdField);

        GridBagConstraints authLabelConstraints = (GridBagConstraints) labelConstraints.clone();
        authLabelConstraints.gridy = 4;
        authLabelConstraints.insets = new Insets(12, 0, 4, 10);
        panel.add(new JLabel("Phương thức:"), authLabelConstraints);

        GridBagConstraints authFieldConstraints = (GridBagConstraints) fieldConstraints.clone();
        authFieldConstraints.gridy = 4;
        authFieldConstraints.insets = new Insets(12, 0, 4, 0);
        panel.add(authPanel, authFieldConstraints);

        int result = JOptionPane.showConfirmDialog(
                null,
                panel,
                "MinLish Desktop Player",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        String baseUrl = normalizeBaseUrl(baseUrlField.getText());
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String password = new String(passwordField.getPassword());
        String setIdText = setIdField.getText() == null ? "" : setIdField.getText().trim();

        if (baseUrl.isBlank() || setIdText.isBlank()) {
            showError("Thieu thong tin", "Ban can nhap API Base URL va Set ID.");
            return null;
        }

        long setId;
        try {
            setId = Long.parseLong(setIdText);
        } catch (NumberFormatException ex) {
            showError("Set ID khong hop le", "Set ID phai la so.");
            return null;
        }

        AuthMode authMode = googleAuthRadio.isSelected() ? AuthMode.GOOGLE : AuthMode.PASSWORD;

        if (authMode == AuthMode.PASSWORD && (email.isBlank() || password.isBlank())) {
            showError("Thieu thong tin", "Dang nhap Email/Password can dien day du email va password.");
            return null;
        }

        return new AppConfig(baseUrl, email, password, setId, authMode);
    }

    private void addConfigRow(JPanel panel, GridBagConstraints labelConstraints, GridBagConstraints fieldConstraints, int row, String label, Component field) {
        GridBagConstraints rowLabel = (GridBagConstraints) labelConstraints.clone();
        rowLabel.gridy = row;
        panel.add(new JLabel(label + ":"), rowLabel);

        GridBagConstraints rowField = (GridBagConstraints) fieldConstraints.clone();
        rowField.gridy = row;
        panel.add(field, rowField);
    }

    private LoadSession loadSession(AppConfig config) {
        String token = config.authMode() == AuthMode.GOOGLE
                ? loginWithGoogleDesktopFlow(config)
                : login(config);
        List<VocabularyCard> cards = loadVocabularyCards(config, token);
        if (cards.isEmpty()) {
            throw new IllegalStateException("Bo tu nay chua co du lieu de hien thi.");
        }
        return new LoadSession(token, cards);
    }

    private String loginWithGoogleDesktopFlow(AppConfig config) {
        HttpServer callbackServer = null;
        try {
            callbackServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            GoogleCallbackResult callbackResult = new GoogleCallbackResult();
            callbackServer.createContext("/oauth-callback", exchange -> handleGoogleCallback(exchange, callbackResult));
            callbackServer.start();

            int port = callbackServer.getAddress().getPort();
            String redirectUri = "http://127.0.0.1:" + port + "/oauth-callback";
            String startUrl = config.baseUrl() + "/api/auth/google/desktop/start?redirectUri="
                    + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);

            openBrowser(startUrl);

            boolean completed = callbackResult.await(GOOGLE_CALLBACK_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                throw new IllegalStateException("Dang nhap Google bi timeout. Vui long thu lai.");
            }
            if (callbackResult.error != null && !callbackResult.error.isBlank()) {
                throw new IllegalStateException("Dang nhap Google that bai: " + callbackResult.error);
            }
            if (callbackResult.accessToken == null || callbackResult.accessToken.isBlank()) {
                throw new IllegalStateException("Google login thanh cong nhung khong nhan duoc access token.");
            }
            return callbackResult.accessToken;
        } catch (IOException ex) {
            throw new IllegalStateException("Khong the khoi tao callback server cho Google login: " + ex.getMessage(), ex);
        } finally {
            if (callbackServer != null) {
                callbackServer.stop(0);
            }
        }
    }

    private void handleGoogleCallback(HttpExchange exchange, GoogleCallbackResult callbackResult) throws IOException {
        try {
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            callbackResult.accessToken = query.get("accessToken");
            callbackResult.error = query.get("error");
            callbackResult.countDown();

            String body = "<html><body style='font-family:Segoe UI,sans-serif;'>"
                    + "<h3>Dang nhap thanh cong</h3>"
                    + "Ban co the dong tab nay va quay lai app MinLish Desktop."
                    + "</body></html>";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        } finally {
            exchange.close();
        }
    }

    private void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (IOException | URISyntaxException ignored) {
        }
        throw new IllegalStateException("Khong mo duoc trinh duyet. Hay mo URL nay thu cong: " + url);
    }

    private Map<String, String> parseQuery(URI uri) {
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new HashMap<>();
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            int index = pair.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, index), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }

    private String login(AppConfig config) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl() + "/api/auth/login"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(new LoginPayload(config.email(), config.password())), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Dang nhap that bai: HTTP " + response.statusCode() + " - " + response.body());
            }

            JsonNode json = MAPPER.readTree(response.body());
            String token = firstNonBlank(json.path("accessToken").asText(null), json.path("token").asText(null));
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("API khong tra ve JWT accessToken.");
            }
            return token;
        } catch (IOException ex) {
            throw new IllegalStateException("Loi doc du lieu dang nhap: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bi ngan khi dang nhap.", ex);
        }
    }

    private List<VocabularyCard> loadVocabularyCards(AppConfig config, String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl() + "/api/vocabularies/set/" + config.setId()))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Khong tai duoc danh sach tu vung: HTTP " + response.statusCode() + " - " + response.body());
            }

            VocabularyCard[] cards = MAPPER.readValue(response.body(), VocabularyCard[].class);
            List<VocabularyCard> result = new ArrayList<>();
            Collections.addAll(result, cards);
            return result;
        } catch (IOException ex) {
            throw new IllegalStateException("Loi doc du lieu tu vung: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bi ngan khi tai tu vung.", ex);
        }
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private String unwrapMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.toString() : current.getMessage();
    }

    private String normalizeBaseUrl(String rawBaseUrl) {
        String value = rawBaseUrl == null ? "" : rawBaseUrl.trim();
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private record AppConfig(String baseUrl, String email, String password, long setId, AuthMode authMode) {
    }

    private enum AuthMode {
        GOOGLE,
        PASSWORD
    }

    private record LoginPayload(String email, String password) {
    }

    private record LoadSession(String token, List<VocabularyCard> cards) {
    }

    private static final class GoogleCallbackResult {
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile String accessToken;
        private volatile String error;

        private void countDown() {
            latch.countDown();
        }

        private boolean await(long timeout, TimeUnit unit) {
            try {
                return latch.await(timeout, unit);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class VocabularyCard {
        public Long id;
        public String word;
        public String pronunciation;
        public String meaning;
        public String type;
        public String level;
    }

    private final class AppWindow extends JWindow {

        private static final int MARGIN = 0;

        private final JLabel wordLabel = new JLabel("Word");
        private final JLabel pronunciationLabel = new JLabel("Pronunciation");
        private final JLabel typeLabel = new JLabel("Type");
        private final JLabel meaningLabel = new JLabel("Meaning");
        private final JButton previousButton = createButton("<");
        private final JButton speakButton = createButton("SPEAK");
        private final JButton nextButton = createButton(">");
        private final JButton closeButton = createButton("X");

        private List<VocabularyCard> cards = Collections.emptyList();
        private int currentIndex = 0;
        private final WindowsAppBarHelper appBarHelper = new WindowsAppBarHelper();

        private AppWindow() {
            setBackground(new Color(0, 0, 0, 0));
            
            setAlwaysOnTop(true);
            setFocusableWindowState(false);

            JPanel card = new RoundedCardPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.X_AXIS));
             card.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

            styleLabel(wordLabel, new Font("SansSerif", Font.BOLD, 19), Color.WHITE, 100);
            styleLabel(pronunciationLabel, new Font("SansSerif", Font.ITALIC, 14), new Color(200, 200, 200), 80);
            styleLabel(typeLabel, new Font("SansSerif", Font.PLAIN, 13), new Color(183, 210, 255), 30);
            styleLabel(meaningLabel, new Font("SansSerif", Font.PLAIN, 14), new Color(240, 240, 240), 150);

            previousButton.addActionListener(e -> showPrevious());
            speakButton.addActionListener(e -> speakCurrentWord());
            nextButton.addActionListener(e -> showNext());
            closeButton.addActionListener(e -> {
            appBarHelper.unregisterAppBar();
            System.exit(0);
        });

            card.add(previousButton);
            card.add(Box.createHorizontalStrut(8));
            card.add(speakButton);
            card.add(Box.createHorizontalStrut(12));
            card.add(wordLabel);
            card.add(Box.createHorizontalStrut(12));
            card.add(pronunciationLabel);
            card.add(Box.createHorizontalStrut(8));
            card.add(typeLabel);
            card.add(Box.createHorizontalStrut(12));
            card.add(meaningLabel);
            card.add(Box.createHorizontalStrut(12));
            card.add(nextButton);
            card.add(Box.createHorizontalStrut(8));
            card.add(closeButton);

            setContentPane(card);
            pack();
        }

        private void setCards(List<VocabularyCard> cards) {
            this.cards = cards == null ? Collections.emptyList() : List.copyOf(cards);
            this.currentIndex = 0;
            refreshCard();
        }

        private void showOverlay() {
            refreshCard();
            repositionWindow();
            setVisible(true); // Bắt buộc phải setVisible trước để hệ điều hành cấp phát HWND
            
            // Yêu cầu Windows biến cửa sổ này thành AppBar
            appBarHelper.registerAppBar(this);
        }

        private void refreshCard() {
            VocabularyCard card = currentCard();
            if (card == null) {
                wordLabel.setText("No data");
                pronunciationLabel.setText("");
                typeLabel.setText("");
                meaningLabel.setText("Chua co tu vung nao trong set nay.");
                previousButton.setEnabled(false);
                nextButton.setEnabled(false);
                speakButton.setEnabled(false);
                return;
            }

            wordLabel.setText(textOrDash(card.word));
            pronunciationLabel.setText(textOrDash(card.pronunciation));
            typeLabel.setText(card.type == null || card.type.isBlank() ? "" : "(" + card.type.trim() + ")");
            meaningLabel.setText(toHtml(textOrDash(card.meaning), 260));

            previousButton.setEnabled(cards.size() > 1);
            nextButton.setEnabled(cards.size() > 1);
            speakButton.setEnabled(true);

            pack();
            repositionWindow();
        }

        private VocabularyCard currentCard() {
            if (cards.isEmpty()) {
                return null;
            }
            if (currentIndex < 0) {
                currentIndex = cards.size() - 1;
            } else if (currentIndex >= cards.size()) {
                currentIndex = 0;
            }
            return cards.get(currentIndex);
        }

        private void showPrevious() {
            if (cards.isEmpty()) {
                return;
            }
            currentIndex = (currentIndex - 1 + cards.size()) % cards.size();
            refreshCard();
        }

        private void showNext() {
            if (cards.isEmpty()) {
                return;
            }
            currentIndex = (currentIndex + 1) % cards.size();
            refreshCard();
        }

        private void speakCurrentWord() {
            VocabularyCard card = currentCard();
            if (card == null || card.word == null || card.word.isBlank()) {
                return;
            }

            String word = card.word.trim();
            speechExecutor.submit(() -> {
                try {
                    speakWithWindowsSpeech(word);
                } catch (Exception ex) {
                    Toolkit.getDefaultToolkit().beep();
                }
            });
        }

        private void speakWithWindowsSpeech(String word) throws IOException, InterruptedException {
            String safeWord = word.replace("'", "''");
            String script = "Add-Type -AssemblyName System.Speech; "
                    + "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
                    + "$s.Rate = 0; $s.Volume = 100; $s.Speak('" + safeWord + "')";

            Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", script)
                    .redirectErrorStream(true)
                    .start();
            process.waitFor();
        }

        private void repositionWindow() {
            GraphicsConfiguration configuration = getGraphicsConfiguration();
            if (configuration == null) {
                configuration = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
            }

            Rectangle screenBounds = configuration.getBounds();
            Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
            
            // Position the window as a full-width bar just above the taskbar.
            int width = screenBounds.width;
            int height = getHeight(); // Height is determined by pack()
            int x = screenBounds.x;
            int y = screenBounds.y + screenBounds.height - screenInsets.bottom - height;
            
            setBounds(x, y, width, height);
        }

        private JButton createButton(String text) {
            JButton button = new JButton(text);
            button.setFocusPainted(false);
            button.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            button.setBackground(new Color(255, 255, 255, 24));
            button.setForeground(Color.WHITE);
            button.setOpaque(true);
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return button;
        }

        private void styleLabel(JLabel label, Font font, Color color, int width) {
            label.setFont(font);
            label.setForeground(color);
            label.setOpaque(false);
            label.setVerticalAlignment(SwingConstants.CENTER);
            label.setMaximumSize(new Dimension(width, Integer.MAX_VALUE));
            label.setPreferredSize(new Dimension(width, 28));
        }

        private String textOrDash(String text) {
            return text == null || text.isBlank() ? "-" : text.trim();
        }

        private String toHtml(String text, int width) {
            return "<html><body style='width:" + width + "px'>" + escapeHtml(text) + "</body></html>";
        }

        private String escapeHtml(String text) {
            return text
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
        }
    }

    private static final class RoundedCardPanel extends JPanel {
        private RoundedCardPanel() {
            setOpaque(false);
            setBackground(new Color(20, 26, 38, 220));
            setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 28), 1));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics2D.setColor(getBackground());
                graphics2D.fillRect(0, 0, getWidth(), getHeight());
            } finally {
                graphics2D.dispose();
            }
            super.paintComponent(graphics);
        }
    }
}