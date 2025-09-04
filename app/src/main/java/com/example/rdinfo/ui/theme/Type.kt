package com.example.rdinfo.ui.theme

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rdinfo.data.InfoRepository
import com.example.rdinfo.data.local.AppDatabase
import com.example.rdinfo.data.local.InfoEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InfoUiState(
    val items: List<InfoEntity> = emptyList()
)

class InfoViewModel(app: Application) : AndroidViewModel(app) {

    private val repo by lazy {
        val db = AppDatabase.get(app)
        InfoRepository(db.infoDao())
    }

    val state: StateFlow<InfoUiState> =
        repo.observeAll()
            .map { list -> InfoUiState(items = list) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InfoUiState())

    fun add(title: String, detail: String) = viewModelScope.launch {
        if (title.isNotBlank()) repo.add(title, detail)
    }

    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
    fun clear() = viewModelScope.launch { repo.clear() }
}
