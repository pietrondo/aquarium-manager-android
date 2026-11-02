package com.pietrondo.aquariummanager.presentation

/** Resolves localized UI copy for state holders that are not composables. */
fun interface UiStrings {
    fun get(id: Int, vararg args: Any): String
}
