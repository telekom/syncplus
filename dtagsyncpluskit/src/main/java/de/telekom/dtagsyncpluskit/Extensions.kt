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
import de.telekom.dtagsyncpluskit.utils.ApiException
import de.telekom.dtagsyncpluskit.utils.CountlyWrapper
import de.telekom.dtagsyncpluskit.utils.Err
import de.telekom.dtagsyncpluskit.utils.Ok
import de.telekom.dtagsyncpluskit.utils.ResultExt
import de.telekom.dtagsyncpluskit.utils.getBodyAsString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Request
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

suspend fun <T> Call<T>.awaitResponse(): ResultExt<Response<T>, ApiError> = withContext(Dispatchers.IO) {
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
                    if (!response.isSuccessful) {
                        recordApiCallException(response)
                        continuation.resume(Err(exposeError(response)))
                        return
                    }

                    when (response.body()) {
                        null -> {
                            recordApiCallException(response)
                            continuation.resume(Err(ApiError.ResponseBodyNull))
                        }

                        else -> continuation.resume(Ok(response))
                    }
                }

                override fun onFailure(
                    call: Call<T>,
                    t: Throwable,
                ) {
                    val error = handleError(call.request(), t)
                    continuation.resume(Err(error))
                }
            },
        )
    }
}

suspend fun <T> Call<T>.await(): ResultExt<T, ApiError> = withContext(Dispatchers.IO) {
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
                    if (!response.isSuccessful) {
                        recordApiCallException(response)
                        continuation.resume(Err(exposeError(response)))
                        return
                    }

                    when (val body = response.body()) {
                        null -> {
                            recordApiCallException(response)
                            continuation.resume(Err(ApiError.ResponseBodyNull))
                        }

                        else -> continuation.resume(Ok(body))
                    }
                }

                override fun onFailure(
                    call: Call<T>,
                    t: Throwable,
                ) {
                    val error = handleError(call.request(), t)
                    continuation.resume(Err(error))
                }
            },
        )
    }
}

private fun handleError(request: Request, t: Throwable): ApiError {
    val error = when (t) {
        is SocketTimeoutException -> ApiError.TimeoutException
        is UnknownHostException -> ApiError.UnknownHostException
        else -> ApiError.NoResponse
    }
    recordApiCallException(request, t)

    return error
}

private fun <T> recordApiCallException(response: Response<T>) {
    CountlyWrapper.recordHandledException(
        ApiException(
            message = """Response unsuccessful:
                            code: ${response.code()},
                            message: ${response.message()},
                            body: ${response.body()?.toString()}
                            error: ${response.errorBody()?.string()}
                            """
        )
    )
}

private fun recordApiCallException(request: Request, t: Throwable?) {
    CountlyWrapper.recordHandledException(
        ApiException(
            message = """Request unsuccessful:
                            request: ${request.url},
                            body: ${request.getBodyAsString()}
                            """,
            cause = t
        )
    )
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
