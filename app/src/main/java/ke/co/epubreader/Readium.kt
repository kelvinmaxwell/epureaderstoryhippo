package ke.co.epubreader
import android.content.Context
import android.view.View
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.lcp.LcpError
import org.readium.r2.lcp.LcpService
import org.readium.r2.lcp.auth.LcpDialogAuthentication

import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.DebugError
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

class Readium(context: Context) {

    val httpClient =
        DefaultHttpClient()

    val assetRetriever =
        AssetRetriever(context.contentResolver, httpClient)

    /**
     * The LCP service decrypts LCP-protected publication and acquire publications from a
     * license file.
     */
    val lcpService = LcpService(
        context,
        assetRetriever
    )?.let { Try.success(it) }
        ?: Try.failure(LcpError.Unknown(DebugError("liblcp is missing on the classpath")))

    private val lcpDialogAuthentication = LcpDialogAuthentication()

    private val contentProtections = listOfNotNull(
        lcpService.getOrNull()?.contentProtection(lcpDialogAuthentication)
    )

    /**
     * The PublicationFactory is used to open publications.
     */
    val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context,
            assetRetriever = assetRetriever,
            httpClient = httpClient,
            pdfFactory = null
        ),
        contentProtections = contentProtections
    )


}

@OptIn(ExperimentalReadiumApi::class)
val FontFamily.Companion.LITERATA: FontFamily get() = FontFamily("Literata")