package de.wartezeiten.app.core.utils

import androidx.lifecycle.SavedStateHandle

/**
 * Reads a navigation argument from a [SavedStateHandle] without an unchecked cast.
 *
 * [SavedStateHandle.get] performs a generic cast that can throw a ClassCastException when the
 * stored value has a different type than the requested type parameter (e.g. a non-String value
 * stored under a nav argument that is read as String). Reading via `get<Any?>` avoids the cast
 * entirely and lets [Any.toString] produce a stable string representation.
 */
fun SavedStateHandle.readStringArgument(key: String): String? {
    return get<Any?>(key)?.toString()
}
