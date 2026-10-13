# Informe PSW: Pipeline CI/CD de Attendance

## 1. Objetivo

Se implemento un pipeline automatizado en Jenkins para el microservicio Java
`vg-ms-attendance`. El flujo compila el proyecto, ejecuta pruebas unitarias,
calcula cobertura JaCoCo, analiza calidad con SonarCloud, ejecuta dos pruebas
funcionales Selenium, ejecuta tres escenarios JMeter y notifica el resultado
en Slack.

## 2. Flujo implementado

1. Checkout de la rama `develop`.
2. Compilacion Maven sin pruebas.
3. Pruebas unitarias con JUnit y reporte JUnit.
4. Cobertura con JaCoCo.
5. Analisis SonarCloud mediante SonarScanner for Maven.
6. Pruebas Selenium en Chromium headless.
7. Pruebas de carga JMeter Verde, Amarillo y Rojo.
8. Notificacion final en `#ci-attendance` mediante Slack.

Evidencia principal: [Jenkins build #16](http://localhost:8080/job/attendance-pipeline/16/console).

## 3. Slack y Jenkins

Se creo una aplicacion Slack con el permiso `chat:write`, se instalo en el
espacio Vallegrande, se agrego al canal `#ci-attendance` y se registro en
Jenkins mediante la credencial `slack-attendance-token`. El bloque `post` del
Jenkinsfile envia mensajes para los estados exitoso, fallido e inestable.

La ejecucion #16 termino correctamente y envio la notificacion:
`Attendance #16: pipeline EXITOSO`.

Captura requerida: canal `#ci-attendance` mostrando el mensaje del bot.

## 4. SonarCloud

El analisis se ejecuta con la organizacion `victortorreblancafranco` y el
proyecto `VictorTorreblancaFranco_as241_prs5_attendance`.

Evidencia: [panel SonarCloud](https://sonarcloud.io/dashboard?id=VictorTorreblancaFranco_as241_prs5_attendance).

En la captura se deben mostrar Quality Gate, issues, duplicacion y cobertura.
Los issues abiertos representan deuda tecnica pendiente; no impiden demostrar
que el analisis fue integrado al pipeline.

## 5. Selenium funcional

### Caso 1: carga del formulario de inicio de sesion

- Funcionalidad: acceso al formulario `/auth/login`.
- Pasos: abrir la ruta, esperar el renderizado Angular y verificar correo y
  contrasena.
- Resultado esperado: se muestran el titulo, el campo `email` y el campo
  `contrasena`.
- Resultado obtenido: aprobado en Jenkins.

### Caso 2: proteccion de Attendance

- Funcionalidad: control de acceso a `/attendance`.
- Pasos: abrir la ruta sin una sesion previa.
- Resultado esperado: el guard redirige a `/auth/login`.
- Resultado obtenido: aprobado en Jenkins.

En Jenkins: `Tests run: 2, Failures: 0, Errors: 0`.

## 6. JMeter

Los planes consultan `GET /api/attendance/date` sobre el servicio de
Attendance. Los escenarios se ejecutaron en QA con JMeter 5.6.3.

| Escenario | Usuarios | Duracion | Muestras | Promedio | P90 | P95 | Maximo | Error |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Verde | 5 | 60 s | 1,617,633 | 0.16 ms | 1 ms | 1 ms | 333 ms | 100% (401) |
| Amarillo | 25 | 180 s | 5,310,921 | 0.77 ms | 2 ms | 2 ms | 42 ms | 100% (401) |
| Rojo | 75 | 300 s | 7,362,902 | 2.74 ms | 6 ms | 8 ms | 125 ms | 100% (401) |

Interpretacion: los tres escenarios generaron carga real y el servicio
respondio rapidamente, pero rechazo todas las solicitudes porque el endpoint
requiere autenticacion. Por ello, el resultado demuestra correctamente la
proteccion del API, pero no mide aun el rendimiento del flujo de negocio con
datos autorizados. Para una segunda iteracion se debe agregar un token de QA
temporal como variable protegida de Jenkins, nunca dentro del `.jmx` ni Git.

Los archivos `.jtl`, logs y `resumen.tsv` quedan archivados en el build #16.

## 7. Guion del video (8 a 10 minutos)

1. Presentar el proyecto Attendance y el objetivo del pipeline (1 min).
2. Mostrar el `Jenkinsfile` y los parametros `RUN_SONAR`, `RUN_E2E` y
   `RUN_JMETER` (1 min).
3. Ejecutar o abrir Jenkins build #16 y mostrar Checkout, Compile y Unit tests
   (1 min).
4. Mostrar SonarCloud y explicar Quality Gate e issues (1 min).
5. Mostrar los dos casos Selenium y sus resultados aprobados (1 min).
6. Abrir los artefactos JMeter y explicar Verde, Amarillo y Rojo con la tabla
   de resultados (2 min).
7. Mostrar Slack con `Attendance #16: pipeline EXITOSO` (1 min).
8. Cerrar indicando que el siguiente paso de mejora es ejecutar JMeter con un
   token de QA para medir respuestas autorizadas (30 s).

