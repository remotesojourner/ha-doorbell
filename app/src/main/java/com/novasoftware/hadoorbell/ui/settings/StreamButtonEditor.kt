package com.novasoftware.hadoorbell.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novasoftware.hadoorbell.R
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntity
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout
import com.novasoftware.hadoorbell.domain.usecase.OpenDoorUseCase
import com.novasoftware.hadoorbell.ui.icons.StreamButtonIcon
import com.novasoftware.hadoorbell.ui.icons.defaultHomeAssistantIcon
import com.novasoftware.hadoorbell.ui.icons.rememberStreamButtonIconChoices
import java.util.UUID

@Composable
internal fun StreamButtonEditor(
    layout: StreamButtonLayout,
    entities: List<HomeAssistantEntity>,
    isLoadingEntities: Boolean,
    entityLoadError: String?,
    onLoadEntities: () -> Unit,
    onLayoutChange: (StreamButtonLayout) -> Unit,
    onEditCall: (() -> Unit)? = null
) {
    var editingRow by rememberSaveable { mutableStateOf<ButtonRow?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }

    ButtonRow.entries.forEach { row ->
        val buttons = row.buttons(layout)
        val otherRow = if (row == ButtonRow.TOP) ButtonRow.BOTTOM else ButtonRow.TOP
        val otherButtons = otherRow.buttons(layout)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(row.title),
                style = MaterialTheme.typography.titleSmall
            )
            if (row == ButtonRow.BOTTOM) {
                FixedButton(
                    label = stringResource(R.string.stream_button_sound),
                    icon = Icons.AutoMirrored.Filled.VolumeUp
                )
                FixedButton(
                    label = stringResource(R.string.stream_button_call),
                    icon = Icons.Default.Call,
                    onEdit = onEditCall
                )
            }
            buttons.forEachIndexed { index, button ->
                val openEditor = {
                    editingRow = row
                    editingId = button.id
                }
                StreamButtonListItem(
                    button = button,
                    onEdit = openEditor,
                    onMoveUp = if (index > 0) {
                        { onLayoutChange(row.replace(layout, buttons.move(index, index - 1))) }
                    } else null,
                    onMoveDown = if (index < buttons.lastIndex) {
                        { onLayoutChange(row.replace(layout, buttons.move(index, index + 1))) }
                    } else null,
                    moveToOtherRowLabel = stringResource(otherRow.moveHere),
                    onMoveToOtherRow = if (otherButtons.size < StreamButtonLayout.MAX_BUTTONS_PER_ROW) {
                        {
                            val updated = row.replace(layout, buttons - button)
                            onLayoutChange(otherRow.replace(updated, otherButtons + button))
                        }
                    } else null,
                    onToggleVisibility = {
                        val updated = buttons.map {
                            if (it.id == button.id) it.copy(visible = !it.visible) else it
                        }
                        onLayoutChange(row.replace(layout, updated))
                    },
                    onRemove = { onLayoutChange(row.replace(layout, buttons - button)) }
                )
            }
            if (buttons.size < StreamButtonLayout.MAX_BUTTONS_PER_ROW) {
                TextButton(
                    onClick = {
                        editingRow = row
                        editingId = null
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(row.add), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    editingRow?.let { row ->
        val existing = row.buttons(layout).find { it.id == editingId }
        val dismiss = {
            editingRow = null
            editingId = null
        }
        EditStreamButtonDialog(
            existing = existing,
            entities = entities,
            isLoadingEntities = isLoadingEntities,
            entityLoadError = entityLoadError,
            onLoadEntities = onLoadEntities,
            onDismiss = dismiss,
            onSave = { button ->
                val buttons = row.buttons(layout)
                val updated = if (existing == null) buttons + button else buttons.map {
                    if (it.id == button.id) button else it
                }
                onLayoutChange(row.replace(layout, updated))
                dismiss()
            }
        )
    }
}

@Composable
private fun FixedButton(label: String, icon: ImageVector, onEdit: (() -> Unit)? = null) {
    val editLabel = stringResource(R.string.stream_button_edit_call)
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = if (onEdit != null) {
            { Icon(Icons.Default.Edit, contentDescription = null) }
        } else null,
        modifier = if (onEdit != null) {
            Modifier.clickable(onClickLabel = editLabel, onClick = onEdit)
        } else Modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun StreamButtonListItem(
    button: StreamButton,
    onEdit: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    moveToOtherRowLabel: String,
    onMoveToOtherRow: (() -> Unit)?,
    onToggleVisibility: () -> Unit,
    onRemove: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val actionKind = ActionKind.from(button.action)
    val actionLabel = stringResource(actionKind.label)
    val details = when (val action = button.action) {
        is StreamButtonAction.SelectOption -> action.option
        else -> action.entityId
    }
    ListItem(
        headlineContent = {
            Text(button.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                Text(
                    stringResource(R.string.stream_button_action_summary, actionLabel, details),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!button.visible) Text(stringResource(R.string.stream_button_hidden))
            }
        },
        leadingContent = if (actionKind.requiresAuthentication || button.requiresAuthentication) {
            {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = stringResource(R.string.stream_button_require_auth)
                )
            }
        } else null,
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.stream_button_menu, button.label)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.stream_button_move_up)) },
                        enabled = onMoveUp != null,
                        onClick = {
                            menuOpen = false
                            onMoveUp?.invoke()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.stream_button_move_down)) },
                        enabled = onMoveDown != null,
                        onClick = {
                            menuOpen = false
                            onMoveDown?.invoke()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(moveToOtherRowLabel) },
                        enabled = onMoveToOtherRow != null,
                        onClick = {
                            menuOpen = false
                            onMoveToOtherRow?.invoke()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(
                                if (button.visible) R.string.stream_button_hide else R.string.stream_button_show
                            ))
                        },
                        onClick = {
                            menuOpen = false
                            onToggleVisibility()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.stream_button_remove)) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        }
                    )
                }
            }
        },
        modifier = Modifier.clickable(
            onClickLabel = stringResource(R.string.stream_button_edit),
            onClick = onEdit
        ),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun EditStreamButtonDialog(
    existing: StreamButton?,
    entities: List<HomeAssistantEntity>,
    isLoadingEntities: Boolean,
    entityLoadError: String?,
    onLoadEntities: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (StreamButton) -> Unit
) {
    var kind by rememberSaveable { mutableStateOf(ActionKind.from(existing?.action)) }
    var label by rememberSaveable { mutableStateOf(existing?.label.orEmpty()) }
    var entityId by rememberSaveable { mutableStateOf(existing?.action?.entityId) }
    var option by rememberSaveable {
        mutableStateOf((existing?.action as? StreamButtonAction.SelectOption)?.option)
    }
    val savedDoor = existing?.action as? StreamButtonAction.OpenDoor
    var doorService by rememberSaveable { mutableStateOf(savedDoor?.service.orEmpty()) }
    var requiresAuthentication by rememberSaveable {
        mutableStateOf(existing?.requiresAuthentication ?: false)
    }
    var icon by rememberSaveable { mutableStateOf(existing?.icon) }
    var cachedEntityIcon by rememberSaveable { mutableStateOf(existing?.entityIcon) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showIconPicker by rememberSaveable { mutableStateOf(false) }
    val entity = entities.find { it.entityId == entityId }
    val currentEntityIcon = entity?.icon ?: cachedEntityIcon
    val automaticIcon = currentEntityIcon ?: entityId?.let(::defaultHomeAssistantIcon)
    val defaultDoorService = if (kind == ActionKind.OPEN_DOOR) {
        OpenDoorUseCase.defaultServiceFor(entityId.orEmpty())
    } else ""
    val service = doorService.trim()
    val validDoorService = if (service.isBlank()) {
        defaultDoorService.isNotBlank()
    } else {
        SERVICE_NAME.matches(service)
    }
    val existingDoorBinding = savedDoor != null && savedDoor.entityId == entityId &&
        savedDoor.service == service
    val action = kind.buildAction(entityId, option, service).takeIf {
        kind != ActionKind.OPEN_DOOR || validDoorService || existingDoorBinding
    }

    if (showPicker) {
        HomeAssistantEntityPicker(
            entities = entities.filter { kind.acceptsDomain(it.domain) },
            isLoading = isLoadingEntities,
            loadError = entityLoadError,
            onRetry = onLoadEntities,
            onDismiss = { showPicker = false },
            selectedEntityId = entityId,
            onSelect = { selected ->
                if (entityId != selected.entityId) {
                    if (label.isBlank() || label == entity?.name || label == option) {
                        label = selected.name
                    }
                    entityId = selected.entityId
                    option = null
                    cachedEntityIcon = selected.icon
                } else if (selected.icon != null) {
                    cachedEntityIcon = selected.icon
                }
                showPicker = false
            }
        )
        return
    }

    if (showIconPicker) {
        StreamButtonIconPicker(
            icon = icon,
            automaticIcon = automaticIcon,
            onDismiss = { showIconPicker = false },
            onSelect = {
                icon = it
                showIconPicker = false
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (existing == null) R.string.stream_button_add else R.string.stream_button_edit))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EditorChoice(
                    label = stringResource(R.string.stream_button_action),
                    displayValue = stringResource(kind.label),
                    choices = ActionKind.entries,
                    choiceLabel = { stringResource(it.label) },
                    onSelect = { nextKind ->
                        if (nextKind != kind) {
                            kind = nextKind
                            entityId?.let { currentEntityId ->
                                if (!nextKind.acceptsDomain(currentEntityId.substringBefore('.'))) {
                                    entityId = null
                                    cachedEntityIcon = null
                                }
                            }
                            option = null
                        }
                    }
                )

                OutlinedButton(
                    onClick = {
                        onLoadEntities()
                        showPicker = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = entity?.name ?: entityId ?: stringResource(R.string.stream_button_choose_entity),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (kind == ActionKind.SELECT_OPTION && entityId != null) {
                    val options = entity?.options.orEmpty()
                    if (options.isNotEmpty()) {
                        EditorChoice(
                            label = stringResource(R.string.stream_button_option),
                            displayValue = option ?: stringResource(R.string.stream_button_choose_option),
                            choices = options,
                            choiceLabel = { it },
                            onSelect = { nextOption ->
                                if (label.isBlank() || label == entity?.name || label == option) {
                                    label = nextOption
                                }
                                option = nextOption
                            }
                        )
                    } else {
                        option?.let { Text(stringResource(R.string.stream_button_current_option, it)) }
                        if (entity != null) {
                            Text(
                                stringResource(R.string.stream_button_no_options),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        entityLoadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        TextButton(enabled = !isLoadingEntities, onClick = onLoadEntities) {
                            Text(
                                stringResource(
                                    if (isLoadingEntities) R.string.stream_button_loading_entities
                                    else R.string.stream_button_load_options
                                )
                            )
                        }
                    }
                }

                if (kind == ActionKind.OPEN_DOOR) {
                    val serviceError = entityId != null && !validDoorService
                    OutlinedTextField(
                        value = doorService,
                        onValueChange = { doorService = it },
                        label = { Text(stringResource(R.string.stream_button_door_service)) },
                        placeholder = { Text(defaultDoorService.ifBlank { "switch.turn_on" }) },
                        supportingText = {
                            Text(
                                when {
                                    serviceError && service.isBlank() ->
                                        stringResource(R.string.stream_button_door_service_required)
                                    serviceError ->
                                        stringResource(R.string.stream_button_door_service_invalid)
                                    defaultDoorService.isNotBlank() ->
                                        stringResource(R.string.stream_button_door_service_default, defaultDoorService)
                                    else ->
                                        stringResource(R.string.stream_button_door_service_hint)
                                }
                            )
                        },
                        isError = serviceError,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.stream_button_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Column {
                    Text(
                        stringResource(R.string.stream_button_icon),
                        style = MaterialTheme.typography.labelMedium
                    )
                    OutlinedButton(
                        onClick = { showIconPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconPreview(icon ?: automaticIcon)
                        Text(
                            text = icon?.let(::iconDisplayName) ?: stringResource(R.string.stream_button_icon_auto),
                            modifier = Modifier.padding(start = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (kind.requiresAuthentication) {
                    Text(
                        text = stringResource(
                            if (kind == ActionKind.DOOR_LOCK) R.string.stream_button_auth_to_unlock
                            else R.string.stream_button_auth_to_open
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = requiresAuthentication,
                                role = Role.Switch,
                                onValueChange = { requiresAuthentication = it }
                            )
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.stream_button_require_auth),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = requiresAuthentication, onCheckedChange = null)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank() && action != null,
                onClick = {
                    if (action != null) {
                        onSave(
                            StreamButton(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                label = label.trim(),
                                action = action,
                                requiresAuthentication = kind.requiresAuthentication || requiresAuthentication,
                                visible = existing?.visible ?: true,
                                icon = icon,
                                entityIcon = currentEntityIcon
                            )
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.stream_button_done))
            }
        }
    )
}

@Composable
private fun StreamButtonIconPicker(
    icon: String?,
    automaticIcon: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val catalog = rememberStreamButtonIconChoices()
    val matches = remember(catalog, query) {
        val search = query.trim().lowercase().removePrefix("mdi:").replace(' ', '-')
        val found = catalog.filter { it.contains(search) }
        if (search in found) listOf(search) + (found - search) else found
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stream_button_choose_icon)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.stream_button_icon_search)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                IconChoice(
                    label = stringResource(R.string.stream_button_icon_auto),
                    icon = automaticIcon,
                    selected = icon == null,
                    onClick = { onSelect(null) }
                )
                if (matches.isEmpty()) {
                    Text(stringResource(R.string.stream_button_icon_no_matches))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(matches, key = { it }) { name ->
                            IconChoice(
                                label = iconDisplayName(name),
                                icon = name,
                                selected = icon?.removePrefix("mdi:") == name,
                                onClick = { onSelect(name) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun IconChoice(label: String, icon: String?, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { IconPreview(icon) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun IconPreview(icon: String?) {
    if (icon == null) Icon(Icons.Default.Widgets, contentDescription = null)
    else StreamButtonIcon(icon, contentDescription = null)
}

private fun iconDisplayName(icon: String): String = icon.removePrefix("mdi:").replace('-', ' ')

@Composable
private fun <T> EditorChoice(
    label: String,
    displayValue: String,
    choices: List<T>,
    choiceLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(displayValue, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 320.dp)
            ) {
                choices.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(choiceLabel(choice)) },
                        onClick = {
                            expanded = false
                            onSelect(choice)
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeAssistantEntityPicker(
    entities: List<HomeAssistantEntity>,
    isLoading: Boolean,
    loadError: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (HomeAssistantEntity) -> Unit,
    selectedEntityId: String? = null
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(entities, query) {
        val search = query.trim()
        entities.filter {
            it.name.contains(search, ignoreCase = true) ||
                it.entityId.contains(search, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stream_button_choose_entity)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.stream_button_entity_search)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                when {
                    isLoading -> Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.stream_button_loading_entities))
                    }
                    loadError != null -> Column {
                        Text(loadError, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.stream_button_retry))
                        }
                    }
                    entities.isEmpty() -> Column {
                        Text(stringResource(R.string.stream_button_no_entities))
                        TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.stream_button_refresh))
                        }
                    }
                    matches.isEmpty() -> Text(stringResource(R.string.stream_button_no_matches))
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(matches, key = { it.entityId }) { entity ->
                            val selected = selectedEntityId == entity.entityId
                            ListItem(
                                headlineContent = { Text(entity.name) },
                                supportingContent = { Text(entity.entityId) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = { onSelect(entity) }),
                                colors = ListItemDefaults.colors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

private enum class ButtonRow(
    @param:StringRes val title: Int,
    @param:StringRes val add: Int,
    @param:StringRes val moveHere: Int
) {
    TOP(R.string.stream_button_top, R.string.stream_button_add_top, R.string.stream_button_move_top),
    BOTTOM(R.string.stream_button_bottom, R.string.stream_button_add_bottom, R.string.stream_button_move_bottom);

    fun buttons(layout: StreamButtonLayout): List<StreamButton> =
        if (this == TOP) layout.top else layout.bottom

    fun replace(layout: StreamButtonLayout, buttons: List<StreamButton>): StreamButtonLayout =
        if (this == TOP) layout.copy(top = buttons) else layout.copy(bottom = buttons)
}

private fun List<StreamButton>.move(from: Int, to: Int): List<StreamButton> =
    toMutableList().apply { add(to, removeAt(from)) }

private val SERVICE_NAME = Regex("[a-z0-9_]+\\.[a-z0-9_]+")

private enum class ActionKind(
    @param:StringRes val label: Int,
    private val domains: Set<String>? = null,
    val requiresAuthentication: Boolean = false
) {
    TOGGLE(R.string.stream_button_toggle, StreamButtonAction.TOGGLE_DOMAINS),
    PRESS(R.string.stream_button_press, StreamButtonAction.PRESS_DOMAINS),
    SELECT_OPTION(R.string.stream_button_select, StreamButtonAction.SELECT_DOMAINS),
    RUN_SCRIPT(R.string.stream_button_script, StreamButtonAction.SCRIPT_DOMAINS),
    TURN_ON(R.string.stream_button_turn_on, StreamButtonAction.TURN_ON_DOMAINS),
    QUICK_REPLY(R.string.stream_button_quick_reply, StreamButtonAction.SELECT_DOMAINS),
    DOOR_LOCK(R.string.stream_button_door_lock, StreamButtonAction.LOCK_DOMAINS, requiresAuthentication = true),
    OPEN_DOOR(R.string.stream_button_open_door, requiresAuthentication = true);

    fun acceptsDomain(domain: String): Boolean = domains?.contains(domain) ?: true

    fun buildAction(entityId: String?, option: String?, service: String): StreamButtonAction? {
        if (entityId.isNullOrBlank()) return null
        return when (this) {
            TOGGLE -> StreamButtonAction.Toggle(entityId)
            PRESS -> StreamButtonAction.Press(entityId)
            SELECT_OPTION -> option?.let { StreamButtonAction.SelectOption(entityId, it) }
            RUN_SCRIPT -> StreamButtonAction.RunScript(entityId)
            TURN_ON -> StreamButtonAction.TurnOn(entityId)
            QUICK_REPLY -> StreamButtonAction.QuickReply(entityId)
            DOOR_LOCK -> StreamButtonAction.DoorLock(entityId)
            OPEN_DOOR -> StreamButtonAction.OpenDoor(entityId, service)
        }
    }

    companion object {
        fun from(action: StreamButtonAction?): ActionKind = when (action) {
            is StreamButtonAction.Toggle, null -> TOGGLE
            is StreamButtonAction.Press -> PRESS
            is StreamButtonAction.SelectOption -> SELECT_OPTION
            is StreamButtonAction.RunScript -> RUN_SCRIPT
            is StreamButtonAction.TurnOn -> TURN_ON
            is StreamButtonAction.QuickReply -> QUICK_REPLY
            is StreamButtonAction.DoorLock -> DOOR_LOCK
            is StreamButtonAction.OpenDoor -> OPEN_DOOR
        }
    }
}
