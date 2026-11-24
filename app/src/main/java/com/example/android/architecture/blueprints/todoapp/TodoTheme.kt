// Archivo principal: Configuración de tema Material 3 inspirada en Ratchet & Clank
package com.example.android.architecture.blueprints.todoapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Bloque clave: Paleta de colores inspirada en Ratchet (naranja) y Clank (cian/acero espacial)
private val RatchetClankDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF6D00),          // Naranja armadura Lombax
    onPrimary = Color(0xFF120900),
    primaryContainer = Color(0xFF5A2400),
    onPrimaryContainer = Color(0xFFFFDBBE),
    secondary = Color(0xFF00E5FF),        // Cian tecnológico de Clank
    onSecondary = Color(0xFF001F24),
    secondaryContainer = Color(0xFF004F58),
    onSecondaryContainer = Color(0xFFA7F2FF),
    background = Color(0xFF0E1320),       // Fondo espacial oscuro (evita el blanco plano)
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF151C2E),          // Superficies de tarjetas, barras y cajón lateral
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF232D47),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF64748B)
)

// Bloque clave: Aplica el esquema de colores Ratchet & Clank a toda la jerarquía Compose
@Composable
fun TodoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RatchetClankDarkColorScheme,
        content = content
    )
}