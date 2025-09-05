// File: app/src/main/java/com/example/rdinfo/ui/theme/InfoViewModel.kt
package com.example.rdinfo.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rdinfo.data.InfoRepository
import com.example.rdinfo.data.local.DrugEntity
import com.example.rdinfo.data.local.FormulationEntity
import com.example.rdinfo.data.local.UseCaseEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Zentrales ViewModel für die Einsatz-/Info-Ansicht.
 * Minimal, aber vollständig kompilierbar.
 */
class InfoViewModel : ViewModel() {

    private val _selectedDrug = MutableStateFlow<DrugEntity?>(null)
    val selectedDrug: StateFlow<DrugEntity?> = _selectedDrug.asStateFlow()

    private val _selectedUseCase = MutableStateFlow<UseCaseEntity?>(null)
    val selectedUseCase: StateFlow<UseCaseEntity?> = _selectedUseCase.asStateFlow()

    private val _selectedFormulation = MutableStateFlow<FormulationEntity?>(null)
    val selectedFormulation: StateFlow<FormulationEntity?> = _selectedFormulation.asStateFlow()

    private val _weightKg = MutableStateFlow<Double?>(null)
    val weightKg: StateFlow<Double?> = _weightKg.asStateFlow()

    // --- Setter ---
    fun setSelectedDrug(drug: DrugEntity?) {
        _selectedDrug.value = drug
        // Abhängigkeiten zurücksetzen
        _selectedUseCase.value = null
        _selectedFormulation.value = null
    }

    fun setSelectedUseCase(uc: UseCaseEntity?) {
        _selectedUseCase.value = uc
    }

    fun setSelectedFormulation(form: FormulationEntity?) {
        _selectedFormulation.value = form
    }

    fun setWeightKg(v: Double?) {
        _weightKg.value = v
    }

    fun useCases(repo: InfoRepository) =
        selectedDrug
            .flatMapLatest { d ->
                if (d != null) repo.observeUseCasesByDrug(d.id)
                else flowOf(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Auswahl eines Einsatzfalls → passende Formulierung (Route/Konzentration) ermitteln
     * und setzen. Nutzt das Repository, damit keine DB-Logik ins ViewModel wandert.
     */
    fun onUseCaseSelected(uc: UseCaseEntity, repo: InfoRepository) {
        viewModelScope.launch {
            _selectedUseCase.value = uc
            val drug = _selectedDrug.value ?: return@launch
            val form = repo.pickPreferredFormulationForUseCase(drug.id, uc.id)
            if (form != null) {
                _selectedFormulation.value = form
            }
        }
    }
}
