package com.mas.alsalah

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object Lux {
    val Gold = Color(0xFFF2CB6B)
    val GoldSoft = Color(0xFFFFE9A8)
    val GoldDeep = Color(0xFFC9972B)
    val Emerald = Color(0xFF12A36B)
    val EmeraldDeep = Color(0xFF064B36)
    val Night = Color(0xFF03120C)
    val OnGold = Color(0xFF2A1D03)
    val goldBrush: Brush get() = Brush.linearGradient(listOf(GoldSoft, Gold, GoldDeep))
}

@Composable
internal fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
internal fun Modifier.glass(shape: Shape = RoundedCornerShape(24.dp), emphasis: Boolean = false): Modifier {
    val dark = isDarkTheme()
    val fill = if (dark) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = if (emphasis) 0.17f else 0.10f), Color.White.copy(alpha = 0.04f)))
    } else {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.94f), Color.White.copy(alpha = 0.70f)))
    }
    val stroke = if (emphasis) {
        Brush.linearGradient(listOf(Lux.Gold.copy(alpha = 0.85f), Lux.Gold.copy(alpha = 0.12f), Lux.Gold.copy(alpha = 0.55f)))
    } else if (dark) {
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.36f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.14f)))
    } else {
        Brush.linearGradient(listOf(Color(0x440B6B4A), Color(0x140B6B4A)))
    }
    return this.clip(shape).background(fill).border(1.dp, stroke, shape)
}

@Composable
internal fun AuroraBackground(content: @Composable BoxScope.() -> Unit) {
    val dark = isDarkTheme()
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(
                Brush.verticalGradient(
                    if (dark) listOf(Color(0xFF03140D), Color(0xFF062A1D), Color(0xFF020E09))
                    else listOf(Color(0xFFF4FAF6), Color(0xFFE6F2EA), Color(0xFFDCEBE1))
                )
            )
            val w = size.width
            val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(if (dark) 0x6612A36B else 0x3312A36B), Color.Transparent),
                    center = Offset(w * 0.05f, h * 0.06f), radius = w * 0.9f
                ),
                radius = w * 0.9f, center = Offset(w * 0.05f, h * 0.06f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(if (dark) 0x33F2CB6B else 0x22F2CB6B), Color.Transparent),
                    center = Offset(w * 1.0f, h * 0.30f), radius = w * 0.7f
                ),
                radius = w * 0.7f, center = Offset(w * 1.0f, h * 0.30f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(if (dark) 0x5512A36B else 0x2212A36B), Color.Transparent),
                    center = Offset(w * 0.8f, h * 1.0f), radius = w * 0.95f
                ),
                radius = w * 0.95f, center = Offset(w * 0.8f, h * 1.0f)
            )
        }
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() }
    }
}

@Composable
internal fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
internal fun GlassCard(modifier: Modifier = Modifier, emphasis: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.glass(RoundedCornerShape(26.dp), emphasis).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
        content = content
    )
}

@Composable
internal fun GlassIconButton(
    icon: ImageVector, desc: String?, onClick: () -> Unit, modifier: Modifier = Modifier,
    size: Dp = 44.dp, tint: Color = Color.Unspecified, active: Boolean = false
) {
    val color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint
    Box(
        modifier.size(size).glass(CircleShape, active).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = color, modifier = Modifier.size(size * 0.46f)) }
}

@Composable
internal fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 44.dp, tint: Color = Color.Unspecified) {
    val t = if (tint == Color.Unspecified) MaterialTheme.colorScheme.primary else tint
    val shape = RoundedCornerShape(size * 0.34f)
    Box(
        modifier.size(size).clip(shape)
            .background(Brush.linearGradient(listOf(t.copy(alpha = 0.30f), t.copy(alpha = 0.08f))))
            .border(1.dp, t.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = t, modifier = Modifier.size(size * 0.5f)) }
}

@Composable
internal fun GoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, height: Dp = 54.dp) {
    Box(
        modifier.height(height).clip(RoundedCornerShape(18.dp)).background(Lux.goldBrush).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, null, tint = Lux.OnGold, modifier = Modifier.size(20.dp))
            Text(text, color = Lux.OnGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
internal fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Box(
        modifier.heightIn(min = 46.dp).glass(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
internal fun PillTab(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier.clip(shape)
            .then(if (selected) Modifier.background(Lux.goldBrush) else Modifier.glass(shape))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = if (selected) Lux.OnGold else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp
        )
    }
}

@Composable
internal fun ProgressRing(progress: Float, modifier: Modifier = Modifier, stroke: Dp = 9.dp, color: Color = Lux.Gold, track: Color = Color.White.copy(alpha = 0.14f)) {
    Canvas(modifier) {
        val s = stroke.toPx()
        val d = size.minDimension - s
        drawArc(
            color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
            topLeft = Offset(s / 2f, s / 2f), size = Size(d, d), style = Stroke(s, cap = StrokeCap.Round)
        )
        val sweep = 360f * progress.coerceIn(0f, 1f)
        if (sweep > 0.5f) drawArc(
            color = color, startAngle = -90f, sweepAngle = sweep, useCenter = false,
            topLeft = Offset(s / 2f, s / 2f), size = Size(d, d), style = Stroke(s, cap = StrokeCap.Round)
        )
    }
}

@Composable
internal fun SectionTitle(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        if (actionLabel != null && onAction != null) {
            Text(actionLabel, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onAction).padding(6.dp))
        } else Spacer(Modifier.width(1.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Box(Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(Lux.goldBrush))
        }
    }
}

@Composable
internal fun Header(title: String, subtitle: String? = null, back: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        if (back != null) {
            GlassIconButton(Icons.Outlined.ArrowBack, "رجوع", back)
        } else {
            Image(
                painterResource(R.drawable.logo_full), contentDescription = "شعار الصلاة",
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End)
            if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.End)
            Box(Modifier.padding(top = 2.dp).width(36.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Lux.goldBrush))
        }
    }
}

@Composable
internal fun FeatureTile(title: String, subtitle: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 124.dp).glass(RoundedCornerShape(24.dp)).clickable(onClick = onClick).padding(15.dp),
        horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconBadge(icon, size = 46.dp)
        Spacer(Modifier.height(2.dp))
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, textAlign = TextAlign.End)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.End)
    }
}
