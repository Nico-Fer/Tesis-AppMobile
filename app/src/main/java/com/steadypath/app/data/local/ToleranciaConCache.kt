package com.steadypath.app.data.local

import com.steadypath.app.data.sync.MonitorDeConectividad
import com.steadypath.app.domain.model.PatientTolerance
import com.steadypath.app.domain.repository.PatientToleranceRepository
import com.steadypath.app.domain.repository.UserSessionRepository

/**
 * Tolerancia calibrada del paciente con copia en el teléfono.
 *
 * Sin esto el comparador de movimiento pedía la tolerancia al servidor al empezar cada
 * ejercicio y, sin red, seguía sin ella sin avisar: el mismo ejercicio se corregía con un
 * rango distinto según hubiera o no conexión.
 */
class ToleranciaConCache(
    private val remoto: PatientToleranceRepository,
    private val dao: ToleranciaPropiaDao,
    private val usuario: UserSessionRepository,
    private val monitor: MonitorDeConectividad
) : PatientToleranceRepository {

    override suspend fun getOwnTolerance(idEjercicio: String): Result<PatientTolerance> {
        val idUsuario = usuario.get()?.idUsuario ?: return remoto.getOwnTolerance(idEjercicio)
        val guardada = runCatching { dao.obtener(idUsuario, idEjercicio.lowercase()) }.getOrNull()

        // Con copia y sin red no se espera a que venza una llamada perdida: el ejercicio arranca.
        if (guardada != null && !monitor.hayRed()) return Result.success(guardada.aDominio())

        val respuesta = remoto.getOwnTolerance(idEjercicio)
        respuesta.onSuccess { tolerancia ->
            runCatching { dao.guardar(tolerancia.aEntidad(idUsuario, idEjercicio)) }
        }
        return if (respuesta.isFailure && guardada != null) Result.success(guardada.aDominio()) else respuesta
    }
}
