package de.telekom.dtagsyncpluskit.utils

class ApiException @JvmOverloads constructor(
    override val message: String? = null,
    override val cause: Throwable? = null
) : Exception(message, cause)