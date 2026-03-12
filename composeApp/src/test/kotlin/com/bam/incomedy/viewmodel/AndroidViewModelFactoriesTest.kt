package com.bam.incomedy.viewmodel

import androidx.lifecycle.viewmodel.CreationExtras
import com.bam.incomedy.feature.auth.viewmodel.AuthAndroidViewModel
import com.bam.incomedy.feature.session.viewmodel.SessionAndroidViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Регрессионные тесты явных Android-фабрик `ViewModel`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AndroidViewModelFactoriesTest {
    /** Проверяет создание Android-адаптера авторизации через явную фабрику. */
    @Test
    fun authFactoryCreatesAuthAndroidViewModel() {
        val application = RuntimeEnvironment.getApplication()
        val factory = AndroidViewModelFactories.auth(application)

        val viewModel = factory.create(
            AuthAndroidViewModel::class.java,
            CreationExtras.Empty,
        )

        assertEquals(AuthAndroidViewModel::class.java, viewModel.javaClass)
    }

    /** Проверяет создание Android-адаптера сессии через явную фабрику. */
    @Test
    fun sessionFactoryCreatesSessionAndroidViewModel() {
        val application = RuntimeEnvironment.getApplication()
        val factory = AndroidViewModelFactories.session(application)

        val viewModel = factory.create(
            SessionAndroidViewModel::class.java,
            CreationExtras.Empty,
        )

        assertEquals(SessionAndroidViewModel::class.java, viewModel.javaClass)
    }
}
