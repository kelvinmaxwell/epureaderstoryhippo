package ke.co.epubreader

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.Asset
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.PublicationOpener
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class EpubViewModel() : ViewModel(), EpubNavigatorFragment.Listener {

    var navigator: EpubNavigatorFragment? = null
        private set

    fun setNavigator(navigatorFragment: EpubNavigatorFragment) {
        navigator = navigatorFragment
    }

    private var publicationTry: Try<Publication, PublicationOpener.OpenError>? = null
    var isBookLoaded by mutableStateOf(false)
        private set

    // Replace publication variable definition with the following
    private var publicationInternal: Publication? = null

    var publication: Publication?
        get() = publicationInternal
        private set(value) {
            publicationInternal = value
        }

    private val _tableOfContents = MutableStateFlow<List<Link>>(emptyList())
    val tableOfContents: StateFlow<List<Link>> = _tableOfContents

    private val _bookTitle = MutableStateFlow<String>("")
    val bookTitle: StateFlow<String> = _bookTitle


    // Initial locator to start reading from the beginning
    val initialLocator: Locator?
        get() = publicationInternal?.let { pub ->
            // Create a Locator for the first resource in the reading order
            val spineItem = pub.readingOrder.firstOrNull()
            spineItem?.let { item ->
                Locator(
                    href = item.href.resolve(),
                    mediaType = item.mediaType.toString()
                        ?.let { MediaType(it) }!!, // Assuming 'type' corresponds to MediaType
                    title = item.title,
                    // Initialize 'locations' and 'text' as needed or use defaults
                    locations = Locator.Locations(),
                    text = Locator.Text()
                )
            }
        }

    init {
        // Optionally, you can initiate loading here or from the UI
    }

    // Download the EPUB file from a URL
    fun downloadEpubFile(context: Context, urlString: String, callback: (File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            val file = File(context.cacheDir, "book.epub")
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            withContext(Dispatchers.Main) {
                callback(file)
            }
        }
    }

    // Load the publication from the downloaded EPUB file
    fun loadBook(urlString: String, context: Context) {
        downloadEpubFile(urlString = urlString, context = context) { file ->
            file?.let {
                val readium = Readium(context)
                viewModelScope.launch(Dispatchers.IO) {

                    // Use the Readium's publicationOpener to open the publication from the file
                    val url = file.toURI().toURL()
                    val asset = readium.assetRetriever.retrieve(file)
                        .getOrElse {
                        }


                    publicationTry =
                        readium.publicationOpener.open(asset as Asset, allowUserInteraction = true)

                    withContext(Dispatchers.Main) {
                        publicationTry?.onSuccess { pub ->
                            // Log the received publication
                            Log.d("EpubViewModel loadBook", "Received publication: $pub")

                            publication = pub

                            // Check if publication is actually null after assignment
                            if (publication != null) {
                                Log.d(
                                    "EpubViewModel loadBook toc",
                                    publication!!.tableOfContents.toString()
                                )

                                publication!!.metadata.title?.let { title ->
                                    _bookTitle.value = title

                                    Log.d("EpubViewModel loadBook", title)
                                }
                            } else {
                                Log.e(
                                    "EpubViewModel loadBook",
                                    "Publication is null after assignment"
                                )
                            }
                            // Extract TOC
                            _tableOfContents.value = publication!!.tableOfContents ?: emptyList()


                            isBookLoaded = true
                        }?.onFailure { error ->
                            Log.d(
                                "EpubViewModel loadBook",
                                "Failed to load publication: ${error.message ?: "Unknown error"}"
                            )
                        }
                    }

                }
            }
        }
    }


    fun saveReadingProgression(locator: Locator) {
        viewModelScope.launch {
            try {
                // Implement your logic to save the reading progression
                Log.d("EpubViewModel", "Saving reading progression: $locator")
                // For example:
                // saveLocatorToPreferences(locator)
                // or
                // saveLocatorToDatabase(locator)
            } catch (e: Exception) {
                Log.e("EpubViewModel", "Failed to save reading progression", e)
            }
        }
    }


    @ExperimentalReadiumApi
    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        TODO("Not yet implemented")
    }

}