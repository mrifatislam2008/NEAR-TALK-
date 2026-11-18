package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.UserIdentity
import com.example.ui.components.RadarPulseRings
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.RoyalBlueDark
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    identity: UserIdentity?,
    onFinished: (isProfileConfigured: Boolean) -> Unit
) {
    val scale = remember { Animatable(0.75f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = tween(550, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(550))
    }

    LaunchedEffect(identity) {
        if (identity != null) {
            delay(1100L)
            onFinished(identity.isProfileConfigured)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(RoyalBlue, RoyalBlueDark)
                )
            )
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        RadarPulseRings(
            isActive = true,
            color = Color.White,
            modifier = Modifier.size(300.dp)
        )

        Column(
            modifier = Modifier
                .padding(32.dp)
                .scale(scale.value)
                .alpha(alpha.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(104.dp),
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = 0.16f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.img_app_icon_fg),
                        contentDescription = "NEAR TALK Logo",
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(22.dp))
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "NEAR TALK",
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "DIRECT BLUETOOTH • ZERO INTERNET",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}
