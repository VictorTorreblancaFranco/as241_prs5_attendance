package com.vg.attendance.infrastructure.adapter.out.client;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Frases cortas de acompañamiento para el aviso de faltas acumuladas.
 *
 * El aviso de 3 faltas consecutivas es de los pocos que sí ameritan una
 * conversación con la familia, así que no se manda siempre el mismo texto: 50
 * frases distintas evitan que el padre reciba un mensaje idéntico cada vez y
 * hacen que el aviso invite a acercarse al colegio en lugar de solo informar.
 *
 * Todas hablan de lo mismo (asistencia y comunicación casa-colegio), porque el
 * mensaje llega a familias que no saben todavía que hay una racha de ausencias.
 */
public final class MensajesFaltaAcumulada {

    private static final String[] FRASES = {
            "Comunicarse con el colegio también es parte de aprender.",
            "La asistencia es el primer paso para no quedarse atrás.",
            "Tres faltas ya son un patrón, no una casualidad.",
            "Faltar cuesta; asistir rinde.",
            "Un día ausente puede ser una semana de atraso.",
            "Lo que se aprende en clase no se recupera en casa.",
            "Estar presente también es respeto por el grupo.",
            "La comunicación evita las ausencias innecesarias.",
            "Si falta, converse con el profesor: casi siempre hay solución.",
            "La familia y el colegio quieren lo mismo por su hijo.",
            "El esfuerzo de un curso se pierde en tres ausencias.",
            "Asistir con puntualidad mantiene el ritmo del grupo.",
            "No avisar de una ausencia es lo que más cuesta después.",
            "Comunicarse a tiempo evita malentendidos y confusiones.",
            "La constancia pesa más que el talento.",
            "El profesor también necesita saber de tus avances.",
            "Antes de faltar, piensa en lo que pierdes.",
            "La asistencia es una costumbre que se entrena.",
            "Cada día en clase suma.",
            "Los compañeros también cuentan con tu presencia.",
            "Un padre informado es un alumno menos ausente.",
            "Faltar por motivos propios se puede evitar.",
            "La comunicación casa-colegio es la base de la confianza.",
            "Ninguna tarea vale una ausencia sin avisar.",
            "El esfuerzo de hoy construye el resultado de mañana.",
            "Estar al día en asistencia facilita la convivencia.",
            "Si tienes una dificultad, coméntala antes de faltar.",
            "La asistencia perfecta es posible con organización.",
            "Un aviso temprano puede convertir una falta en una tarde.",
            "Volver al ritmo tras faltar cuesta más que no hacerlo.",
            "La presencia también comunica interés.",
            "Tus compañeros no pueden hacer tu trabajo por ti.",
            "Conversar evita que la asistencia se convierta en reincidencia.",
            "El colegio está para ayudarte: úsalo a tiempo.",
            "Lo aprendido se pierde si el estudiante falta seguido.",
            "La constancia diaria vale más que los grandes esfuerzos puntuales.",
            "Asistir es la manera más corta de aprender.",
            "Revisar los avisos del colegio evita sorpresas.",
            "Una ausencia sin aviso es la que más preocupa.",
            "El profesor necesita verte para ayudarte mejor.",
            "La puntualidad es la primera muestra de compromiso.",
            "Comunicarse evita que las faltas se repitan sin solución.",
            "El interés de los padres se nota en la asistencia.",
            "No hay excusa que valga tres días perdidos.",
            "Si no puedes asistir, avisa: es tan importante como ir.",
            "La asistencia sostiene los buenos resultados del curso.",
            "Mantener la rutina escolar protege el bienestar del estudiante.",
            "El compromiso con los estudios empieza por la asistencia.",
            "Un colegio y una familia que conversan evitan casi todos los problemas.",
            "Asistir también es una forma de respeto hacia el docente.",
    };

    private MensajesFaltaAcumulada() {
    }

    /** Una frase al azar de las 50 disponibles. */
    public static String frase() {
        return FRASES[ThreadLocalRandom.current().nextInt(FRASES.length)];
    }

    /** Cuántas frases hay, para no dar por buena una lista vacía o duplicada. */
    public static int total() {
        return FRASES.length;
    }
}
