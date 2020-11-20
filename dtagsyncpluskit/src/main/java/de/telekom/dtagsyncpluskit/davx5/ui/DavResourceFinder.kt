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

package de.telekom.dtagsyncpluskit.davx5.ui

import android.app.Application
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.UrlUtils
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.property.*
import de.telekom.dtagsyncpluskit.api.BearerAuthInterceptor
import de.telekom.dtagsyncpluskit.davx5.DavUtils
import de.telekom.dtagsyncpluskit.davx5.HttpClient
import de.telekom.dtagsyncpluskit.davx5.log.StringHandler
import de.telekom.dtagsyncpluskit.davx5.model.Collection
import de.telekom.dtagsyncpluskit.davx5.model.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.apache.commons.lang3.builder.ReflectionToStringBuilder
import org.xbill.DNS.Lookup
import org.xbill.DNS.Type
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class DavResourceFinder(
    app: Application,
    private val credentials: Credentials
) : AutoCloseable {

    private val context = app.applicationContext

    enum class Service(val wellKnownName: String) {
        CALDAV("caldav"),
        CARDDAV("carddav");

        override fun toString() = wellKnownName
    }

    val log = Logger.getLogger("syncplus.DavResourceFinder")
    private val logBuffer = StringHandler()

    init {
        log.level = Level.FINEST
        log.addHandler(logBuffer)
    }

    private val httpClient: HttpClient = HttpClient.Builder(app, logger = log)
        .addAuthentication(BearerAuthInterceptor(app, credentials))
        .withUnauthorizedCallback {
            // TODO
        }
        .setForeground(true)
        .build()

    override fun close() {
        httpClient.close()
    }


    /**
     * Finds the initial configuration, i.e. runs the auto-detection process. Must not throw an
     * exception, but return an empty Configuration with error logs instead.
     */
    fun findInitialConfiguration(): Configuration {
        var cardDavConfig: Configuration.ServiceInfo? = null
        var calDavConfig: Configuration.ServiceInfo? = null

        try {
            try {
                cardDavConfig = findInitialConfiguration(Service.CARDDAV)
            } catch (e: Exception) {
                log.log(Level.INFO, "CardDAV service detection failed", e)
                rethrowIfInterrupted(e)
            }

            try {
                calDavConfig = findInitialConfiguration(Service.CALDAV)
            } catch (e: Exception) {
                log.log(Level.INFO, "CalDAV service detection failed", e)
                rethrowIfInterrupted(e)
            }
        } catch (e: Exception) {
            // we have been interrupted; reset results so that an error message will be shown
            cardDavConfig = null
            calDavConfig = null
        }

        return Configuration(
            cardDavConfig, calDavConfig,
            logBuffer.toString()
        )
    }

    private fun findInitialConfiguration(service: Service): Configuration.ServiceInfo? {
        // user-given base URI (either mailto: URI or http(s):// URL)
        val baseURI = URI.create(credentials.spicaEnv.baseUrl)

        // domain for service discovery
        var discoveryFQDN: String? = null

        // put discovered information here
        val config = Configuration.ServiceInfo()
        log.info("Finding initial ${service.wellKnownName} service configuration")

        if (baseURI.scheme.equals("http", true) || baseURI.scheme.equals("https", true)) {
            baseURI.toHttpUrlOrNull()?.let { baseURL ->
                // remember domain for service discovery
                // try service discovery only for https:// URLs because only secure service discovery is implemented
                if (baseURL.scheme.equals("https", true))
                    discoveryFQDN = baseURL.host

                checkUserGivenURL(baseURL, service, config)

                if (config.principal == null)
                    try {
                        config.principal = getCurrentUserPrincipal(
                            baseURL.resolve("/.well-known/" + service.wellKnownName)!!,
                            service
                        )
                    } catch (e: Exception) {
                        log.log(Level.FINE, "Well-known URL detection failed", e)
                        rethrowIfInterrupted(e)
                    }
            }
        } else if (baseURI.scheme.equals("mailto", true)) {
            val mailbox = baseURI.schemeSpecificPart

            val posAt = mailbox.lastIndexOf("@")
            if (posAt != -1)
                discoveryFQDN = mailbox.substring(posAt + 1)
        }

        // Step 2: If user-given URL didn't reveal a principal, search for it: SERVICE DISCOVERY
        if (config.principal == null)
            discoveryFQDN?.let {
                log.info("No principal found at user-given URL, trying to discover")
                try {
                    config.principal = discoverPrincipalUrl(it, service)
                } catch (e: Exception) {
                    log.log(Level.FINE, "$service service discovery failed", e)
                    rethrowIfInterrupted(e)
                }
            }

        if (config.principal != null && service == Service.CALDAV)
        // query email address (CalDAV scheduling: calendar-user-address-set)
            try {
                DavResource(httpClient.okHttpClient, config.principal!!, log).propfind(
                    0,
                    CalendarUserAddressSet.NAME
                ) { response, _ ->
                    response[CalendarUserAddressSet::class.java]?.let { addressSet ->
                        for (href in addressSet.hrefs)
                            try {
                                val uri = URI(href)
                                if (uri.scheme.equals("mailto", true))
                                    config.email = uri.schemeSpecificPart
                            } catch (e: URISyntaxException) {
                                log.log(Level.WARNING, "Couldn't parse user address", e)
                            }
                    }
                }
            } catch (e: Exception) {
                log.log(Level.WARNING, "Couldn't query user email address", e)
                rethrowIfInterrupted(e)
            }

        // return config or null if config doesn't contain useful information
        val serviceAvailable =
            config.principal != null || config.homeSets.isNotEmpty() || config.collections.isNotEmpty()
        return if (serviceAvailable)
            config
        else
            null
    }

    private fun checkUserGivenURL(
        baseURL: HttpUrl,
        service: Service,
        config: Configuration.ServiceInfo
    ) {
        log.info("Checking user-given URL: $baseURL")

        val davBase = DavResource(httpClient.okHttpClient, baseURL, log)
        try {
            when (service) {
                Service.CARDDAV -> {
                    davBase.propfind(
                        0,
                        ResourceType.NAME, DisplayName.NAME, AddressbookDescription.NAME,
                        AddressbookHomeSet.NAME,
                        CurrentUserPrincipal.NAME
                    ) { response, _ ->
                        scanCardDavResponse(response, config)
                    }
                }
                Service.CALDAV -> {
                    davBase.propfind(
                        0,
                        ResourceType.NAME,
                        DisplayName.NAME,
                        CalendarColor.NAME,
                        CalendarDescription.NAME,
                        CalendarTimezone.NAME,
                        CurrentUserPrivilegeSet.NAME,
                        SupportedCalendarComponentSet.NAME,
                        CalendarHomeSet.NAME,
                        CurrentUserPrincipal.NAME
                    ) { response, _ ->
                        scanCalDavResponse(response, config)
                    }
                }
            }
        } catch (e: Exception) {
            log.log(Level.FINE, "PROPFIND/OPTIONS on user-given URL failed", e)
            rethrowIfInterrupted(e)
        }
    }

    /**
     * If [dav] references an address book, an address book home set, and/or a princiapl,
     * it will added to, config.collections, config.homesets and/or config.principal.
     * URLs will be stored with trailing "/".
     *
     * @param dav       response whose properties are evaluated
     * @param config    structure where the results are stored into
     */
    fun scanCardDavResponse(dav: Response, config: Configuration.ServiceInfo) {
        var principal: HttpUrl? = null

        // check for current-user-principal
        dav[CurrentUserPrincipal::class.java]?.href?.let {
            principal = dav.requestedUrl.resolve(it)
        }

        // Is it an address book and/or principal?
        dav[ResourceType::class.java]?.let {
            if (it.types.contains(ResourceType.ADDRESSBOOK)) {
                val info = Collection.fromDavResponse(dav)!!
                log.info("Found address book at ${info.url}")
                config.collections[info.url] = info
            }

            if (it.types.contains(ResourceType.PRINCIPAL))
                principal = dav.href
        }

        // Is it an addressbook-home-set?
        dav[AddressbookHomeSet::class.java]?.let { homeSet ->
            for (href in homeSet.hrefs) {
                dav.requestedUrl.resolve(href)?.let {
                    val location = UrlUtils.withTrailingSlash(it)
                    log.info("Found address book home-set at $location")
                    config.homeSets += location
                }
            }
        }

        principal?.let {
            if (providesService(it, Service.CARDDAV))
                config.principal = principal
        }
    }

    /**
     * If [dav] references an address book, an address book home set, and/or a princiapl,
     * it will added to, config.collections, config.homesets and/or config.principal.
     * URLs will be stored with trailing "/".
     *
     * @param dav       response whose properties are evaluated
     * @param config    structure where the results are stored into
     */
    private fun scanCalDavResponse(dav: Response, config: Configuration.ServiceInfo) {
        var principal: HttpUrl? = null

        // check for current-user-principal
        dav[CurrentUserPrincipal::class.java]?.href?.let {
            principal = dav.requestedUrl.resolve(it)
        }

        // Is it a calendar book and/or principal?
        dav[ResourceType::class.java]?.let {
            if (it.types.contains(ResourceType.CALENDAR)) {
                val info = Collection.fromDavResponse(dav)!!
                log.info("Found calendar at ${info.url}")
                config.collections[info.url] = info
            }

            if (it.types.contains(ResourceType.PRINCIPAL))
                principal = dav.href
        }

        // Is it an calendar-home-set?
        dav[CalendarHomeSet::class.java]?.let { homeSet ->
            for (href in homeSet.hrefs) {
                dav.requestedUrl.resolve(href)?.let {
                    val location = UrlUtils.withTrailingSlash(it)
                    log.info("Found calendar book home-set at $location")
                    config.homeSets += location
                }
            }
        }

        principal?.let {
            if (providesService(it, Service.CALDAV))
                config.principal = principal
        }
    }


    @Throws(IOException::class)
    fun providesService(url: HttpUrl, service: Service): Boolean {
        var provided = false
        try {
            DavResource(httpClient.okHttpClient, url, log).options { capabilities, _ ->
                if ((service == Service.CARDDAV && capabilities.contains("addressbook")) ||
                    (service == Service.CALDAV && capabilities.contains("calendar-access"))
                )
                    provided = true
            }
        } catch (e: Exception) {
            log.log(Level.SEVERE, "Couldn't detect services on $url", e)
            if (e !is HttpException && e !is DavException)
                throw e
        }
        return provided
    }


    /**
     * Try to find the principal URL by performing service discovery on a given domain name.
     * Only secure services (caldavs, carddavs) will be discovered!
     * @param domain         domain name, e.g. "icloud.com"
     * @param service        service to discover (CALDAV or CARDDAV)
     * @return principal URL, or null if none found
     */
    @Throws(IOException::class, HttpException::class, DavException::class)
    private fun discoverPrincipalUrl(domain: String, service: Service): HttpUrl? {
        val scheme: String
        val fqdn: String
        var port = 443
        val paths = LinkedList<String>()     // there may be multiple paths to try

        val query = "_${service.wellKnownName}s._tcp.$domain"
        log.fine("Looking up SRV records for $query")
        val srvLookup = Lookup(query, Type.SRV)
        DavUtils.prepareLookup(context, srvLookup)
        val srv = DavUtils.selectSRVRecord(srvLookup.run())
        if (srv != null) {
            // choose SRV record to use (query may return multiple SRV records)
            scheme = "https"
            fqdn = srv.target.toString(true)
            port = srv.port
            log.info("Found $service service at https://$fqdn:$port")
        } else {
            // no SRV records, try domain name as FQDN
            log.info("Didn't find $service service, trying at https://$domain:$port")

            scheme = "https"
            fqdn = domain
        }

        // look for TXT record too (for initial context path)
        val txtLookup = Lookup(query, Type.TXT)
        DavUtils.prepareLookup(context, txtLookup)
        paths.addAll(DavUtils.pathsFromTXTRecords(txtLookup.run()))

        // if there's TXT record and if it it's wrong, try well-known
        paths.add("/.well-known/" + service.wellKnownName)
        // if this fails, too, try "/"
        paths.add("/")

        for (path in paths)
            try {
                val initialContextPath = HttpUrl.Builder()
                    .scheme(scheme)
                    .host(fqdn).port(port)
                    .encodedPath(path)
                    .build()

                log.info("Trying to determine principal from initial context path=$initialContextPath")
                val principal = getCurrentUserPrincipal(initialContextPath, service)

                principal?.let { return it }
            } catch (e: Exception) {
                log.log(Level.WARNING, "No resource found", e)
                rethrowIfInterrupted(e)
            }
        return null
    }

    /**
     * Queries a given URL for current-user-principal
     *
     * @param url       URL to query with PROPFIND (Depth: 0)
     * @param service   required service (may be null, in which case no service check is done)
     * @return          current-user-principal URL that provides required service, or null if none
     */
    @Throws(IOException::class, HttpException::class, DavException::class)
    fun getCurrentUserPrincipal(url: HttpUrl, service: Service?): HttpUrl? {
        var principal: HttpUrl? = null
        DavResource(httpClient.okHttpClient, url, log).propfind(
            0,
            CurrentUserPrincipal.NAME
        ) { response, _ ->
            response[CurrentUserPrincipal::class.java]?.href?.let { href ->
                response.requestedUrl.resolve(href)?.let {
                    log.info("Found current-user-principal: $it")

                    // service check
                    if (service != null && !providesService(it, service))
                        log.info("$it doesn't provide required $service service")
                    else
                        principal = it
                }
            }
        }
        return principal
    }

    /**
     * Re-throws the exception if it signals that the current thread was interrupted
     * to stop the current operation.
     */
    private fun rethrowIfInterrupted(e: Exception) {
        if ((e is InterruptedIOException && e !is SocketTimeoutException) || e is InterruptedException)
            throw e
    }


    // data classes

    class Configuration(
        val cardDAV: ServiceInfo?,
        val calDAV: ServiceInfo?,

        val logs: String
    ) {

        data class ServiceInfo(
            var principal: HttpUrl? = null,
            val homeSets: MutableSet<HttpUrl> = HashSet(),
            val collections: MutableMap<HttpUrl, Collection> = HashMap(),

            var email: String? = null
        )

        override fun toString(): String {
            val builder = ReflectionToStringBuilder(this)
            builder.setExcludeFieldNames("logs")
            return builder.toString()
        }

    }

}
