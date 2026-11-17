package com.steadypath.app.domain.model

/**
 * Lente de la cámara con el que se ejecuta una sesión, una calibración o la grabación de
 * una referencia.
 *
 * **[FRONTAL] es el lente "selfie"**, el del mismo lado que la pantalla: apunta a la
 * persona mientras ésta mira el visor, de modo que puede ver el esqueleto, el contador de
 * repeticiones y el botón de emergencia durante el ejercicio. Es el modo natural para uso
 * autónomo y por eso es el predeterminado del paciente.
 *
 * **[TRASERA]** apunta en sentido contrario a la pantalla. Es el modo para cuando otra
 * persona sostiene el teléfono ---el profesional grabando la referencia de quien atiende,
 * o un familiar acompañando--- y también la alternativa cuando hace falta más campo de
 * visión del que da el lente frontal.
 *
 * No debe confundirse con la **vista frontal**, que describe la orientación del cuerpo
 * respecto de la cámara (la persona de frente) y es independiente del lente utilizado.
 *
 * Sobre el espejado, y conviene ser preciso porque es delicado. `PreviewView` espeja la
 * imagen del lente frontal para que la persona se vea como en un espejo, mientras que el
 * frame que llega al análisis no está espejado: por eso el overlay debe invertir el eje
 * horizontal al dibujar. Esa es una corrección de presentación y no toca el pipeline.
 *
 * Lo que **no** vale es suponer que el espejado le da igual a la comparación. Los ángulos
 * que produce `DefaultAngleExtractor` son **signados** ---`atan2(cross, dot)` en (−π, π]---
 * y una ejecución espejada les invierte el signo. Que una referencia grabada con un lente
 * sirva para una sesión ejecutada con el otro depende de que ambos entreguen al análisis
 * frames con la misma lateralidad, cosa que se espera de CameraX pero que **debe
 * verificarse en dispositivo**: si no se cumpliera, la comparación fallaría en silencio,
 * devolviendo correcciones erróneas en lugar de un error visible.
 */
enum class CameraLens(val etiqueta: String) {
    FRONTAL("Cámara frontal"),
    TRASERA("Cámara trasera");

    companion object {
        /** Predeterminado del paciente: cámara y pantalla miran a la persona. */
        val PREDETERMINADO_PACIENTE = FRONTAL

        /** Predeterminado del profesional: sostiene el teléfono y apunta a otra persona. */
        val PREDETERMINADO_PROFESIONAL = TRASERA
    }
}
