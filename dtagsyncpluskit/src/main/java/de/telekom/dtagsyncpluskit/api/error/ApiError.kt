package de.telekom.dtagsyncpluskit.api.error

import retrofit2.Response

sealed interface ApiError {
    class ResponseUnsuccessful(val response: Response<*>) : ApiError

    object ResponseBodyNull : ApiError

    object TimeoutException : ApiError

    object UnknownHostException : ApiError

    object NoResponse : ApiError

    sealed class ContactError(val errorCode: Int) : ApiError {
        object TooManyContacts : ContactError(560)
    }
}
