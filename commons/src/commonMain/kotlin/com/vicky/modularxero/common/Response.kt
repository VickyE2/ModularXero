package com.vicky.modularxero.common

import com.vicky.modularxero.common.values.EmptyMessageValue
import com.vicky.modularxero.common.values.MapValue
import com.vicky.modularxero.common.values.MessageValue
import com.vicky.modularxero.common.values.StringValue
import kotlinx.serialization.Contextual
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

interface HandleableServerMessage

@Serializable
enum class MessageType : HandleableServerMessage {
    LOGIN,
    SIGNUP,
    REQUEST_DATA,
    VERIFY,
    RE_VERIFY,
    FORGOT_PASSWORD,
    REFRESH,
    UPDATE,
    UPLOAD,
    UPLOAD_INIT,
    UPLOAD_END,
    NULL
}

@Serializable
class Response<T : MessageValue> {
    @Contextual
    var type: HandleableServerMessage = MessageType.NULL
    lateinit var payload: T
    var id: String? = null
    var status: ResponseStatus = ResponseStatus.UNKNOWN

    constructor(type: HandleableServerMessage, payload: T, status: ResponseStatus) {
        this.type = type
        this.payload = payload
        this.status = status
    }

    constructor()

    enum class ResponseStatus {
        OK,
        FAILED,
        PENDING,
        UNKNOWN,
        UNAUTHORIZED
    }

    companion object {
        fun error(string: String): Response<MapValue> {
            val resp = Response<MapValue>()
            resp.status = ResponseStatus.FAILED
            resp.payload = MapValue(
                mapOf<String, MessageValue>(
                    "reason" to StringValue(string)
                )
            )
            return resp
        }
    }
}
