package pe.edu.vallegrande.edunova.attendance.domain.exception;

public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
