package com.steadypath.app.data.vision

import androidx.camera.core.ImageProxy
import com.steadypath.app.domain.model.PoseFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class PoseEstimatorSessionTest {

    /** Estimador falso con conteo de referencias real; `acquire` puede fallar a pedido. */
    private class FakeEstimator(private val fallaAlAdquirir: Boolean = false) : PoseEstimator {
        var usuarios = 0
        var acquires = 0
        var releases = 0
        override val estado: StateFlow<EstadoEstimador> = MutableStateFlow(EstadoEstimador.Inactivo)
        override fun estimate(image: ImageProxy, rotationDegrees: Int): PoseFrame? = null
        override fun acquire() {
            acquires++
            if (fallaAlAdquirir) throw IllegalStateException("modelo roto")
            usuarios++
        }
        override fun release() {
            releases++
            if (usuarios > 0) usuarios--
        }
    }

    // Unconfined: el launch corre en línea, así el test no necesita coroutines-test.
    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())

    private fun PoseEstimatorSession.startInline() = start(scope, Dispatchers.Unconfined)

    @Test
    fun `dos sesiones comparten el estimador y solo la ultima lo libera`() {
        val estimator = FakeEstimator()
        val a = PoseEstimatorSession(estimator)
        val b = PoseEstimatorSession(estimator)

        a.startInline()
        b.startInline()
        assertEquals(2, estimator.usuarios)

        a.stop()
        assertEquals(1, estimator.usuarios)

        b.stop()
        assertEquals(0, estimator.usuarios)
    }

    @Test
    fun `start y stop repetidos no desbalancean el contador`() {
        val estimator = FakeEstimator()
        val session = PoseEstimatorSession(estimator)

        session.startInline()
        session.startInline()
        assertEquals(1, estimator.acquires)
        assertEquals(1, estimator.usuarios)

        session.stop()
        session.stop()
        assertEquals(1, estimator.releases)
        assertEquals(0, estimator.usuarios)
    }

    @Test
    fun `stop sin start no libera nada`() {
        val estimator = FakeEstimator()
        PoseEstimatorSession(estimator).stop()
        assertEquals(0, estimator.releases)
    }

    @Test
    fun `si acquire falla la sesion no cuenta como adquirida`() {
        val estimator = FakeEstimator(fallaAlAdquirir = true)
        val session = PoseEstimatorSession(estimator)

        session.startInline()
        assertEquals(0, estimator.usuarios)

        session.stop()
        assertEquals(0, estimator.releases)
    }

    @Test
    fun `tras un fallo se puede reintentar con start`() {
        val estimator = FakeEstimator(fallaAlAdquirir = true)
        val session = PoseEstimatorSession(estimator)

        session.startInline()
        session.startInline()
        assertEquals(2, estimator.acquires)
    }
}
