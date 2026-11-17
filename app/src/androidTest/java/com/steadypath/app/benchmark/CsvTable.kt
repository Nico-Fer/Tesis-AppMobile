package com.steadypath.app.benchmark

import java.io.File
import java.util.Locale

/**
 * CSV con encabezado fijo que se agrega fila a fila. Los decimales se escriben con
 * `Locale.US` (punto) sea cual sea el idioma del dispositivo de Test Lab.
 */
class CsvTable(val file: File, private val columns: List<String>) {

    init {
        file.parentFile?.mkdirs()
        if (!file.exists()) file.writeText(columns.joinToString(",") + "\n")
    }

    fun append(row: Map<String, Any?>) {
        require(row.keys == columns.toSet()) {
            "Columnas de ${file.name} no coinciden: sobran ${row.keys - columns.toSet()}, " +
                "faltan ${columns.toSet() - row.keys}"
        }
        file.appendText(columns.joinToString(",") { format(row[it]) } + "\n")
    }

    private fun format(value: Any?): String = when (value) {
        null -> ""
        is Double -> if (value.isFinite()) String.format(Locale.US, "%.3f", value) else value.toString()
        is Float -> if (value.isFinite()) String.format(Locale.US, "%.5f", value) else value.toString()
        is String -> if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
        else -> value.toString()
    }
}
