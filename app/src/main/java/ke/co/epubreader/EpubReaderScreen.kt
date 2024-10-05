package ke.co.epubreader


import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.clickable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity

import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubDefaults
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.mediatype.MediaType


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubReaderScreen(viewModel: EpubViewModel, epubUrl: String, activity: FragmentActivity) {
    val context = LocalContext.current
    val navigatorTag = "EpubNavigatorFragment"
    val isBookLoaded by viewModel::isBookLoaded

    val tableOfContents by viewModel.tableOfContents.collectAsState()
    var isTocDialogOpen by remember { mutableStateOf(false) }
    val bookTitle by viewModel.bookTitle.collectAsState()


    // This will hold our fragment reference
    var fragment by remember { mutableStateOf<EpubNavigatorFragment?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadBook(urlString = epubUrl, context = context)
    }

    // Handle locator collection in a Composable-friendly way
    LaunchedEffect(fragment) {
        fragment?.let { safeFragment ->
            if (safeFragment.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                safeFragment.currentLocator
                    .onEach { locator ->
                        viewModel.saveReadingProgression(locator)
                    }
                    .launchIn(this)
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(bookTitle) },
                actions = {
                    IconButton(onClick = { isTocDialogOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.List, // Use a suitable TOC icon
                            contentDescription = "Table of Contents"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!isBookLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {

                Text(text = "loading .......")
                //    CircularProgressIndicator()
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        id = View.generateViewId()
                    }
                },
                update = { frameLayout ->
                    if (frameLayout.childCount == 0) {
                        viewModel.publication?.let { publication ->
                            val initialLocator = viewModel.initialLocator

                            val factory = EpubNavigatorFactory(
                                publication = publication,
                                configuration = EpubNavigatorFactory.Configuration(
                                    defaults = EpubDefaults(
                                        pageMargins = 1.5,
                                        scroll = true
                                    )
                                )
                            )

                            val fragmentFactory = factory.createFragmentFactory(
                                initialLocator = initialLocator,
                                readingOrder = publication.readingOrder,
                                initialPreferences = EpubPreferences(),
                                listener = viewModel,
                                paginationListener = null,
                                configuration = EpubNavigatorFragment.Configuration()
                            )

                            val newFragment = fragmentFactory.instantiate(
                                fragmentFactory.javaClass.classLoader!!,
                                EpubNavigatorFragment::class.java.name
                            ) as EpubNavigatorFragment

                            activity.supportFragmentManager.commit {
                                replace(frameLayout.id, newFragment, navigatorTag)
                            }

                            // Update our fragment reference
                            fragment = newFragment
                            viewModel.setNavigator(fragment!!)

                        }
                    }
                },
                modifier = Modifier.fillMaxSize().padding(top = 20.dp)
            )
        }

        val coroutineScope = rememberCoroutineScope()
        // TOC Dialog
        if (isTocDialogOpen) {
            if (tableOfContents.isNotEmpty()) {
                TOCDialog(
                    tableOfContents = tableOfContents,
                    onDismiss = { isTocDialogOpen = false },
                    onItemClick = { link ->
                        coroutineScope.launch {
                            navigateToLink(viewModel, link)
                            isTocDialogOpen = false
                        }
                    }
                )
            } else {
                AlertDialog(
                    onDismissRequest = { isTocDialogOpen = false },
                    title = { Text("Table of Contents") },
                    text = { Text("No Table of Contents available.") },
                    confirmButton = {
                        TextButton(onClick = { isTocDialogOpen = false }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}


    @Composable
    fun TOCDialog(
        tableOfContents: List<Link>,
        onDismiss: () -> Unit,
        onItemClick: (Link) -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Table of Contents") },
            text = {
                LazyColumn {
                    items(tableOfContents) { link ->
                        TOCItem(link = link, onClick = { onItemClick(link) })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        )
    }

    @Composable
    fun TOCItem(link: Link, onClick: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = link.title ?: "Untitled",
                style = MaterialTheme.typography.bodyLarge
            )
            // Optionally, display nested TOC items if available
            if (link.children.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                // Recursive approach for nested TOC items
                link.children.forEach { childLink ->
                    TOCItem(link = childLink, onClick = { onClick() })
                }
            }
        }
    }

suspend fun navigateToLink(viewModel: EpubViewModel, link: Link) {
    viewModel.publication?.let { publication ->
        // Resolve the href to a valid String URL


        // Convert mediaType to string, ensuring it's not null or empty
        val mediaType = link.mediaType?.let { MediaType(it.toString()) } ?: MediaType("application/xhtml+xml")

        // Create Locator, ensuring it has valid href and mediaType
        val locator = mediaType?.let {
            Locator(
                href = link.href.resolve(), // Use the resolved href
                mediaType = it, // Use the converted mediaType
                title = link.title, // Use the link title if available
                locations = Locator.Locations(), // Default empty Locations
                text = Locator.Text() // Default empty Text
            )
        }

        // Navigate to the locator
        locator?.let { viewModel.navigator?.go(it) } ?: run {
            Log.e("EpubViewModel", "Navigator is not initialized when trying to navigate.")
        }

        // Save the reading progression for the locator
        if (locator != null) {
            viewModel.saveReadingProgression(locator)
        }
    } ?: run {
        Log.e("EpubViewModel", "Publication is not loaded.")
    }
}

