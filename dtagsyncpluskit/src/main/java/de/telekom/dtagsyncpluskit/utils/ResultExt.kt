package de.telekom.dtagsyncpluskit.utils

sealed class ResultExt<out T, out E>

class Ok<out T>(val value: T) : ResultExt<T, Nothing>()

class Err<out E>(val error: E) : ResultExt<Nothing, E>()

fun <T, E> ResultExt<T, E>.isOk(): Boolean =
    when (this) {
        is Ok -> true
        is Err -> false
    }

fun <T, E> ResultExt<T, E>.isErr(): Boolean =
    when (this) {
        is Ok -> false
        is Err -> true
    }

fun <T, E> ResultExt<T, E>.errorOrNull(): Err<E>? =
    when (this) {
        is Ok -> null
        is Err -> this
    }

fun <U, T, E> ResultExt<T, E>.map(transform: (T) -> U): ResultExt<U, E> =
    when (this) {
        is Ok -> Ok(transform(value))
        is Err -> this
    }

fun <U, T, E> ResultExt<T, E>.mapError(transform: (E) -> U): ResultExt<T, U> =
    when (this) {
        is Ok -> this
        is Err -> Err(transform(error))
    }

fun <U, T, E> ResultExt<T, E>.andThen(transform: (T) -> ResultExt<U, E>): ResultExt<U, E> =
    when (this) {
        is Ok -> transform(value)
        is Err -> this
    }