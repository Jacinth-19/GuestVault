package com.guestvault.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Row of dots showing how many PIN digits have been entered. */
@Composable
fun PinDots(
    length: Int,
    filled: Int,
    modifier: Modifier = Modifier,
    error: Boolean = false
) {
    val dotColor = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val borderColor = if (error) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.outline
    }

    Row(
        modifier = modifier.semantics {
            contentDescription = "$filled of $length PIN digits entered"
        },
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(length) { index ->
            val isFilled = index < filled
            val scale by animateFloatAsState(
                targetValue = if (isFilled) 1f else 0.78f,
                label = "pinDotScale"
            )
            Box(
                modifier = Modifier
                    .scale(scale)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (isFilled) dotColor else Color.Transparent)
                    .border(1.6.dp, if (isFilled) dotColor else borderColor, CircleShape)
            )
        }
    }
}

/**
 * Numeric keypad used for every PIN entry in the app.
 *
 * Every key has a stable content description ("PIN key 1", "Delete digit") so
 * the UI can be driven by the instrumented walkthrough test.
 */
@Composable
fun PinKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    onBiometric: (() -> Unit)? = null,
    enabled: Boolean = true,
    keySize: Dp = 70.dp
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9')).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { digit ->
                    PinKey(digit = digit, keySize = keySize, enabled = enabled, onClick = { onDigit(digit) })
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBiometric != null) {
                Box(
                    modifier = Modifier
                        .size(keySize)
                        .clip(CircleShape)
                        .clickable(enabled = enabled) { onBiometric() }
                        .semantics { contentDescription = "Use fingerprint" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Fingerprint,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            } else {
                Spacer(Modifier.size(keySize))
            }

            PinKey(digit = '0', keySize = keySize, enabled = enabled, onClick = { onDigit('0') })

            Box(
                modifier = Modifier
                    .size(keySize)
                    .clip(CircleShape)
                    .clickable(enabled = enabled) { onBackspace() }
                    .semantics { contentDescription = "Delete digit" },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Backspace,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun PinKey(
    digit: Char,
    keySize: Dp,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(keySize)
            .semantics { contentDescription = "PIN key $digit" },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick,
        enabled = enabled
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(keySize)) {
            Text(
                text = digit.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Compact dots used on the lock screen header. */
@Composable
fun PinDotsMini(filled: Int, length: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(length) { index ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    )
            )
        }
    }
}
