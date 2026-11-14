package com.monitorung.app.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.monitorung.app.R
import com.monitorung.app.presentation.ui.theme.RadiusL

@Composable
fun BubbleNavBar(
    items: List<NavBarItem>,
    currentRoute: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PaperCard(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        padding = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = item.route == currentRoute
                val background by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    animationSpec = tween(180), label = "tab_background",
                )
                val foreground by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(180), label = "tab_foreground",
                )
                val badgeLabel = if (item.badgeCount > 0) {
                    pluralStringResource(R.plurals.history_pending_count, item.badgeCount, item.badgeCount)
                } else null
                Column(
                    modifier = Modifier.weight(1f)
                        .heightIn(min = 64.dp)
                        .clip(RoundedCornerShape(RadiusL))
                        .background(background)
                        .selectable(selected, role = Role.Tab, onClick = { onItemSelected(item.route) })
                        .semantics { if (badgeLabel != null) stateDescription = badgeLabel }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = null,
                            tint = foreground,
                            modifier = Modifier.size(24.dp),
                        )
                        if (item.badgeCount > 0) {
                            Box(Modifier.offset(x = 4.dp).size(6.dp)
                                .background(MaterialTheme.colorScheme.error, RoundedCornerShape(3.dp)))
                        }
                    }
                    Text(
                        item.label,
                        color = foreground,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
