package com.pietrondo.aquariummanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pietrondo.aquariummanager.presentation.AquariumListScreen
import com.pietrondo.aquariummanager.presentation.AquariumListViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val factory = viewModelFactory {
            initializer { AquariumListViewModel((application as AquariumApplication).aquariumRepository) }
        }
        val viewModel = ViewModelProvider(this, factory)[AquariumListViewModel::class.java]
        setContent { MaterialTheme { AquariumListScreen(viewModel) } }
    }
}
