/**
 * This file is part of SyncPlus.
 *
 * Copyright (C) 2020  Deutsche Telekom AG
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.telekom.dtagsyncpluskit

import android.app.Activity
import android.content.res.Resources
import android.os.Build
import android.text.Html
import android.text.Spanned
import androidx.fragment.app.Fragment
import de.telekom.dtagsyncpluskit.api.error.ApiError
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.utils.Err
import de.telekom.dtagsyncpluskit.utils.Ok
import de.telekom.dtagsyncpluskit.utils.ResultExt
import kotlinx.coroutines.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.resume

val Int.dp: Int
    get() = (this / Resources.getSystem().displayMetrics.density).toInt()

val Int.px: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()

fun <K, V> Map<K, V>.toArray(): ArrayList<V> {
    val ret = ArrayList<V>(this.size)
    for ((_, value) in this) {
        ret.add(value)
    }
    return ret
}

fun CoroutineScope.runOnMain(block: suspend CoroutineScope.() -> Unit): Job {
    return launch(Dispatchers.Main, CoroutineStart.DEFAULT, block)
}

inline fun <reified T : Any> Activity.extra(
    key: String,
    default: T? = null,
) = lazy {
    val value = intent?.extras?.get(key)
    if (value is T) value else default
}

inline fun <reified T : Any> Activity.extraNotNull(
    key: String,
    default: T? = null,
) = lazy {
    val value = intent?.extras?.get(key)
    requireNotNull(if (value is T) value else default) { key }
}

inline fun <reified T : Any> Fragment.extra(
    key: String,
    default: T? = null,
) = lazy {
    val value = arguments?.get(key)
    if (value is T) value else default
}

inline fun <reified T : Any> Fragment.extraNotNull(
    key: String,
    default: T? = null,
) = lazy {
    val value = arguments?.get(key)
    requireNotNull(if (value is T) value else default) { key }
}

suspend fun <T> Call<T>.awaitResponse(): ResultExt<Response<T>, ApiError> =
    withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                this@awaitResponse.cancel() // cancel Call enqueuing
            }
            enqueue(
                object : Callback<T> {
                    override fun onResponse(
                        call: Call<T>,
                        response: Response<T>,
                    ) {
                        if (response.isSuccessful) {
                            continuation.resume(Ok(response))
                            return
                        }

                        continuation.resume(Err(exposeError(response)))
                    }

                    override fun onFailure(
                        call: Call<T>,
                        t: Throwable,
                    ) {
                        val error =
                            when (t) {
                                is SocketTimeoutException -> ApiError.TimeoutException
                                is UnknownHostException -> ApiError.UnknownHostException
                                else -> ApiError.NoResponse
                            }
                        continuation.resume(Err(error))
                    }
                },
            )
        }
    }

suspend fun <T> Call<T>.await(): ResultExt<T, ApiError> =
    withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                this@await.cancel() // cancel Call enqueuing
            }
            enqueue(
                object : Callback<T> {
                    override fun onResponse(
                        call: Call<T>,
                        response: Response<T>,
                    ) {
                        if (response.isSuccessful) {
                            response.body()?.let {
                                continuation.resume(Ok(it))
                            } ?: continuation.resume(Err(ApiError.ResponseBodyNull))
                        } else {
                            continuation.resume(Err(exposeError(response)))
                        }
                    }

                    override fun onFailure(
                        call: Call<T>,
                        t: Throwable,
                    ) {
                        val error =
                            when (t) {
                                is SocketTimeoutException -> ApiError.TimeoutException
                                is UnknownHostException -> ApiError.UnknownHostException
                                else -> ApiError.NoResponse
                            }
                        continuation.resume(Err(error))
                    }
                },
            )
        }
    }

private fun <T> exposeError(response: Response<T>): ApiError {
    val message =
        "Request is failed. Code: ${response.code()}. Error: ${response.errorBody()?.string()}"
    Logger.log.warning(message)
    return when (response.code()) {
        ApiError.ContactError.TooManyContacts.errorCode -> ApiError.ContactError.TooManyContacts
        else -> ApiError.ResponseUnsuccessful(response)
    }
}

fun String.fromHTML(): Spanned {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
    } else {
        Html.fromHtml(this)
    }
}
