package com.example.data.maps

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.example.BuildConfig
import com.example.domain.engine.DeliveryFeeEngine
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.coroutines.resume

data class ResolvedAddress(
    val formattedAddress: String,
    val latitude: Double,
    val longitude: Double,
    val isFromDeviceGps: Boolean,
    val isFromMapsApi: Boolean
)

data class RouteCalculationResult(
    val distanceMeters: Long,
    val durationMinutes: Int,
    val sourceLabel: String,
    val usedGoogleRoutesApi: Boolean
)

class MapsService(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(context.applicationContext)
    }

    fun isMapsApiConfigured(): Boolean {
        val key = BuildConfig.MAPS_API_KEY
        return key.isNotBlank() && !key.startsWith("YOUR_")
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentDeviceLocation(): Result<Location> =
        suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }

            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        cont.resume(Result.success(location))
                    } else {
                        fusedClient.lastLocation
                            .addOnSuccessListener { last ->
                                if (last != null) {
                                    cont.resume(Result.success(last))
                                } else {
                                    cont.resume(
                                        Result.failure(
                                            IllegalStateException(
                                                "GPS fix unavailable right now. Enter your address manually."
                                            )
                                        )
                                    )
                                }
                            }
                            .addOnFailureListener { err ->
                                cont.resume(Result.failure(err))
                            }
                    }
                }
                .addOnFailureListener { err ->
                    cont.resume(Result.failure(err))
                }
        }

    suspend fun reverseGeocode(lat: Double, lng: Double): ResolvedAddress =
        withContext(Dispatchers.IO) {
            if (!isMapsApiConfigured()) {
                return@withContext ResolvedAddress(
                    formattedAddress = "GPS Pin (${String.format("%.4f", lat)}, ${String.format("%.4f", lng)})",
                    latitude = lat,
                    longitude = lng,
                    isFromDeviceGps = true,
                    isFromMapsApi = false
                )
            }

            try {
                val url =
                    "https://maps.googleapis.com/maps/api/geocode/json?latlng=$lat,$lng&key=${BuildConfig.MAPS_API_KEY}"
                val request = Request.Builder().url(url).get().build()
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val json = JSONObject(body)
                        val results = json.optJSONArray("results")
                        val formatted = results?.optJSONObject(0)?.optString("formatted_address")
                        if (!formatted.isNullOrBlank()) {
                            return@withContext ResolvedAddress(
                                formattedAddress = formatted,
                                latitude = lat,
                                longitude = lng,
                                isFromDeviceGps = true,
                                isFromMapsApi = true
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MapsService", "Reverse geocoding failed, using GPS coordinates", e)
            }

            ResolvedAddress(
                formattedAddress = "GPS Pin (${String.format("%.4f", lat)}, ${String.format("%.4f", lng)})",
                latitude = lat,
                longitude = lng,
                isFromDeviceGps = true,
                isFromMapsApi = false
            )
        }

    suspend fun geocodeManualAddress(addressQuery: String): Result<ResolvedAddress> =
        withContext(Dispatchers.IO) {
            val trimmed = addressQuery.trim()
            if (trimmed.length < 3) {
                return@withContext Result.failure(
                    IllegalArgumentException("Enter at least 3 characters for the delivery address.")
                )
            }
            if (!isMapsApiConfigured()) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "Google Maps API key (MAPS_API_KEY) is not configured in Secrets. " +
                            "Use manual distance entry or configure MAPS_API_KEY for address lookup."
                    )
                )
            }

            try {
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                val url =
                    "https://maps.googleapis.com/maps/api/geocode/json?address=$encoded&key=${BuildConfig.MAPS_API_KEY}"
                val request = Request.Builder().url(url).get().build()
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("Geocoding HTTP ${response.code}")
                        )
                    }
                    val json = JSONObject(body)
                    val status = json.optString("status")
                    val first = json.optJSONArray("results")?.optJSONObject(0)
                    if (status == "OK" && first != null) {
                        val loc = first.getJSONObject("geometry").getJSONObject("location")
                        val formatted = first.optString("formatted_address", trimmed)
                        return@withContext Result.success(
                            ResolvedAddress(
                                formattedAddress = formatted,
                                latitude = loc.getDouble("lat"),
                                longitude = loc.getDouble("lng"),
                                isFromDeviceGps = false,
                                isFromMapsApi = true
                            )
                        )
                    }
                    return@withContext Result.failure(
                        IllegalStateException("Address lookup returned $status")
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun computeRouteDistance(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double
    ): RouteCalculationResult = withContext(Dispatchers.IO) {
        if (isMapsApiConfigured()) {
            try {
                val payload = JSONObject().apply {
                    put(
                        "origin",
                        JSONObject().put(
                            "location",
                            JSONObject().put(
                                "latLng",
                                JSONObject().put("latitude", originLat).put("longitude", originLng)
                            )
                        )
                    )
                    put(
                        "destination",
                        JSONObject().put(
                            "location",
                            JSONObject().put(
                                "latLng",
                                JSONObject().put("latitude", destLat).put("longitude", destLng)
                            )
                        )
                    )
                    put("travelMode", "TWO_WHEELER")
                }
                val request = Request.Builder()
                    .url("https://routes.googleapis.com/directions/v2:computeRoutes")
                    .addHeader("X-Goog-Api-Key", BuildConfig.MAPS_API_KEY)
                    .addHeader("X-Goog-FieldMask", "routes.distanceMeters,routes.duration")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val routes = JSONObject(body).optJSONArray("routes")
                        val first = routes?.optJSONObject(0)
                        if (first != null && first.has("distanceMeters")) {
                            val meters = first.getLong("distanceMeters")
                            val durSec = first.optString("duration", "900s")
                                .removeSuffix("s")
                                .toLongOrNull() ?: 900L
                            val mins = (durSec / 60L).toInt().coerceAtLeast(5)
                            return@withContext RouteCalculationResult(
                                distanceMeters = meters,
                                durationMinutes = mins,
                                sourceLabel = "Google Routes API (2-Wheeler)",
                                usedGoogleRoutesApi = true
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MapsService", "Google Routes API failed, falling back to Haversine", e)
            }
        }

        val haversineMeters = DeliveryFeeEngine.calculateHaversineMeters(
            originLat, originLng, destLat, destLng
        ).coerceAtLeast(250L)
        val estMins = (8 + (haversineMeters / 250.0)).toInt().coerceIn(8, 70)
        RouteCalculationResult(
            distanceMeters = haversineMeters,
            durationMinutes = estMins,
            sourceLabel = if (isMapsApiConfigured()) {
                "Direct Haversine Fallback"
            } else {
                "Haversine Distance (Set MAPS_API_KEY in Secrets for live Routes)"
            },
            usedGoogleRoutesApi = false
        )
    }
}
