CREATE TABLE IF NOT EXISTS asistencia_sesiones (
    id BIGSERIAL PRIMARY KEY,
    institucion_id UUID,
    clase_id VARCHAR(80) NOT NULL,
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
);

ALTER TABLE asistencias
ADD COLUMN IF NOT EXISTS session_id BIGINT;

ALTER TABLE asistencias
ADD COLUMN IF NOT EXISTS institucion_id UUID;

ALTER TABLE asistencia_sesiones
ADD COLUMN IF NOT EXISTS institucion_id UUID;

ALTER TABLE asistencia_sesiones
ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(120);

DROP INDEX IF EXISTS ux_asistencia_sesiones_clase_fecha;
DROP INDEX IF EXISTS ux_asistencia_sesiones_tenant_clase_fecha;
CREATE UNIQUE INDEX IF NOT EXISTS ux_asistencia_sesiones_tenant_clase_fecha_anio
ON asistencia_sesiones (institucion_id, clase_id, fecha, anio_lectivo);

CREATE UNIQUE INDEX IF NOT EXISTS ux_asistencia_sesiones_tenant_idempotency_key
ON asistencia_sesiones (institucion_id, idempotency_key)
WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_asistencias_session_id
ON asistencias (session_id);

CREATE INDEX IF NOT EXISTS ix_asistencias_institucion_id
ON asistencias (institucion_id);

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
ON CONFLICT (institucion_id, clase_id, fecha, anio_lectivo) DO NOTHING;

UPDATE asistencias a
SET session_id = s.id
FROM asistencia_sesiones s
WHERE a.session_id IS NULL
  AND s.institucion_id IS NOT DISTINCT FROM a.institucion_id
  AND s.clase_id = a.clase_id
  AND s.fecha = a.fecha
  AND s.anio_lectivo = a.anio_lectivo;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_asistencias_session_id'
    ) THEN
        ALTER TABLE asistencias
        ADD CONSTRAINT fk_asistencias_session_id
        FOREIGN KEY (session_id)
        REFERENCES asistencia_sesiones(id);
    END IF;
END $$;
