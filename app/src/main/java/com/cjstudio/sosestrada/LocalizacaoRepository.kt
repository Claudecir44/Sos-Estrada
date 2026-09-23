package com.cjstudio.sosestrada

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalizacaoRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : ILocalizacaoRepository {

    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(context) }

    @Suppress("DEPRECATION")
    private val geocoder by lazy { Geocoder(context, Locale.getDefault()) }

    // A permissão é pedida pela tela antes de chamar; sem ela o
    // SecurityException vira Result.failure.
    @SuppressLint("MissingPermission")
    override suspend fun ultimaLocalizacao(): Result<Location?> = runCatching {
        fusedLocationClient.lastLocation.await()
    }

    // O Geocoder faz rede e bloqueia a thread — sempre fora da thread principal.
    override suspend fun enderecoDe(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()?.getAddressLine(0)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun distanciaKmAte(latitude: Double, longitude: Double, endereco: String): Double? =
        withContext(Dispatchers.IO) {
            try {
                // Os cadastros costumam não ter o país; sem ele o geocoder erra de cidade.
                val consulta = if (endereco.lowercase().contains("brasil")) endereco else "$endereco, Brasil"
                @Suppress("DEPRECATION")
                val destino = geocoder.getFromLocationName(consulta, 1)?.firstOrNull() ?: return@withContext null
                val resultado = FloatArray(1)
                Location.distanceBetween(latitude, longitude, destino.latitude, destino.longitude, resultado)
                resultado[0] / 1000.0
            } catch (e: Exception) {
                null
            }
        }
}
