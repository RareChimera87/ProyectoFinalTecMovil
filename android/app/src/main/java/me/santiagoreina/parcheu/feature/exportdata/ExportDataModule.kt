package me.santiagoreina.parcheu.feature.exportdata

import me.santiagoreina.parcheu.data.account.accountDataModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** 'Descargar mis datos' (E8.3). */
val exportDataModule =
    module {
        includes(accountDataModule)
        single<ExportFileStore> { AndroidExportFileStore(androidContext()) }
        viewModel { ExportDataViewModel(get(), get()) }
    }
