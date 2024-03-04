package de.telekom.dtagsyncpluskit.utils

import android.net.DnsResolver
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import org.xbill.DNS.EDNSOption
import org.xbill.DNS.Message
import org.xbill.DNS.Resolver
import org.xbill.DNS.ResolverListener
import org.xbill.DNS.TSIG
import java.io.IOException
import java.time.Duration

/**
 * dnsjava Resolver that uses Android's [DnsResolver] API, which is available since Android 10.
 */
@RequiresApi(Build.VERSION_CODES.Q)
object Android10Resolver : Resolver {

    private val executor = Dispatchers.IO.asExecutor()
    private val resolver = DnsResolver.getInstance()

    override fun send(query: Message): Message = runBlocking {
        val future = CompletableDeferred<Message>()

        resolver.rawQuery(
            null,
            query.toWire(),
            DnsResolver.FLAG_EMPTY,
            executor,
            null,
            object : DnsResolver.Callback<ByteArray> {
                override fun onAnswer(rawAnswer: ByteArray, rcode: Int) {
                    future.complete(Message((rawAnswer)))
                }

                override fun onError(error: DnsResolver.DnsException) {
                    // wrap into IOException as expected by dnsjava
                    future.completeExceptionally(IOException(error))
                }
            })

        future.await()
    }

    override fun sendAsync(query: Message, listener: ResolverListener) =
        // currently not used by dnsjava, so no need to implement it
        throw NotImplementedError()


    override fun setPort(port: Int) {
        // not applicable
    }

    override fun setTCP(flag: Boolean) {
        // not applicable
    }

    override fun setIgnoreTruncation(flag: Boolean) {
        // not applicable
    }

    override fun setEDNS(level: Int) {
        // not applicable
    }

    override fun setEDNS(
        version: Int,
        payloadSize: Int,
        flags: Int,
        options: MutableList<EDNSOption>?
    ) {
        // not applicable
    }

    override fun setTSIGKey(key: TSIG?) {
        // not applicable
    }

    override fun setTimeout(secs: Int, msecs: Int) {
        // not applicable
    }

    override fun setTimeout(secs: Int) {
        // not applicable
    }

    override fun setTimeout(timeout: Duration?) {
        // not applicable
    }
}