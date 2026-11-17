package com.steadypath.app.domain.comparison

/**
 * De dónde salió la referencia contra la que se compara una ejecución.
 *
 * Importa porque determina **cuánto puede afirmar el sistema**. Una referencia registrada
 * por el profesional con la persona presente respalda un juicio sobre la corrección del
 * movimiento; una que la persona grabó sola, no: el sistema reproduciría fielmente la forma
 * que se le enseñó, sea buena o mala, y diría que está bien mientras está mal.
 *
 * No se bloquea la grabación autónoma ---hacerlo dejaría sin la función a quien no tiene un
 * profesional a mano, que es exactamente la barrera de acceso que este trabajo busca
 * reducir---, pero sí se ajusta el lenguaje de la retroalimentación al respaldo disponible.
 * Es la misma exigencia que impone el requerimiento RNF-17: no presentar la devolución en
 * términos que se lean como una evaluación clínica.
 *
 * El dato ya viaja desde el backend (`creadaPorProfesional` en la respuesta de referencia);
 * esta enumeración solo le da forma en el dominio.
 */
enum class ReferenceProvenance {
    /** La registró el profesional con la persona presente. Respalda juicio de corrección. */
    PROFESIONAL,

    /** La registró la persona por su cuenta. Sirve para medir consistencia, no corrección. */
    PROPIA,

    /** Referencia genérica de fábrica: ni siquiera es de esta persona. */
    EMPAQUETADA;

    /**
     * Si el sistema puede afirmar que un movimiento está *bien hecho*, en lugar de limitarse
     * a decir que se parece a la referencia disponible.
     */
    val respaldaCorreccion: Boolean get() = this == PROFESIONAL
}
