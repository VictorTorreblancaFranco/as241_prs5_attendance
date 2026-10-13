package com.vg.attendance.functional;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/** Pruebas funcionales reproducibles del acceso al modulo de Attendance. */
class AttendanceFunctionalSeleniumIT {

    private static WebDriver driver;
    private static WebDriverWait wait;

    @BeforeAll
    static void startBrowser() {
        ChromeOptions options = new ChromeOptions();
        if (Boolean.parseBoolean(System.getProperty("selenium.headless", "true"))) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu",
                "--no-zygote", "--window-size=1440,1000");
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));
    }

    @AfterAll
    static void stopBrowser() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    void loginScreenLoadsWithRequiredFields() {
        driver.get(frontendUrl() + "/auth/login");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("email")));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("contrasena")));
        assertTrue(driver.findElement(By.tagName("body")).getText().contains("Bienvenido de vuelta")
                        && driver.findElement(By.id("email")).isDisplayed()
                        && driver.findElement(By.id("contrasena")).isDisplayed(),
                "El formulario de inicio de sesion no se cargo correctamente");
    }

    @Test
    void attendanceRouteRequiresAuthentication() {
        driver.get(frontendUrl() + "/attendance");
        wait.until(d -> d.getCurrentUrl().contains("/auth/login"));
        assertTrue(driver.getCurrentUrl().contains("/auth/login"),
                "La ruta de Attendance debe exigir autenticacion");
    }

    private static String frontendUrl() {
        return System.getProperty("frontend.url", "http://localhost:4200");
    }
}
