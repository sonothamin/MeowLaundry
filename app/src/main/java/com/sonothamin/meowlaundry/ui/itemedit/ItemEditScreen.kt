package com.sonothamin.meowlaundry.ui.itemedit

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.sonothamin.meowlaundry.data.ClothingType
import com.sonothamin.meowlaundry.data.Currencies
import com.sonothamin.meowlaundry.ui.components.GroupedField
import com.sonothamin.meowlaundry.ui.components.SuggestionFieldGroup
import com.sonothamin.meowlaundry.ui.components.CurrencyDropdown
import com.sonothamin.meowlaundry.data.PhotoStore
import com.sonothamin.meowlaundry.ui.theme.Spacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditScreen(
    viewModel: ItemEditViewModel,
    photoStore: PhotoStore,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val brandSuggestions by viewModel.brandSuggestions.collectAsStateWithLifecycle()
    val garmentTypeSuggestions by viewModel.garmentTypeSuggestions.collectAsStateWithLifecycle()
    val colorSuggestions by viewModel.colorSuggestions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onDone()
    }

    var pendingCapturePath by remember { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val path = pendingCapturePath
        if (success && path != null) viewModel.onPhotoCaptured(path)
    }

    // Camera is a dangerous permission on API 23+: launching TakePicture() without it
    // granted throws a SecurityException and crashes the activity, which is what was
    // happening before. Request it first and only open the camera once it's granted.
    fun launchCamera() {
        val (file, uri) = photoStore.createCaptureTarget()
        pendingCapturePath = file.absolutePath
        cameraLauncher.launch(uri)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            scope.launch { snackbarHostState.showSnackbar("Camera permission is needed to take a photo") }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris -> viewModel.onPhotosPicked(uris) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Add article" else "Edit article") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        FilledIconButton(
                            onClick = { showDeleteConfirm = true },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete article")
                        }
                        Spacer(modifier = Modifier.width(Spacing.xs))
                    }
                    FilledIconButton(
                        onClick = viewModel::save,
                        enabled = state.isValid,
                    ) {
                        Icon(Icons.Default.Check, contentDescription = if (state.isNew) "Add to closet" else "Save changes")
                    }
                    Spacer(modifier = Modifier.width(Spacing.sm))
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            PhotoPicker(
                primaryImagePath = state.primaryImagePath,
                photos = state.photos,
                onTakePhoto = {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        launchCamera()
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onPickFromGallery = { galleryLauncher.launch("image/*") },
                onRemovePhoto = viewModel::removePhoto,
                onSetPrimary = viewModel::setPrimaryPhoto,
            )

            // Brand, color and type sit in one grouped row - they're the three inputs the title is built from.
            SuggestionFieldGroup(
                fields = listOf(
                    GroupedField(
                        value = state.brand,
                        onValueChange = viewModel::onBrandChange,
                        label = "Brand",
                        placeholder = "Uniqlo",
                        suggestions = brandSuggestions,
                    ),
                    GroupedField(
                        value = state.color,
                        onValueChange = viewModel::onColorChange,
                        label = "Color",
                        placeholder = "Navy",
                        suggestions = colorSuggestions,
                        showColorDot = true,
                    ),
                    GroupedField(
                        value = state.garmentType,
                        onValueChange = viewModel::onGarmentTypeChange,
                        label = "Type",
                        placeholder = "Shirt",
                        suggestions = garmentTypeSuggestions,
                    ),
                ),
            )

            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Title") },
                placeholder = { Text("Built from brand, color and type") },
                supportingText = {
                    Text(
                        if (state.titleCustomized) "Custom title. Tap the wand to use the generated one."
                        else "Generated from the fields above. Edit it to write your own."
                    )
                },
                trailingIcon = {
                    if (state.titleCustomized && state.generatedTitle.isNotBlank()) {
                        IconButton(onClick = viewModel::resetTitleToGenerated) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = "Use generated title")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            TypeDropdown(selected = state.type, onSelect = viewModel::onTypeChange)

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Top) {
                CurrencyDropdown(
                    selected = state.currency,
                    onSelect = viewModel::onCurrencyChange,
                    modifier = Modifier.width(128.dp),
                )
                OutlinedTextField(
                    value = state.priceText,
                    onValueChange = viewModel::onPriceChange,
                    label = { Text("Replacement price (optional)") },
                    prefix = { Text(Currencies.symbol(state.currency)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this article?") },
            text = { Text("This removes it and its photo permanently. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PhotoPicker(
    primaryImagePath: String?,
    photos: List<PhotoEntry>,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    onRemovePhoto: (PhotoEntry) -> Unit,
    onSetPrimary: (PhotoEntry) -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(0.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (primaryImagePath != null) {
                AsyncImage(
                    model = primaryImagePath,
                    contentDescription = "Garment photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AddAPhoto,
                    contentDescription = null,
                    modifier = Modifier.padding(Spacing.xxl),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Every photo shows here, whether the article is brand-new (not saved yet) or
        // already exists - tap a thumbnail to make it primary, or the x to remove it.
        if (photos.isNotEmpty()) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(photos, key = { it.id ?: it.path }) { photo ->
                    PhotoThumbnail(
                        photo = photo,
                        onClick = { onSetPrimary(photo) },
                        onRemove = { onRemovePhoto(photo) },
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(Spacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = onTakePhoto) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Text(" Camera", modifier = Modifier.padding(start = Spacing.xs))
                }
                OutlinedButton(onClick = onPickFromGallery) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Text(" Gallery", modifier = Modifier.padding(start = Spacing.xs))
                }
            }
            if (photos.size > 1) {
                Text(
                    "Tap a thumbnail to make it the primary photo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(
    photo: PhotoEntry,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(modifier = Modifier.size(72.dp)) {
        AsyncImage(
            model = photo.path,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.small)
                .border(
                    width = if (photo.isPrimary) 2.dp else 0.dp,
                    color = if (photo.isPrimary) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = MaterialTheme.shapes.small,
                )
                .clickable(onClick = onClick),
        )
        if (photo.isPrimary) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Primary photo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp)
                    .size(16.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(1.dp),
            )
        }
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Remove photo",
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(16.dp)
                .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                .clickable(onClick = onRemove)
                .padding(1.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeDropdown(selected: ClothingType, onSelect: (ClothingType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name.lowercase().replaceFirstChar { it.uppercase() },
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ClothingType.values().forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    },
                )
            }
        }
    }
}
