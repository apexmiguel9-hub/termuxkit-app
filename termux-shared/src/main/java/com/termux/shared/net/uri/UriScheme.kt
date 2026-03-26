package com.termux.shared.net.uri

import android.net.Uri

/**
 * The [Uri] schemes.
 *
 * https://www.iana.org/assignments/uri-schemes/uri-schemes.xhtml
 * https://en.wikipedia.org/wiki/List_of_URI_schemes
 */
object UriScheme {

    /** Android app resource. */
    @JvmField
    val SCHEME_ANDROID_RESOURCE: String = "android.resource"

    /** Android content provider. https://www.iana.org/assignments/uri-schemes/prov/content. */
    @JvmField
    val SCHEME_CONTENT: String = "content"

    /** Filesystem or android app asset. https://www.rfc-editor.org/rfc/rfc8089.html. */
    @JvmField
    val SCHEME_FILE: String = "file"

    /** Hypertext Transfer Protocol. */
    @JvmField
    val SCHEME_HTTP: String = "http"

    /** Hypertext Transfer Protocol Secure. */
    @JvmField
    val SCHEME_HTTPS: String = "https"
}
