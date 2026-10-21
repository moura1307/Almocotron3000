package org.almocatron;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Properties;

public class Main {

    private static final String FORM_URL =
            "https://docs.google.com/forms/d/e/1FAIpQLScdfRBzEQvFapZUvI8cvnC8p_vAT5OU6tZDOBkQXFk_3dAXQw/viewform";

    private static final String USER_HOME = System.getProperty("user.home");
    private static final Path BASE_DIR = Paths.get(USER_HOME, "MyBotProfiles");
    private static final Path DATA_FILE = BASE_DIR.resolve("dados.properties");

    record UserData(String matricula, String nome, String curso) {}

    public static void main(String[] args) throws IOException {
        // 1. Dados do usuário (pergunta só na primeira vez)
        UserData data = getUserData();
        if (data == null) { return; }

        // 2. Navegador
        String choice = chooseBrowser();
        if (choice == null) {
            return; // usuário cancelou
        }

        // 3. Perfil e marcador de login (checado ANTES de abrir o driver)
        Path profileDir = BASE_DIR.resolve(choice);
        Files.createDirectories(profileDir);
        Path loginMarker = profileDir.resolve(".bot-logged-in");
        boolean firstRun = !Files.exists(loginMarker);

        WebDriver driver = selectDriver(choice, profileDir);
        try {
            if (firstRun) {
                driver.get("https://accounts.google.com");
                JOptionPane.showMessageDialog(null,
                        "Faça login na conta Google e clique em OK quando terminar.");
                Files.createFile(loginMarker);
            }

            fillForm(driver, data);

        } finally {
            driver.quit();
        }
    }

    private static void fillForm(WebDriver driver, UserData data) {
        String url = FORM_URL
                + "?usp=pp_url"
                + "&entry.661216181=" + URLEncoder.encode(data.matricula(), StandardCharsets.UTF_8)
                + "&entry.1948897288=" + URLEncoder.encode(data.nome(), StandardCharsets.UTF_8)
                + "&entry.1756620298=" + URLEncoder.encode(data.curso(), StandardCharsets.UTF_8);
        driver.get(url);
        dismissDraftDialog(driver);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        WebElement emailCheckbox = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//div[@role='checkbox']")));
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block: 'center'});", emailCheckbox);

        if (!"true".equals(emailCheckbox.getAttribute("aria-checked"))) {
            try {
                emailCheckbox.click();
            } catch (ElementClickInterceptedException e) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", emailCheckbox);
            }
        }

        wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//div[@role='button'][@aria-label='Enviar']"))).click();
    }

    private static WebDriver selectDriver(String choice, Path profileDir) {
        String path = profileDir.toAbsolutePath().toString();

        return switch (choice) {
            case "Chrome" -> {
                ChromeOptions options = new ChromeOptions();
                options.addArguments("--user-data-dir=" + path);
                yield new ChromeDriver(options);
            }
            case "Firefox" -> {
                FirefoxOptions options = new FirefoxOptions();
                options.addArguments("-profile", path);
                yield new FirefoxDriver(options);
            }
            case "Edge" -> {
                EdgeOptions options = new EdgeOptions();
                options.addArguments("--user-data-dir=" + path);
                yield new EdgeDriver(options);
            }
            default -> throw new IllegalArgumentException("Unsupported browser: " + choice);
        };
    }

    private static String chooseBrowser() {
        String[] browsers = {"Chrome", "Firefox", "Edge"};
        return (String) JOptionPane.showInputDialog(
                null,
                "Qual navegador deseja usar?",
                "ALMOCOTRON 3000",
                JOptionPane.QUESTION_MESSAGE,
                null,
                browsers,
                browsers[0]);
    }

    private static UserData getUserData() throws IOException {
        UserData data = loadUserData();
        if (data == null) {
            data = askUserData();
            if (data != null) {
                saveUserData(data);
            }
        }
        return data;
    }

    private static UserData askUserData() {
        String[] cursos = {
                "Edificações", "Informática", "Mineração", "Petróleo e Gás",
                "Química", "Engenharia Civil", "Engenharia da Computação", "Telemática"
        };

        JTextField matriculaField = new JTextField(20);
        JTextField nomeField = new JTextField(20);
        JComboBox<String> cursoBox = new JComboBox<>(cursos);

        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
        panel.add(new JLabel("Matrícula:"));
        panel.add(matriculaField);
        panel.add(new JLabel("Nome:"));
        panel.add(nomeField);
        panel.add(new JLabel("Curso:"));
        panel.add(cursoBox);

        while (true) {
            int result = JOptionPane.showConfirmDialog(
                    null, panel, "ALMOCOTRON 3000 - Seus dados",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

            if (result != JOptionPane.OK_OPTION) {
                return null;
            }

            String matricula = matriculaField.getText().trim();
            String nome = nomeField.getText().trim();

            if (matricula.isEmpty() || nome.isEmpty()) {
                JOptionPane.showMessageDialog(null,
                        "Preencha matrícula e nome.", "Campos obrigatórios",
                        JOptionPane.WARNING_MESSAGE);
                continue;
            }

            return new UserData(matricula, nome, (String) cursoBox.getSelectedItem());
        }
    }

    private static UserData loadUserData() {
        if (!Files.exists(DATA_FILE)) {
            return null;
        }
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(DATA_FILE, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            return null; // arquivo ilegível: pede os dados de novo
        }

        String matricula = props.getProperty("matricula");
        String nome = props.getProperty("nome");
        String curso = props.getProperty("curso");
        if (matricula == null || nome == null || curso == null) {
            return null;
        }
        return new UserData(matricula, nome, curso);
    }

    private static void saveUserData(UserData data) throws IOException {
        Files.createDirectories(DATA_FILE.getParent());
        Properties props = new Properties();
        props.setProperty("matricula", data.matricula());
        props.setProperty("nome", data.nome());
        props.setProperty("curso", data.curso());
        try (Writer writer = Files.newBufferedWriter(DATA_FILE, StandardCharsets.UTF_8)) {
            props.store(writer, "Dados do usuario");
        }
    }

    private static void dismissDraftDialog(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
        By continuar = By.xpath("//a[.//span[normalize-space()='Continuar']]");

        try {
            WebElement btn = wait.until(ExpectedConditions.presenceOfElementLocated(continuar));
            System.out.println("Diálogo de rascunho encontrado.");

            String href = btn.getAttribute("href");
            try {
                wait.until(ExpectedConditions.elementToBeClickable(btn)).click();
            } catch (WebDriverException e) {
                System.out.println("Clique falhou, navegando pelo href: " + e.getMessage());
                driver.get(href);
            }

            try {
                new WebDriverWait(driver, Duration.ofSeconds(3))
                        .until(ExpectedConditions.invisibilityOfElementLocated(continuar));
            } catch (TimeoutException e) {
                System.out.println("Diálogo ainda visível, navegando pelo href.");
                driver.get(href);
            }
        } catch (TimeoutException e) {
            System.out.println("Nenhum diálogo de rascunho apareceu em 10s.");
        }
    }
}