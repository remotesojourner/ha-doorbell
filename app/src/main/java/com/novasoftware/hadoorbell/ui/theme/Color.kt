package com.novasoftware.hadoorbell.ui.theme

import androidx.compose.ui.graphics.Color

// Tonal palette seeded from the logo's Home Assistant blue (#03A9F4).
val BluePrimaryLight = Color(0xFF00658E)
val BlueOnPrimaryLight = Color(0xFFFFFFFF)
val BluePrimaryContainerLight = Color(0xFFC7E7FF)
val BlueOnPrimaryContainerLight = Color(0xFF001E2E)
val SlateSecondaryLight = Color(0xFF4F616E)
val SlateSecondaryContainerLight = Color(0xFFD2E5F5)
val SlateOnSecondaryContainerLight = Color(0xFF0B1D29)
val VioletTertiaryLight = Color(0xFF63597C)
val VioletTertiaryContainerLight = Color(0xFFE9DDFF)

val BluePrimaryDark = Color(0xFF84CFFF)
val BlueOnPrimaryDark = Color(0xFF00344C)
val BluePrimaryContainerDark = Color(0xFF004C6C)
val BlueOnPrimaryContainerDark = Color(0xFFC7E7FF)
val SlateSecondaryDark = Color(0xFFB6C9D8)
val SlateSecondaryContainerDark = Color(0xFF374955)
val SlateOnSecondaryContainerDark = Color(0xFFD2E5F5)
val VioletTertiaryDark = Color(0xFFCDC0E9)
val VioletTertiaryContainerDark = Color(0xFF4B4263)

/** Fixed colors for controls drawn over the live video, which is dark regardless of the system theme. */
object StreamColors {
    val OnControl = Color.White
    val ControlBackground = Color.White.copy(alpha = 0.2f)
    val Answer = Color(0xFF4CAF50)
    val HangUp = Color.Red
    val CallPulse = Color.Red.copy(alpha = 0.3f)
    val LockUnlocked = Color.Red.copy(alpha = 0.8f)
    val LockJammed = Color(0xFFFFA500)
    val LockBusy = Color.Gray.copy(alpha = 0.8f)
    val LockUnknown = Color.DarkGray.copy(alpha = 0.8f)
    val TopScrim = Color.Black.copy(alpha = 0.6f)
    val BottomScrim = Color.Black.copy(alpha = 0.8f)
    val MessageBackground = Color(0xEB33373B)
    val SecondaryText = Color.White.copy(alpha = 0.8f)
    val Error = Color(0xFFFFB4AB)
}
