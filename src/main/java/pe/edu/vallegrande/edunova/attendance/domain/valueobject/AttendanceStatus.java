package pe.edu.vallegrande.edunova.attendance.domain.valueobject;

import lombok.Getter;

@Getter
public enum AttendanceStatus {
    PRESENTE("A", "Asistió", false, false),
    FALTA("F", "Faltó", false, false),
    TARDANZA("T", "Tardanza", true, false),
    JUSTIFICADO("J", "Justificado", false, true);
    
    private final String code;
    private final String description;
    private final boolean requiresTime;
    private final boolean requiresNote;
    
    AttendanceStatus(String code, String description, boolean requiresTime, boolean requiresNote) {
        this.code = code;
        this.description = description;
        this.requiresTime = requiresTime;
        this.requiresNote = requiresNote;
    }
    
    public static AttendanceStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("El estado de asistencia es obligatorio");
        }
        String normalizedCode = code.trim().toUpperCase();
        for (AttendanceStatus status : values()) {
            if (status.code.equals(normalizedCode)) {
                return status;
            }
        }
        throw new IllegalArgumentException("El estado debe ser A, F, T o J");
    }
}
