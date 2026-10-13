# Guia de entrega PSW - Attendance

Esta guia corresponde al microservicio `vg-ms-attendance`. El pipeline no usa
produccion: las pruebas deben apuntar a un entorno local o de QA.

## Estado actual

- [x] Compilacion Maven en `Jenkinsfile`.
- [x] Pruebas unitarias y reporte JUnit.
- [x] Cobertura JaCoCo (`target/site/jacoco/jacoco.xml`).
- [x] Analisis SonarQube/SonarCloud preparado.
- [x] Tres escenarios JMeter: verde, amarillo y rojo.
- [x] Tres pruebas funcionales Selenium preparadas.
- [x] Notificacion Slack preparada en el `post` del pipeline.
- [ ] Ejecutar el pipeline en Jenkins con las credenciales reales.
- [ ] Adjuntar capturas de Jenkins, SonarQube, JMeter y Slack.
- [ ] Grabar el video de 8 a 10 minutos.

## Configuracion unica de Jenkins

Instalar estos plugins: **SonarQube Scanner**, **Slack Notification** y
**JaCoCo**. Los plugins de Pipeline, Git y JUnit forman parte de la
instalacion sugerida.

Crear en Jenkins:

1. Una credencial **Secret text** con ID `sonarcloud-attendance-token`.
2. En *Manage Jenkins > System*, un servidor SonarQube llamado `SonarCloud`
   con URL `https://sonarcloud.io` y la credencial anterior.
3. La integracion de Slack para el canal `#ci-attendance`.
4. Un job Pipeline desde SCM apuntando a la rama `develop` del repositorio de
   `vg-ms-attendance` y usando el `Jenkinsfile` del proyecto.

Los tokens nunca se guardan en Git ni en este archivo.

## Ejecucion recomendada

Primera ejecucion, solo compilacion y pruebas:

```text
RUN_SONAR=false
RUN_E2E=false
RUN_JMETER=false
```

Ejecucion completa en QA:

```text
RUN_SONAR=true
RUN_E2E=true
RUN_JMETER=true
FRONTEND_URL=http://localhost:4200
ATTENDANCE_URL=http://localhost:5085
JMETER_DATE=2026-10-12
```

Para Selenium deben estar levantados frontend y backend, y el entorno de
prueba debe permitir una sesion autenticada. Para JMeter debe estar instalado
`jmeter` en el agente Jenkins.

## Evidencias que se deben capturar

### Slack y Jenkins

Capturar el build con las etapas verdes y el mensaje `pipeline EXITOSO` en
Slack. Si falla una etapa, corregirla antes de tomar la captura final.

### SonarQube

Capturar el proyecto `vg-ms-attendance` mostrando Quality Gate, bugs,
vulnerabilidades, code smells y cobertura. En el informe explicar cada valor
con una frase y registrar cualquier deuda tecnica.

### JMeter

Para cada escenario registrar usuarios, duracion, promedio, p90, p95,
throughput y porcentaje de errores. No ejecutar el escenario rojo contra
produccion.

| Escenario | Usuarios | Duracion | Uso |
|---|---:|---:|---|
| Verde | 5 | 60 s | Operacion normal |
| Amarillo | 25 | 180 s | Carga media |
| Rojo | 75 | 300 s | Saturacion controlada |

### Selenium

Documentar dos casos como minimo con funcionalidad, pasos, resultado esperado,
resultado obtenido y captura. Los casos preparados verifican la carga de
Attendance, los filtros/clase/estudiantes y el flujo de registro masivo.

## Guion breve para el video

1. Presentar el microservicio y el objetivo del pipeline.
2. Mostrar el `Jenkinsfile` y ejecutar el build.
3. Mostrar pruebas unitarias, SonarQube y Quality Gate.
4. Mostrar los dos casos Selenium.
5. Mostrar los tres reportes JMeter y explicar verde, amarillo y rojo.
6. Mostrar la notificacion de Slack y cerrar con las conclusiones.

