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

/** Smoke funcional de la pantalla de Attendance para el entorno de QA. */
class AttendanceFunctionalSeleniumIT {

    private static WebDriver driver;
    private static WebDriverWait wait;

    @BeforeAll
    static void startBrowser() {
        ChromeOptions options = new ChromeOptions();
        if (Boolean.parseBoolean(System.getProperty("selenium.headless", "true"))) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--window-size=1440,1000");
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
    void attendanceScreenLoads() {
        driver.get(frontendUrl() + "/attendance");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
        assertTrue(driver.getPageSource().contains("Asistencia"),
                "La pantalla de asistencia no se cargo correctamente");
    }

    @Test
    void attendanceScreenExposesClassAndStudentWorkflow() {
        driver.get(frontendUrl() + "/attendance");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
        String page = driver.getPageSource();
        assertTrue(page.contains("Grado") || page.contains("Sección"),
                "No se muestran los filtros academicos de Attendance");
        assertTrue(page.contains("Buscar estudiante") || page.contains("estudiante"),
                "No se muestra el flujo de estudiantes de Attendance");
    }

    @Test
    void massAttendanceActionStartsTheSaveWorkflow() {
        driver.get(frontendUrl() + "/attendance");
        var markAll = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("button.mark-all-button")));
        markAll.click();

        boolean savingOrFinished = wait.until(d -> {
            String text = d.findElement(By.tagName("body")).getText();
            return text.contains("Guardando asistencias")
                    || text.contains("Asistencias guardadas")
                    || text.contains("asistencia guardada");
        });
        assertTrue(savingOrFinished, "La accion masiva no inicio ni confirmo el guardado");
    }

    private static String frontendUrl() {
        return System.getProperty("frontend.url", "http://localhost:4200");
    }
}
