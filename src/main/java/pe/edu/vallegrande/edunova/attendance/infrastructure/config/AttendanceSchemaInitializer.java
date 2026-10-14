package pe.edu.vallegrande.edunova.attendance.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceSchemaInitializer implements ApplicationRunner {

    private final DatabaseClient databaseClient;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        createAttendanceTable()
                .then(createSessionTable())
                .then(addSessionIdToAttendance())
                .then(backfillAttendanceSessions())
                .then(createAuditTable())
                .then(normalizeExistingAttendanceData())
                // Las columnas de auditoría deben existir antes de crear sus índices.
                .then(createSessionIndexes())
                .then(createDuplicateProtectionIndex())
                .doOnSuccess(ignored -> log.info("Attendance schema checks completed"))
                // No se acepta tráfico con el esquema parcialmente actualizado.
                .block();
    }

    private Mono<Void> createAttendanceTable() {
        return databaseClient.sql("""
                CREATE TABLE IF NOT EXISTS asistencias (
                    id BIGSERIAL PRIMARY KEY,
                    session_id BIGINT,
                    institucion_id UUID,
                    estudiante_id VARCHAR(80) NOT NULL,
                    clase_id VARCHAR(80) NOT NULL,
                    profesor_id VARCHAR(80) NOT NULL,
                    registrado_por VARCHAR(80),
                    fecha DATE NOT NULL,
                    anio_lectivo INTEGER NOT NULL,
                    estado VARCHAR(2) NOT NULL,
                    hora_llegada TIME,
                    justificacion_nota TEXT,
                    justificacion_foto_url TEXT,
                    registrado_en TIMESTAMP,
                    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    version INTEGER NOT NULL DEFAULT 0
                )
                """)
                .then();
    }

    private Mono<Void> createSessionTable() {
        return databaseClient.sql("""
                CREATE TABLE IF NOT EXISTS asistencia_sesiones (
                    id BIGSERIAL PRIMARY KEY,
                    institucion_id UUID,
                    clase_id VARCHAR(80) NOT NULL,
                    idempotency_key VARCHAR(120),
                    profesor_id VARCHAR(80) NOT NULL,
                    fecha DATE NOT NULL,
                    anio_lectivo INTEGER NOT NULL,
                    estado VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
                    total_estudiantes INTEGER,
                    registros_guardados INTEGER NOT NULL DEFAULT 0,
                    creado_por VARCHAR(80),
                    enviado_por VARCHAR(80),
                    enviado_en TIMESTAMP,
                    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    version INTEGER NOT NULL DEFAULT 0
                )
                """)
                .then();
    }

    private Mono<Void> addSessionIdToAttendance() {
        return databaseClient.sql("""
                ALTER TABLE asistencias
                ADD COLUMN IF NOT EXISTS session_id BIGINT;
                """)
                .then()
                .then(databaseClient.sql("""
                    ALTER TABLE asistencias
                    ADD COLUMN IF NOT EXISTS institucion_id UUID
                    """).then())
                .then(databaseClient.sql("""
                    ALTER TABLE asistencia_sesiones
                    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(120)
                    """).then())
                .then(databaseClient.sql("""
                    ALTER TABLE asistencia_sesiones
                    ADD COLUMN IF NOT EXISTS institucion_id UUID
                    """).then());
    }

    private Mono<Void> backfillAttendanceSessions() {
        return databaseClient.sql("""
                INSERT INTO asistencia_sesiones (
                    institucion_id,
                    clase_id,
                    profesor_id,
                    fecha,
                    anio_lectivo,
                    estado,
                    total_estudiantes,
                    registros_guardados,
                    creado_por,
                    enviado_por,
                    enviado_en,
                    creado_en,
                    actualizado_en,
                    version
                )
                SELECT
                    a.institucion_id,
                    a.clase_id,
                    MIN(a.profesor_id) AS profesor_id,
                    a.fecha,
                    MIN(a.anio_lectivo) AS anio_lectivo,
                    'SUBMITTED' AS estado,
                    COUNT(*) AS total_estudiantes,
                    COUNT(*) AS registros_guardados,
                    MIN(a.registrado_por) AS creado_por,
                    MIN(a.registrado_por) AS enviado_por,
                    MAX(COALESCE(a.actualizado_en, a.registrado_en, a.creado_en, CURRENT_TIMESTAMP)) AS enviado_en,
                    MIN(COALESCE(a.creado_en, a.registrado_en, CURRENT_TIMESTAMP)) AS creado_en,
                    MAX(COALESCE(a.actualizado_en, a.registrado_en, a.creado_en, CURRENT_TIMESTAMP)) AS actualizado_en,
                    0 AS version
                FROM asistencias a
                WHERE a.session_id IS NULL
                GROUP BY a.institucion_id, a.clase_id, a.fecha, a.anio_lectivo
                ON CONFLICT (institucion_id, clase_id, fecha, anio_lectivo) DO NOTHING
                """)
                .then()
                .then(databaseClient.sql("""
                        UPDATE asistencias a
                        SET session_id = s.id
                        FROM asistencia_sesiones s
                        WHERE a.session_id IS NULL
                          AND s.institucion_id IS NOT DISTINCT FROM a.institucion_id
                          AND s.clase_id = a.clase_id
                          AND s.fecha = a.fecha
                          AND s.anio_lectivo = a.anio_lectivo
                        """).then())
                ;
    }

    private Mono<Void> createAuditTable() {
        return databaseClient.sql("""
                CREATE TABLE IF NOT EXISTS asistencia_auditoria (
                    id BIGSERIAL PRIMARY KEY,
                    attendance_id BIGINT NOT NULL,
                    institucion_id UUID,
                    changed_by VARCHAR(80),
                    changed_by_role VARCHAR(40),
                    action VARCHAR(30) NOT NULL,
                    previous_estado VARCHAR(2),
                    new_estado VARCHAR(2),
                    previous_hora_llegada VARCHAR(20),
                    new_hora_llegada VARCHAR(20),
                    previous_justificacion_nota TEXT,
                    new_justificacion_nota TEXT,
                    previous_justificacion_foto_url TEXT,
                    new_justificacion_foto_url TEXT,
                    motivo_cambio TEXT,
                    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """)
                .then();
    }

    private Mono<Void> normalizeExistingAttendanceData() {
        return databaseClient.sql("""
                UPDATE asistencias
                SET hora_llegada = NULL
                WHERE estado <> 'T' AND hora_llegada IS NOT NULL
                """)
                .then()
                .then(databaseClient.sql("""
                    ALTER TABLE asistencia_auditoria
                    ADD COLUMN IF NOT EXISTS institucion_id UUID
                    """).then())
                .then(databaseClient.sql("""
                    UPDATE asistencia_auditoria audit
                    SET institucion_id = attendance.institucion_id
                    FROM asistencias attendance
                    WHERE audit.attendance_id = attendance.id
                      AND audit.institucion_id IS NULL
                    """).then())
                .then(databaseClient.sql("""
                        UPDATE asistencias
                        SET justificacion_nota = NULL,
                            justificacion_foto_url = NULL
                        WHERE estado NOT IN ('J', 'T')
                          AND (justificacion_nota IS NOT NULL OR justificacion_foto_url IS NOT NULL)
                        """).then());
    }

    private Mono<Void> createDuplicateProtectionIndex() {
        return databaseClient.sql("""
                DROP INDEX IF EXISTS ux_asistencias_estudiante_clase_fecha
                """)
                .then()
                .then(databaseClient.sql("""
                    DROP INDEX IF EXISTS ux_asistencias_tenant_estudiante_clase_fecha
                    """).then())
                .then(databaseClient.sql("""
                    CREATE UNIQUE INDEX IF NOT EXISTS ux_asistencias_tenant_estudiante_clase_fecha_anio
                    ON asistencias (institucion_id, estudiante_id, clase_id, fecha, anio_lectivo)
                    """).then())
                ;
    }

    private Mono<Void> createSessionIndexes() {
        return databaseClient.sql("""
                DROP INDEX IF EXISTS ux_asistencia_sesiones_clase_fecha
                """)
                .then()
                .then(databaseClient.sql("""
                    DROP INDEX IF EXISTS ux_asistencia_sesiones_tenant_clase_fecha
                    """).then())
                .then(databaseClient.sql("""
                    CREATE UNIQUE INDEX IF NOT EXISTS ux_asistencia_sesiones_tenant_clase_fecha_anio
                    ON asistencia_sesiones (institucion_id, clase_id, fecha, anio_lectivo)
                    """).then())
                .then(databaseClient.sql("""
                    CREATE UNIQUE INDEX IF NOT EXISTS ux_asistencia_sesiones_tenant_idempotency_key
                    ON asistencia_sesiones (institucion_id, idempotency_key)
                    WHERE idempotency_key IS NOT NULL
                    """).then())
                .then(databaseClient.sql("""
                        CREATE INDEX IF NOT EXISTS ix_asistencias_session_id
                        ON asistencias (session_id)
                        """).then())
                .then(databaseClient.sql("""
                        CREATE INDEX IF NOT EXISTS ix_asistencias_institucion_id
                        ON asistencias (institucion_id)
                        """).then())
                .then(databaseClient.sql("""
                        CREATE INDEX IF NOT EXISTS ix_asistencias_estudiante_fecha
                        ON asistencias (institucion_id, estudiante_id, fecha DESC)
                        """).then())
                .then(databaseClient.sql("""
                        CREATE INDEX IF NOT EXISTS ix_asistencias_clase_fecha
                        ON asistencias (institucion_id, clase_id, fecha)
                        """).then())
                .then(databaseClient.sql("""
                        CREATE INDEX IF NOT EXISTS ix_asistencia_sesiones_institucion_id
                        ON asistencia_sesiones (institucion_id)
                        """).then())
                .then(databaseClient.sql("""
                        CREATE INDEX IF NOT EXISTS ix_auditoria_attendance_id_changed_at
                        ON asistencia_auditoria (attendance_id, changed_at DESC)
                        """).then())
                .then(databaseClient.sql("""
                        CREATE INDEX IF NOT EXISTS ix_auditoria_tenant_attendance_id_changed_at
                        ON asistencia_auditoria (institucion_id, attendance_id, changed_at DESC)
                        """).then())
                ;
    }
}
