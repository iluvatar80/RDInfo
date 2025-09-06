// ============================================================
// File: app/src/main/java/com/rdinfo/logic/RuleDosingService.kt
// (Minimaler Service – nur, falls irgendwo referenziert)
// ============================================================
package com.rdinfo.logic

class RuleDosingService {
    fun availableRoutes(): List<String> = listOf("i.v.", "i.m.", "i.o.", "s.c.")
}


// ===============================================================
// File: app/src/main/java/com/rdinfo/logic/RuleDosingUiAdapter.kt
// (Minimaler UI‑Adapter – nur, falls irgendwo referenziert)
// ===============================================================
package com.rdinfo.logic

object RuleDosingUiAdapter {
    fun formatMl(value: Double, decimals: Int = 2): String =
        String.format("%1$.${decimals}f ml", value)

    fun formatMg(value: Double, decimals: Int = 2): String =
        String.format("%1$.${decimals}f mg", value)
}

