package io.github.demiterhugo.debuglens.ui.diagnostic

import androidx.annotation.StringRes
import io.github.demiterhugo.debuglens.R
import io.github.demiterhugo.debuglens.domain.model.DiagnosticCategory

@get:StringRes
internal val DiagnosticCategory.titleResource: Int
    get() = when (this) {
        DiagnosticCategory.NULL_REFERENCE -> R.string.category_null_reference
        DiagnosticCategory.INVALID_STATE -> R.string.category_invalid_state
        DiagnosticCategory.INVALID_ARGUMENT -> R.string.category_invalid_argument
        DiagnosticCategory.INDEX_OUT_OF_BOUNDS -> R.string.category_index_out_of_bounds
        DiagnosticCategory.TYPE_MISMATCH -> R.string.category_type_mismatch
        DiagnosticCategory.SECURITY -> R.string.category_security
        DiagnosticCategory.OUT_OF_MEMORY -> R.string.category_out_of_memory
        DiagnosticCategory.UNRESPONSIVE -> R.string.category_unresponsive
        DiagnosticCategory.UNKNOWN -> R.string.category_unknown
    }

@get:StringRes
internal val DiagnosticCategory.guidanceResource: Int
    get() = when (this) {
        DiagnosticCategory.NULL_REFERENCE -> R.string.guidance_null_reference
        DiagnosticCategory.INVALID_STATE -> R.string.guidance_invalid_state
        DiagnosticCategory.INVALID_ARGUMENT -> R.string.guidance_invalid_argument
        DiagnosticCategory.INDEX_OUT_OF_BOUNDS -> R.string.guidance_index_out_of_bounds
        DiagnosticCategory.TYPE_MISMATCH -> R.string.guidance_type_mismatch
        DiagnosticCategory.SECURITY -> R.string.guidance_security
        DiagnosticCategory.OUT_OF_MEMORY -> R.string.guidance_out_of_memory
        DiagnosticCategory.UNRESPONSIVE -> R.string.guidance_unresponsive
        DiagnosticCategory.UNKNOWN -> R.string.guidance_unknown
    }
