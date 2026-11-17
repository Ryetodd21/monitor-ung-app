package com.monitorung.app.presentation.ui.screens.profile

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monitorung.app.R
import com.monitorung.app.presentation.ui.components.AchievementBadge
import com.monitorung.app.presentation.ui.components.ConfettiBurst
import com.monitorung.app.presentation.ui.components.GradientCard
import com.monitorung.app.presentation.ui.components.SectionBadge
import com.monitorung.app.presentation.ui.components.SpringButton
import com.monitorung.app.presentation.ui.components.StaggeredEntrance
import com.monitorung.app.presentation.ui.theme.*
import com.monitorung.app.presentation.viewmodel.ProfileViewModel
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editMode by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    // papercard-style celebration: confetti on save success.
    var showConfetti by remember { mutableStateOf(false) }
    // Selected avatar image (base64 data URL for display + server upload).
    var selectedAvatarBase64 by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val saveSuccessText = stringResource(R.string.profile_save_success)
    val passwordChangedText = stringResource(R.string.profile_password_changed)

    // Image picker launcher.
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedAvatarBase64 = uriToBase64DataUrl(context, it)
        }
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            snackbarHostState.showSnackbar(saveSuccessText)
            editMode = false
            showConfetti = true
            selectedAvatarBase64 = null
            viewModel.clearSaveSuccess()
        }
    }
    LaunchedEffect(uiState.passwordChangeSuccess) {
        if (uiState.passwordChangeSuccess) {
            snackbarHostState.showSnackbar(passwordChangedText)
            showPasswordDialog = false
            showConfetti = true
            viewModel.clearPasswordSuccess()
        }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_my_profile)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.profile_back))
                    }
                },
                actions = {
                    if (editMode) {
                        TextButton(onClick = { editMode = false }) { Text(stringResource(R.string.common_cancel)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StaggeredEntrance(index = 0, modifier = Modifier.fillMaxWidth()) {
            ProfileHeader(
                name = uiState.user?.name ?: "User",
                email = uiState.user?.email ?: "",
                role = uiState.user?.role ?: "",
                avatarUrl = selectedAvatarBase64 ?: uiState.user?.avatarUrl ?: "",
                isEditMode = editMode,
                onAvatarClick = { imagePickerLauncher.launch("image/*") },
            )
            }

            if (uiState.user != null) {
                // Achievements row — papercard-style badges
                StaggeredEntrance(index = 1, modifier = Modifier.fillMaxWidth()) {
                GradientCard(
                    modifier = Modifier.fillMaxWidth(),
                    gradient = Brush.horizontalGradient(GradientSoftBlue),
                    padding = 16.dp,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionBadge(
                            label = stringResource(R.string.profile_achievements),
                            accentColor = ChartBlue,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            AchievementBadge(
                                icon = Icons.Default.Person,
                                title = stringResource(R.string.achievement_first_login),
                                isUnlocked = uiState.user?.name?.isNotEmpty() == true,
                                gradient = GradientBlueTeal,
                                tint = PrimaryBlue,
                            )
                            AchievementBadge(
                                icon = Icons.Default.LocalFireDepartment,
                                title = "Streak",
                                isUnlocked = false,
                                gradient = GradientAmberPeach,
                                tint = WarningAmber,
                            )
                            AchievementBadge(
                                icon = Icons.Default.CloudDone,
                                title = stringResource(R.string.achievement_first_sync),
                                isUnlocked = false,
                                gradient = GradientTealGreen,
                                tint = SuccessTeal,
                            )
                            AchievementBadge(
                                icon = Icons.Default.Security,
                                title = stringResource(R.string.achievement_privacy),
                                isUnlocked = true,
                                gradient = GradientPurplePink,
                                tint = ChartPurple,
                            )
                            AchievementBadge(
                                icon = Icons.Default.WbSunny,
                                title = "Early Bird",
                                isUnlocked = false,
                                gradient = GradientAmberGold,
                                tint = WarningAmber,
                            )
                        }
                    }
                }
                }

                StaggeredEntrance(index = 2, modifier = Modifier.fillMaxWidth()) {
                GradientCard(
                    modifier = Modifier.fillMaxWidth(),
                    gradient = Brush.horizontalGradient(GradientSoftBlue),
                    padding = 0.dp,
                ) {
                    ProfileForm(
                        name = uiState.user!!.name,
                        email = uiState.user!!.email,
                        phone = uiState.user!!.phone,
                        bio = uiState.user!!.bio,
                        editMode = editMode,
                        isSaving = uiState.isSaving,
                        onEdit = { editMode = true },
                        onSave = { fullName, email, phone, bio ->
                            viewModel.updateProfile(
                                fullName = fullName.ifEmpty { null },
                                email = email.ifEmpty { null },
                                phone = phone.ifEmpty { null },
                                bio = bio,
                                avatarUrl = selectedAvatarBase64,
                            )
                        },
                    )
                }
                }

                StaggeredEntrance(index = 3, modifier = Modifier.fillMaxWidth()) {
                GradientCard(
                    modifier = Modifier.fillMaxWidth(),
                    gradient = Brush.horizontalGradient(GradientSoftBlue),
                    padding = 0.dp,
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionBadge(
                            label = stringResource(R.string.profile_section_security),
                            accentColor = WarningAmber,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.profile_password_label), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.profile_password_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(
                                onClick = { showPasswordDialog = true },
                                shape = RoundedCornerShape(RadiusS)
                            ) {
                                Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.profile_change_password))
                            }
                        }
                    }
                }
                }
            }
        }
    }

    if (showPasswordDialog) {
        PasswordChangeDialog(
            isSaving = uiState.isSaving,
            onDismiss = { showPasswordDialog = false },
            onConfirm = { current, new ->
                viewModel.changePassword(current, new)
            }
        )
    }

        // papercard-style celebration overlay — fires on profile save / password change.
        ConfettiBurst(
            trigger = showConfetti,
            onFinished = { showConfetti = false },
        )
    }
}

@Composable
private fun ProfileHeader(
    name: String,
    email: String,
    role: String,
    avatarUrl: String,
    isEditMode: Boolean,
    onAvatarClick: () -> Unit,
) {
    GradientCard(
        modifier = Modifier.fillMaxWidth(),
        gradient = Brush.horizontalGradient(GradientSoftBlue),
        padding = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Avatar with glow effect.
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 12.dp,
                        shape = CircleShape,
                        ambientColor = PrimaryBlue.copy(alpha = 0.35f),
                        spotColor = PrimaryBlue.copy(alpha = 0.35f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarUrl.isNotEmpty()) {
                    val bitmap = remember(avatarUrl) { loadBitmapFromUrl(avatarUrl) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = name,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        AvatarInitial(name)
                    }
                } else {
                    AvatarInitial(name)
                }
            }
            // Camera overlay — OUTSIDE the avatar circle, below it.
            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .offset(y = (-8).dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue)
                        .clickable(onClick = onAvatarClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = stringResource(R.string.profile_change_avatar),
                        tint = Color.White,
                        modifier = Modifier
                            .size(32.dp)
                            .padding(6.dp),
                    )
                }
            }
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(RadiusS))
                    .background(Blue50)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = role.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    color = Blue700,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun AvatarInitial(name: String) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Blue600),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Load a bitmap from a base64 data URL or return null.
 * Network URLs are not fetched here — the server returns a URL string that
 * the Android app's HTTP client resolves; if avatarUrl starts with "http",
 * we show the initial instead (network image loading requires Coil/Glide).
 */
private fun loadBitmapFromUrl(url: String): android.graphics.Bitmap? {
    return try {
        if (url.startsWith("data:", ignoreCase = true)) {
            val base64 = url.substringAfter("base64,")
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

/** Convert a content URI to a base64 data URL (max 1 MB). */
private fun uriToBase64DataUrl(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val bytes = inputStream.readBytes()
        inputStream.close()
        if (bytes.size > 1_048_576) return null // 1 MB limit
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        "data:$mimeType;base64,$encoded"
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileForm(
    name: String,
    email: String,
    phone: String,
    bio: String,
    editMode: Boolean,
    isSaving: Boolean,
    onEdit: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var nameState by remember(name) { mutableStateOf(name) }
    var emailState by remember(email) { mutableStateOf(email) }
    var phoneState by remember(phone) { mutableStateOf(phone) }
    var bioState by remember(bio) { mutableStateOf(bio) }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionBadge(
                label = stringResource(R.string.profile_section_account),
                accentColor = ChartBlue,
                modifier = Modifier.weight(1f),
            )
            if (!editMode) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, stringResource(R.string.profile_edit), tint = ChartBlue)
                }
            }
        }

        OutlinedTextField(
            value = nameState,
            onValueChange = { nameState = it },
            label = { Text(stringResource(R.string.profile_field_full_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            readOnly = !editMode,
            shape = RoundedCornerShape(RadiusM),
            leadingIcon = { Icon(Icons.Default.Person, null) }
        )
        OutlinedTextField(
            value = emailState,
            onValueChange = { emailState = it },
            label = { Text(stringResource(R.string.profile_field_email)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            readOnly = !editMode,
            shape = RoundedCornerShape(RadiusM),
            leadingIcon = { Icon(Icons.Default.Email, null) }
        )
        OutlinedTextField(
            value = phoneState,
            onValueChange = { phoneState = it },
            label = { Text(stringResource(R.string.profile_field_phone)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            readOnly = !editMode,
            shape = RoundedCornerShape(RadiusM),
            leadingIcon = { Icon(Icons.Default.Phone, null) }
        )
        OutlinedTextField(
            value = bioState,
            onValueChange = { bioState = it },
            label = { Text(stringResource(R.string.profile_field_bio)) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = !editMode,
            shape = RoundedCornerShape(RadiusM),
            minLines = 2,
            maxLines = 4
        )

        if (editMode) {
            SpringButton(
                onClick = { onSave(nameState, emailState, phoneState, bioState) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                cornerRadius = 10.dp,
                containerColor = Blue600,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = TextOnPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.profile_save))
                }
            }
        }
    }
}

@Composable
private fun PasswordChangeDialog(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var current by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_change_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = { Text(stringResource(R.string.profile_current_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(RadiusS)
                )
                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it },
                    label = { Text(stringResource(R.string.profile_new_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(RadiusS)
                )
                OutlinedTextField(
                    value = confirmPass,
                    onValueChange = { confirmPass = it },
                    label = { Text(stringResource(R.string.profile_confirm_new_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = confirmPass.isNotEmpty() && newPass != confirmPass,
                    supportingText = {
                        if (confirmPass.isNotEmpty() && newPass != confirmPass) {
                            Text(stringResource(R.string.profile_password_mismatch), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    shape = RoundedCornerShape(RadiusS)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(current, newPass) },
                enabled = !isSaving && current.isNotEmpty() && newPass.length >= 6 && newPass == confirmPass
            ) {
                Text(stringResource(if (isSaving) R.string.profile_save_in_progress else R.string.profile_change_password))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
