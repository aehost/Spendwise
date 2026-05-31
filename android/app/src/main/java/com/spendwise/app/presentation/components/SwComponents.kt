package com.spendwise.app.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendwise.app.presentation.theme.*

/**
 * SpendWise shared UI component library.
 *
 * One source of truth for the patterns repeated across screens (section
 * labels, empty states, summary bars, progress rings, cards). Every screen
 * should compose from these so the visual language stays consistent and
 * future polish happens in one place.
 *
 * All composables use only stable Compose APIs and the existing theme tokens.
 */

// ── Section label ─────────────────────────────────────────────────
@Composable
fun SwSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        color = TextMuted,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = modifier
    )
}

// ── Card surface ──────────────────────────────────────────────────
@Composable
fun SwCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(CardBg)
            .border(0.5.dp, BorderColor, RoundedCornerShape(cornerRadius))
            .padding(padding),
        content = content
    )
}

// ── Summary bar (Spent / Received / Net …) ────────────────────────
data class SwStat(val label: String, val value: String, val color: Color)

@Composable
fun SwSummaryBar(stats: List<SwStat>, modifier: Modifier = Modifier) {
    if (stats.isEmpty()) return
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(CardBg, CardBg2)))
            .border(0.5.dp, BorderColor, RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        stats.forEachIndexed { i, s ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = s.color)
                Text(s.label, fontSize = 11.sp, color = TextMuted)
            }
            if (i < stats.lastIndex) {
                Box(Modifier.width(0.5.dp).height(28.dp).background(BorderColor))
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────
@Composable
fun SwEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    icon: ImageVector? = null,
    ctaText: String? = null,
    onCta: (() -> Unit)? = null
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(96.dp)
                    .background(
                        Brush.linearGradient(listOf(Primary.copy(0.18f), Secondary.copy(0.10f))),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    icon != null  -> Icon(icon, null, tint = Primary, modifier = Modifier.size(44.dp))
                    emoji != null -> Text(emoji, fontSize = 40.sp)
                    else          -> Text("✨", fontSize = 40.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle, fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp)
            )
            if (ctaText != null && onCta != null) {
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = onCta,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) { Text(ctaText, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// ── Circular progress ring (Goals, Health Score, Budget) ──────────
@Composable
fun SwProgressRing(
    progress: Float,                       // 0f .. 1f
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = 12.dp,
    ringColor: Color = Primary,
    trackColor: Color = BorderColor,
    animate: Boolean = true,
    content: @Composable () -> Unit = {}
) {
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (animate) 800 else 0),
        label = "ring"
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke  = strokeWidth.toPx()
            val inset   = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)
            // Track
            drawArc(
                color = trackColor,
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            // Progress
            drawArc(
                color = ringColor,
                startAngle = -90f, sweepAngle = animated * 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

// ── Linear progress bar (budget rows) ─────────────────────────────
@Composable
fun SwLinearProgress(
    progress: Float,                       // 0f .. 1f
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    color: Color = Primary,
    trackColor: Color = BorderColor
) {
    val pct = progress.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height))
            .background(trackColor)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(pct)
                .clip(RoundedCornerShape(height))
                .background(color)
        )
    }
}

// ── Haptic click ──────────────────────────────────────────────────
/**
 * Wraps an onClick with a subtle haptic tick. Permission-free and respects the
 * user's system haptic setting. We use HAPTICS, not audio: click sounds are
 * intrusive in a finance app used in public, whereas a tactile tick feels
 * premium and private.
 *
 *   FloatingActionButton(onClick = hapticClick { showSheet = true }) { ... }
 */
@Composable
fun hapticClick(onClick: () -> Unit): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onClick()
    }
}

// ── Small pill chip (status, tags) ────────────────────────────────
@Composable
fun SwPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}
