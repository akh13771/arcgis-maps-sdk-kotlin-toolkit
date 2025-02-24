/*
 *
 *
 *  Copyright 2024 Esri
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  
 */

package com.arcgismaps.toolkit.popupapp

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import coil3.Bitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.compose.AsyncImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.toBitmap
import coil3.util.DebugLogger
import com.arcgismaps.ArcGISEnvironment
import com.arcgismaps.httpcore.authentication.ArcGISAuthenticationChallenge
import com.arcgismaps.httpcore.authentication.ArcGISAuthenticationChallengeHandler
import com.arcgismaps.httpcore.authentication.ArcGISAuthenticationChallengeResponse
import com.arcgismaps.httpcore.authentication.TokenCredential
import com.arcgismaps.toolkit.popupapp.screens.mapscreen.MainScreen
import com.arcgismaps.toolkit.popupapp.screens.mapscreen.MapViewModel
import com.arcgismaps.toolkit.popupapp.ui.theme.PopupAppTheme
import kotlinx.coroutines.delay

var imgLoader: ImageLoader? = null
var bmap: Bitmap? = null


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel: MapViewModel by viewModels { MapViewModel.Factory }
        ArcGISEnvironment.authenticationManager.arcGISAuthenticationChallengeHandler =
            TestArcGISAuthenticationChallengeHandler(
                BuildConfig.webMapUser,
                BuildConfig.webMapPassword
            )

        val symbolWithId = if (bmap == null) {
            assets.open("img.png").use {
                println("IMGG decoding bitmap")
                bmap = BitmapFactory.decodeStream(it)
                SymbolWithId(bmap!!, "foo")
            }
        } else {
            SymbolWithId(bmap!!, "foo")
        }
        // show the composable map using the mapViewModel


        if (imgLoader == null) {
            imgLoader = ImageLoader.Builder(this.applicationContext)
                .components {
                    add(SymbolImageFetcher.Factory())
                }
                .networkCachePolicy(CachePolicy.DISABLED)
                .logger(DebugLogger())
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(this.applicationContext, 0.25)
                        .strongReferencesEnabled(true)
                        .build()
                }
                .build()
        }
        setContent {
            PopupAppTheme {
                println("IMGG SYMBOLWITH ID $symbolWithId")
                AsyncSymbol(symbolWithId)
            }
        }
    }
}

internal data class SymbolWithId(val symbol: Bitmap, val id: String) : ImageSource.Metadata() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SymbolWithId

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

internal class SymbolImageFetcher(
    private val img: Bitmap,
    private val context: Context
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val res = ImageFetchResult(
            image = img.asImage(true),
            dataSource = DataSource.MEMORY,
            isSampled = false
        )
        println("IMGG Fetching FETCH RESULTS $res")
        delay(3_000)
        return res
    }

    class Factory : Fetcher.Factory<SymbolWithId> {
        override fun create(
            data: SymbolWithId,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher =
            SymbolImageFetcher(data.symbol, options.context).also {
                println("IMGG CREATING FETCHER")
            }

    }
}

internal suspend fun getImage(symbol: SymbolWithId, context: Context): Bitmap {
    val cacheKey = symbol.hashCode().toString()
    val request = ImageRequest.Builder(context)
        .diskCachePolicy(CachePolicy.ENABLED)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .data(symbol)
        .memoryCacheKey(cacheKey)
        .build()

    val result = imgLoader?.execute(request)
    if (result is ErrorResult) {
        throw result.throwable
    } else {
        println("IMGG Image successfully cached with key: $cacheKey")
        return result!!



            .image!!.toBitmap(500, 500)
    }
}

@Composable
internal fun AsyncSymbol(symbolWithId: SymbolWithId) {
    AsyncImage(
        model = symbolWithId,
        placeholder = rememberVectorPainter(Icons.Rounded.Phone),
        contentDescription = null,
        imageLoader = imgLoader!!
    )
}

@Composable
internal fun Symbol(symbolWithId: SymbolWithId) {
    val context = LocalContext.current
    var drawable: Bitmap? by remember { mutableStateOf(null) }
    LaunchedEffect(Unit) {
        drawable = getImage(symbolWithId, context)
    }
    if (drawable == null) {
        println("IMGG: displaying vector")
        Image(
            painter = rememberVectorPainter(Icons.Rounded.Phone),
            contentDescription = null
        )
    } else {
        println("IMGG: displaying bitmap $drawable")
        Image(
            drawable!!.asImageBitmap(),
            contentDescription = null,
        )
    }
}


@Composable
fun PopupApp(viewModel: MapViewModel) {
    MainScreen(viewModel)
}

class TestArcGISAuthenticationChallengeHandler(
    private val username: String,
    private val password: String
) : ArcGISAuthenticationChallengeHandler {
    override suspend fun handleArcGISAuthenticationChallenge(
        challenge: ArcGISAuthenticationChallenge
    ): ArcGISAuthenticationChallengeResponse {
        val result: Result<TokenCredential> =
            TokenCredential.create(
                challenge.requestUrl,
                username,
                password,
                tokenExpirationInterval = 0
            )
        return result.let {
            if (it.isSuccess) {
                ArcGISAuthenticationChallengeResponse.ContinueWithCredential(it.getOrThrow())
            } else {
                ArcGISAuthenticationChallengeResponse.ContinueAndFailWithError(it.exceptionOrNull()!!)
            }
        }
    }
}
