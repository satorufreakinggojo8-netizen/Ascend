package com.cadence.fit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cadence.fit.ui.CadenceViewModel
import com.cadence.fit.ui.navigation.CadenceApp
import com.cadence.fit.ui.theme.CadenceTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CadenceApplication).container

        setContent {
            CadenceTheme {
                val viewModel: CadenceViewModel = viewModel {
                    CadenceViewModel(
                        repository = container.repository,
                        sleepRepository = container.sleepRepository,
                        backupService = container.backupService,
                    )
                }
                CadenceApp(viewModel)
            }
        }
    }
}
