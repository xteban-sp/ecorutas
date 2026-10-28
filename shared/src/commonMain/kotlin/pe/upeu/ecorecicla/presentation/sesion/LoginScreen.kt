package pe.upeu.ecorecicla.presentation.sesion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LoginScreen(viewModel: SesionViewModel, aviso: String?) {
    val estado by viewModel.login.collectAsStateWithLifecycle()

    Scaffold { relleno ->
        Column(
            Modifier.fillMaxSize().padding(relleno).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Recycling, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Text("EcoRutas", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Pesajes en ruta · EcoRecicla",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val mensaje = estado.mensaje ?: aviso
            if (mensaje != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                ) {
                    Text(mensaje, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            OutlinedTextField(
                value = estado.usuario,
                onValueChange = viewModel::cambiarUsuario,
                label = { Text("Usuario") },
                singleLine = true,
                isError = estado.errorUsuario != null,
                supportingText = estado.errorUsuario?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                enabled = !estado.cargando,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            )
            OutlinedTextField(
                value = estado.clave,
                onValueChange = viewModel::cambiarClave,
                label = { Text("Contrasena") },
                singleLine = true,
                isError = estado.errorClave != null,
                supportingText = estado.errorClave?.let { { Text(it) } },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.ingresar() }),
                enabled = !estado.cargando,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Button(
                onClick = viewModel::ingresar,
                enabled = !estado.cargando,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(48.dp)
            ) {
                if (estado.cargando) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Ingresar")
                }
            }
        }
    }
}
