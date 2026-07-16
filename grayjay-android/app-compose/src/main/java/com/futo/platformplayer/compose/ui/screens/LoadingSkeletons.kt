package com.futo.platformplayer.compose.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun ShimmerBlock(
    modifier: Modifier,
    shape: Shape = MaterialTheme.shapes.small,
) {
    val transition = rememberInfiniteTransition(label = "skeleton-shimmer")
    val offset = transition.animateFloat(
        initialValue = -900f,
        targetValue = 1_800f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_250),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeleton-offset",
    ).value
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.11f)
    Box(
        modifier = modifier.background(
            brush = Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = androidx.compose.ui.geometry.Offset(offset - 500f, 0f),
                end = androidx.compose.ui.geometry.Offset(offset, 500f),
            ),
            shape = shape,
        ),
    )
}

@Composable
internal fun VideoListSkeleton(
    count: Int = 4,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag("video-list-skeleton"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(count) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ShimmerBlock(
                        modifier = Modifier
                            .width(184.dp)
                            .aspectRatio(16f / 9f),
                        shape = MaterialTheme.shapes.medium,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ShimmerBlock(Modifier.fillMaxWidth().height(18.dp))
                        ShimmerBlock(Modifier.fillMaxWidth(0.72f).height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ShimmerBlock(Modifier.size(24.dp), CircleShape)
                            ShimmerBlock(Modifier.fillMaxWidth(0.58f).height(14.dp))
                        }
                        ShimmerBlock(Modifier.fillMaxWidth(0.45f).height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun SuggestionListSkeleton(count: Int = 5) {
    Column(
        modifier = Modifier.testTag("suggestion-list-skeleton"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        repeat(count) { index ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ShimmerBlock(Modifier.size(24.dp), CircleShape)
                ShimmerBlock(
                    Modifier
                        .fillMaxWidth(if (index % 2 == 0) 0.72f else 0.55f)
                        .height(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun CommentListSkeleton(count: Int = 3, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.testTag("comment-list-skeleton"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        repeat(count) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShimmerBlock(Modifier.size(40.dp), CircleShape)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ShimmerBlock(Modifier.fillMaxWidth(0.35f).height(14.dp))
                    ShimmerBlock(Modifier.fillMaxWidth().height(14.dp))
                    ShimmerBlock(Modifier.fillMaxWidth(0.78f).height(14.dp))
                }
            }
        }
    }
}
