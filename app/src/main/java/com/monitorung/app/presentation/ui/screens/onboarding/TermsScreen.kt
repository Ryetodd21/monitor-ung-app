package com.monitorung.app.presentation.ui.screens.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.monitorung.app.R
import com.monitorung.app.presentation.ui.components.ConfettiBurst
import com.monitorung.app.presentation.ui.components.GradientCard
import com.monitorung.app.presentation.ui.components.GradientSpringButton
import com.monitorung.app.presentation.ui.components.PaperCard
import com.monitorung.app.presentation.ui.components.StaggeredEntrance
import com.monitorung.app.presentation.ui.theme.*
import com.monitorung.app.presentation.viewmodel.OnboardingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    var isChecked by remember { mutableStateOf(false) }
    // papercard-style celebration: confetti on terms accept.
    var showConfetti by remember { mutableStateOf(false) }

    val hasScrolledToBottom by remember {
        derivedStateOf { scrollState.value >= scrollState.maxValue - 100 }
    }
    val scrollFraction by remember {
        derivedStateOf {
            if (scrollState.maxValue > 0) {
                (scrollState.value.toFloat() / scrollState.maxValue.toFloat()).coerceIn(0f, 1f)
            } else 1f
        }
    }
    val canProceed = isChecked && hasScrolledToBottom

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Soft ambient gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            MaterialTheme.colorScheme.surface,
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // Step dots
            StepDots(
                currentStep = 1,
                totalSteps = 2,
                labels = listOf(
                    stringResource(R.string.terms_step_terms),
                    stringResource(R.string.terms_step_permissions),
                ),
                modifier = Modifier.padding(top = 20.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(RadiusM),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = stringResource(R.string.terms_header_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.terms_header_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Content card — GradientCard wrapper for papercard gamified feel
            GradientCard(
                gradient = Brush.horizontalGradient(GradientSoftBlue),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                padding = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(20.dp)
                ) {
                    // Scroll hint
                    if (!hasScrolledToBottom) {
                        ScrollHint(
                            progress = scrollFraction,
                            targetReached = hasScrolledToBottom
                        )
                    }

                    // Item 1
                    StaggeredEntrance(index = 0, modifier = Modifier.fillMaxWidth()) {
                        TermItem(
                            number = 1,
                            icon = Icons.Outlined.School,
                            title = stringResource(R.string.terms_q1_title),
                            content = stringResource(R.string.terms_q1_body),
                            color = PrimaryBlue
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // Item 2
                    StaggeredEntrance(index = 1, modifier = Modifier.fillMaxWidth()) {
                        TermItem(
                            number = 2,
                            icon = Icons.Outlined.Smartphone,
                            title = stringResource(R.string.terms_q2_title),
                            bullets = listOf(
                                stringResource(R.string.terms_q2_bullet_1),
                                stringResource(R.string.terms_q2_bullet_2),
                                stringResource(R.string.terms_q2_bullet_3),
                                stringResource(R.string.terms_q2_bullet_4),
                            ),
                            color = ChartIndigo
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // Item 3
                    StaggeredEntrance(index = 2, modifier = Modifier.fillMaxWidth()) {
                        TermItem(
                            number = 3,
                            icon = Icons.Outlined.LocationOn,
                            title = stringResource(R.string.terms_q3_title),
                            content = stringResource(R.string.terms_q3_body),
                            color = ChartGreen
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // Item 4
                    StaggeredEntrance(index = 3, modifier = Modifier.fillMaxWidth()) {
                        TermItem(
                            number = 4,
                            icon = Icons.Outlined.Lock,
                            title = stringResource(R.string.terms_q4_title),
                            bullets = listOf(
                                stringResource(R.string.terms_q4_bullet_1),
                                stringResource(R.string.terms_q4_bullet_2),
                                stringResource(R.string.terms_q4_bullet_3),
                                stringResource(R.string.terms_q4_bullet_4),
                            ),
                            color = ChartAmber
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // Item 5
                    StaggeredEntrance(index = 4, modifier = Modifier.fillMaxWidth()) {
                        TermItem(
                            number = 5,
                            icon = Icons.Outlined.Favorite,
                            title = stringResource(R.string.terms_q5_title),
                            bullets = listOf(
                                stringResource(R.string.terms_q5_bullet_1),
                                stringResource(R.string.terms_q5_bullet_2),
                                stringResource(R.string.terms_q5_bullet_3),
                                stringResource(R.string.terms_q5_bullet_4),
                            ),
                            color = ChartPurple
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Warning box
                    PaperCard(
                        modifier = Modifier.fillMaxWidth(),
                        padding = 0.dp,
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.terms_warning_title),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.terms_warning_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Agreement
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusM))
                    .clickable { isChecked = !isChecked }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { isChecked = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.outline
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (hasScrolledToBottom) {
                        stringResource(R.string.terms_checkbox_ready)
                    } else {
                        stringResource(R.string.terms_checkbox_wait)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasScrolledToBottom) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Buttons — GradientSpringButton for accept, playful TextButton for decline.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    onClick = onDecline,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(RadiusM2),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = ErrorFuchsia
                    )
                ) {
                    Text(
                        text = stringResource(R.string.terms_decline_button),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }

                GradientSpringButton(
                    text = stringResource(R.string.terms_accept_button),
                    onClick = {
                        viewModel.acceptTerms()
                        showConfetti = true
                        onAccept()
                    },
                    enabled = canProceed,
                    gradient = Brush.horizontalGradient(GradientBlueTeal),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    cornerRadius = 14.dp,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // papercard-style celebration overlay — fires when terms are accepted.
        ConfettiBurst(
            trigger = showConfetti,
            onFinished = { showConfetti = false },
        )
    }
}

@Composable
private fun ScrollHint(progress: Float, targetReached: Boolean) {
    if (!targetReached) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.South,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.terms_scroll_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(RadiusMicro))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(
                            brush = Brush.horizontalGradient(GradientBlueTeal)
                        )
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TermItem(
    number: Int,
    icon: ImageVector,
    title: String,
    content: String? = null,
    bullets: List<String>? = null,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Colorful left-border accent — papercard gamified style
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(RadiusMicro))
                .background(color)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Surface(
            shape = RoundedCornerShape(RadiusS2),
            color = color.copy(alpha = 0.15f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (content != null) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 19.sp
                )
            }
            if (bullets != null) {
                bullets.forEach { bullet ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = color,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 6.dp, top = 1.dp)
                        )
                        Text(
                            text = bullet,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
        }
    }
}
