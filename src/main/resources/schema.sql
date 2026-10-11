-- Esquema de vg-ms-attendance.
--
-- Antes este servicio no arranco en una base recien creada: el unico script
-- (sql/attendance-session-migration.sql) altera las tablas asistencias y
-- asistencia_auditoria sin crearlas, de modo que el ALTER abortaba y el servicio
-- se caia al buscar sus tablas. Este script crea las tres y se ejecuta solo al
-- arrancar (spring.sql.init), asi que una instalacion limpia funciona.
--
-- Todo es idempotente: CREATE ... IF NOT EXISTS. Sobre una base ya migrada no
-- cambia nada.

CREATE TABLE IF NOT EXISTS asistencia_sesiones (
    id                  BIGSERIAL PRIMARY KEY,
    institucion_id      UUID,
    clase_id            VARCHAR(80)  NOT NULL,
    idempotency_key     VARCHAR(120),
    profesor_id         VARCHAR(80)  NOT NULL,
    fecha               DATE         NOT NULL,
    anio_lectivo        INTEGER      NOT NULL,
    estado              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    total_estudiantes   INTEGER,
    registros_guardados INTEGER      NOT NULL DEFAULT 0,
    creado_por          VARCHAR(80),
    enviado_por          VARCHAR(80),
    enviado_en          TIMESTAMP,
    creado_en           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             INTEGER      NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS asistencias (
    id                    BIGSERIAL PRIMARY KEY,
    session_id            BIGINT,
    institucion_id        UUID,
    estudiante_id         VARCHAR(80) NOT NULL,
    clase_id              VARCHAR(80) NOT NULL,
    profesor_id           VARCHAR(80) NOT NULL,
    registrado_por        VARCHAR(80),
    fecha                 DATE        NOT NULL,
    anio_lectivo          INTEGER     NOT NULL,
    estado                VARCHAR(2)  NOT NULL,
    hora_llegada          TIME,
    justificacion_nota    TEXT,
    justificacion_foto_url TEXT,
    registrado_en         TIMESTAMP,
    creado_en             TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version               INTEGER     NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS asistencia_auditoria (
    id                        BIGSERIAL PRIMARY KEY,
    attendance_id             BIGINT       NOT NULL,
    institucion_id            UUID,
    changed_by                VARCHAR(80),
    changed_by_role           VARCHAR(40),
    action                    VARCHAR(30)  NOT NULL,
    previous_estado           VARCHAR(2),
    new_estado                VARCHAR(2),
    previous_hora_llegada     VARCHAR(20),
    new_hora_llegada          VARCHAR(20),
    previous_justificacion_nota    TEXT,
    new_justificacion_nota         TEXT,
    previous_justificacion_foto_url TEXT,
    new_justificacion_foto_url     TEXT,
    motivo_cambio             TEXT,
    changed_at                TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- El indice unico que la propia migracion exige en su ON CONFLICT: sin el, el
-- servicio no arrancaba con "no unique or exclusion constraint matching".
CREATE UNIQUE INDEX IF NOT EXISTS ux_asistencia_sesiones_tenant_clase_fecha_anio
    ON asistencia_sesiones (institucion_id, clase_id, fecha, anio_lectivo);

CREATE UNIQUE INDEX IF NOT EXISTS ux_asistencia_sesiones_tenant_idempotency_key
    ON asistencia_sesiones (institucion_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_asistencias_tenant_estudiante_clase_fecha_anio
    ON asistencias (institucion_id, estudiante_id, clase_id, fecha, anio_lectivo);

CREATE INDEX IF NOT EXISTS ix_asistencias_session_id        ON asistencias (session_id);
CREATE INDEX IF NOT EXISTS ix_asistencias_institucion_id    ON asistencias (institucion_id);
CREATE INDEX IF NOT EXISTS ix_asistencias_clase_fecha       ON asistencias (institucion_id, clase_id, fecha);
CREATE INDEX IF NOT EXISTS ix_asistencias_estudiante_fecha  ON asistencias (institucion_id, estudiante_id, fecha DESC);
CREATE INDEX IF NOT EXISTS ix_asistencia_sesiones_institucion_id ON asistencia_sesiones (institucion_id);
CREATE INDEX IF NOT EXISTS ix_auditoria_attendance_id_changed_at ON asistencia_auditoria (attendance_id, changed_at DESC);
CREATE INDEX IF NOT EXISTS ix_auditoria_tenant_attendance_id_changed_at ON asistencia_auditoria (institucion_id, attendance_id, changed_at DESC);