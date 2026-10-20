package com.pietrondo.aquariummanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pietrondo.aquariummanager.presentation.AquariumListScreen
import com.pietrondo.aquariummanager.presentation.AquariumListViewModel
import com.pietrondo.aquariummanager.presentation.AquariumTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val factory = viewModelFactory {
            initializer { AquariumListViewModel((application as AquariumApplication).aquariumRepository) }
        }
        val viewModel = ViewModelProvider(this, factory)[AquariumListViewModel::class.java]
        setContent { AquariumTheme { AquariumListScreen(viewModel) } }
    }
}
