package co.santahelena.his.util;

/** Se lanza cuando una operación viola una regla de negocio; el mensaje se muestra al usuario. */
public class ReglaException extends RuntimeException {

    public ReglaException(String mensaje) {
        super(mensaje);
    }
}
