@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.ak.vodcalendar.ui.touch

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.ListItem as TvListItem
import androidx.tv.material3.OutlinedButton as TvOutlinedButton

/*
 * The TV Material components only react to the D-pad (OK / Enter); a tap on a phone
 * screen does nothing. These wrappers keep the TV components (same look, same D-pad
 * focus) and add touch: tap = click, long press = long click, with the pressed look.
 */

/** Tap and long press for a TV component, shown through its [interactionSource]. */
@Composable
fun Modifier.touchClicks(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
): Modifier {
    if (!enabled) return this
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    val hasLongClick = onLongClick != null
    return this.then(
        Modifier.pointerInput(interactionSource, hasLongClick) {
            detectTapGestures(
                onPress = { offset ->
                    val press = PressInteraction.Press(offset)
                    interactionSource.emit(press)
                    val released = tryAwaitRelease()
                    interactionSource.emit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                },
                onLongPress = if (hasLongClick) { _: Offset -> longClick?.invoke() } else null,
                onTap = { click() },
            )
        },
    )
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    TvButton(
        onClick = onClick,
        modifier = modifier.touchClicks(source, enabled, onClick, onLongClick),
        onLongClick = onLongClick,
        enabled = enabled,
        interactionSource = source,
        content = content,
    )
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    TvOutlinedButton(
        onClick = onClick,
        modifier = modifier.touchClicks(source, enabled, onClick, onLongClick),
        onLongClick = onLongClick,
        enabled = enabled,
        interactionSource = source,
        content = content,
    )
}

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    TvCard(
        onClick = onClick,
        modifier = modifier.touchClicks(source, true, onClick, onLongClick),
        onLongClick = onLongClick,
        interactionSource = source,
        content = content,
    )
}

@Composable
fun ListItem(
    selected: Boolean,
    onClick: () -> Unit,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    TvListItem(
        selected = selected,
        onClick = onClick,
        headlineContent = headlineContent,
        modifier = modifier.touchClicks(source, enabled, onClick, onLongClick),
        enabled = enabled,
        onLongClick = onLongClick,
        supportingContent = supportingContent,
        trailingContent = trailingContent,
        interactionSource = source,
    )
}
