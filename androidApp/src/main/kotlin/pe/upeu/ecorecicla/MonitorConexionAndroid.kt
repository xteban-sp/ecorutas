package pe.upeu.ecorecicla

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import pe.upeu.ecorecicla.domain.repository.MonitorConexion

/** Emite true/false cada vez que el dispositivo gana o pierde una red con Internet. */
class MonitorConexionAndroid(contexto: Context) : MonitorConexion {

    private val gestor = contexto.getSystemService(ConnectivityManager::class.java)

    override val conectado: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(hayRed())
            }
        }
        trySend(hayRed())
        gestor.registerDefaultNetworkCallback(callback)
        awaitClose { gestor.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun hayRed(): Boolean =
        gestor.getNetworkCapabilities(gestor.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
