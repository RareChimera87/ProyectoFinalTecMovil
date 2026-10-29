package me.santiagoreina.parcheu.feature.exportdata

import me.santiagoreina.parcheu.data.account.accountDataModule
import me.santiagoreina.parcheu.data.auth.SessionCleaner
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** 'Descargar mis datos' (E8.3). */
val exportDataModule =
    module {
        includes(accountDataModule)
        single<ExportFileStore> { AndroidExportFileStore(androidContext()) }
        // Cerrar sesión (E8.2) borra el JSON exportado que quedó en la caché.
        single<SessionCleaner>(named("export-files")) { exportFilesCleaner(get()) }
        viewModel { ExportDataViewModel(get(), get()) }
    }
