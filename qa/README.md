# PSW - Evidencias de `vg-ms-attendance`

## Pipeline

El `Jenkinsfile` ejecuta compilacion, pruebas unitarias, Jacoco, SonarQube,
Selenium, JMeter y Slack. Las etapas externas se activan con parametros para
que una ejecucion local no finja resultados:

```text
RUN_SONAR=true
RUN_E2E=true
RUN_JMETER=true
FRONTEND_URL=http://localhost:4200
ATTENDANCE_URL=http://localhost:5085
```

Credenciales necesarias en Jenkins:

- `sonarcloud-attendance-token`: token de SonarCloud.
- Configuracion global SonarQube llamada `SonarCloud`.
- Integracion Slack habilitada para `slackSend`.

## Selenium

Los dos casos de `AttendanceFunctionalTest` comprueban que la pantalla cargue
y que existan los filtros academicos y el flujo de estudiantes. El navegador
debe abrir una sesion autenticada en el entorno de QA; el test no reutiliza la
sesion personal del desarrollador.

Ejecucion local:

```bash
./mvnw -Pfunctional \
  -Dfrontend.url=http://localhost:4200 \
  -Dselenium.headless=true test
```

## JMeter

Los escenarios se ejecutan en modo no grafico y generan un reporte HTML:

| Escenario | Usuarios | Duracion | Objetivo |
|---|---:|---:|---|
| Verde | 5 | 60 s | Operacion normal |
| Amarillo | 25 | 180 s | Carga media |
| Rojo | 75 | 300 s | Saturacion controlada |

Los planes incluidos consultan `/api/attendance/date` para medir una operación
real de lectura sin escribir asistencias. En el entorno con gateway se debe
usar la URL del gateway y agregar el token/tenant de QA mediante un Header
Manager del plan; nunca se deben ejecutar contra produccion.

Resultados que deben documentarse: promedio, p90, p95, throughput y porcentaje
de errores. No se debe ejecutar el escenario rojo contra produccion.
