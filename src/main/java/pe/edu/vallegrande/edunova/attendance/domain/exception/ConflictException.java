package pe.edu.vallegrande.edunova.attendance.domain.exception;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
