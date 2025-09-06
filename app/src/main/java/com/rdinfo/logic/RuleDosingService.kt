// File: app/src/main/java/com/rdinfo/logic/RuleDosingService.kt
package com.rdinfo.logic

/**
 * Minimaler Service für Dosierregeln. Keine UI‑Abhängigkeiten.
 */
class RuleDosingService {

    data class DosingRule(
        val id: String,
        val name: String,
        val route: String? = null,   // z. B. "i.v.", "i.m.", ...
        val mgPerKg: Double? = null,
        val mcgPerKg: Double? = null,
        val absoluteMg: Double? = null,
    )

    /** Gibt eine kleine Standardliste an Applikationswegen zurück. */
    fun availableRoutes(): List<String> = listOf("i.v.", "i.m.", "i.o.", "s.c.", "p.o.")

    /**
     * Wählt aus einer Menge von Regeln eine sinnvolle "beste" Regel aus.
     * Priorität: passender Applikationsweg > mg/kg > µg/kg > absolute mg.
     */
    fun findBestDosingRule(rules: List<DosingRule>, route: String? = null): DosingRule? {
        if (rules.isEmpty()) return null
        val want = route?.trim()?.lowercase()
        return rules
            .sortedWith(
                compareByDescending<DosingRule> { it.route?.trim()?.lowercase() == want }
                    .thenByDescending { it.mgPerKg != null }
                    .thenByDescending { it.mcgPerKg != null }
                    .thenByDescending { it.absoluteMg != null }
            )
            .firstOrNull()
    }
}
