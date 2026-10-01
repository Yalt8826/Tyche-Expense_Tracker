package dev.yashas.expensetracker.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yashas.expensetracker.R
import kotlinx.coroutines.delay

/** Brand gold, sampled from the Tyche mark. */
private val TycheGold = Color(0xFFF6C445)
private val TycheNight = Color(0xFF0D0A15)

private const val SPLASH_MS = 2200L

/**
 * Brand splash (P6 finale): the designed Tyche art full-bleed, wordmark + tagline
 * fade in, auto-advances after 2.2s — tap or swipe up to skip instantly.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    var textVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(250)
        textVisible = true
        delay(SPLASH_MS)
        onDone()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TycheNight)
            .clickable(
                interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                indication = null,
                onClickLabel = "Continue",
            ) { onDone() },
    ) {
        Image(
            painter = painterResource(R.drawable.tyche_splash),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 84.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "TYCHE",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 10.sp,
                color = TycheGold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Know where your money goes",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFC9C4D4),
            )
        }
    }
}
