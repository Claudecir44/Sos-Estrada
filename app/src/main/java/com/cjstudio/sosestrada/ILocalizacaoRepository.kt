package com.cjstudio.sosestrada

import android.location.Location

// GPS do aparelho + geocodificação (endereço <-> coordenadas).
interface ILocalizacaoRepository {
    // Última localização conhecida; null sem permissão ou com o GPS desligado.
    suspend fun ultimaLocalizacao(): Result<Location?>

    // Endereço legível das coordenadas; null se o geocoder não achar.
    suspend fun enderecoDe(latitude: Double, longitude: Double): String?

    // Distância em km até um endereço em texto; null se não der pra localizar.
    suspend fun distanciaKmAte(latitude: Double, longitude: Double, endereco: String): Double?
}
