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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.JsonObject
import com.novasoftware.hadoorbell.R
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntity
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntityAppearance
import com.novasoftware.hadoorbell.domain.model.HomeAssistantService
import com.novasoftware.hadoorbell.domain.model.LockState
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout
import com.novasoftware.hadoorbell.domain.model.isHomeAssistantEventType
import com.novasoftware.hadoorbell.domain.model.isHomeAssistantServicePart
import com.novasoftware.hadoorbell.ui.icons.StreamButtonIcon
import com.novasoftware.hadoorbell.ui.icons.rememberStreamButtonIconChoices
import com.novasoftware.hadoorbell.ui.icons.resolveStreamButtonIcon
import com.novasoftware.hadoorbell.ui.icons.streamButtonFallbackIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.util.UUID

@Composable
internal fun StreamButtonEditor(
    layout: StreamButtonLayout,
    entities: List<HomeAssistantEntity>,
    isLoadingEntities: Boolean,
    entityLoadError: String?,
    onLoadEntities: () -> Unit,
    onLayoutChange: (StreamButtonLayout) -> Unit,
    onEditCall: (() -> Unit)? = null,
    services: List<HomeAssistantService> = emptyList(),
    isLoadingServices: Boolean = false,
    serviceLoadError: String? = null,
    onLoadServices: () -> Unit = {},
    enabled: Boolean = true,
    onObserveAppearances: (Set<String>) -> Flow<Map<String, HomeAssistantEntityAppearance>> = { emptyFlow() }
) {
    var editingRow by rememberSaveable(enabled) { mutableStateOf<ButtonRow?>(null) }
    var editingId by rememberSaveable(enabled) { mutableStateOf<String?>(null) }
    var draftEntityIds by remember(enabled) { mutableStateOf(emptySet<String>()) }
    val observedIds = buildSet {
        if (enabled) {
            layout.buttons.forEach { button ->
                if (button.icon.isNullOrBlank() || !button.stateEntityId.isNullOrBlank()) add(button.iconEntityId)
                if (button.action is StreamButtonAction.DoorLock) add(button.action.entityId)
            }
            if (editingRow != null) addAll(draftEntityIds)
        }
    }.filterTo(mutableSetOf(), String::isNotBlank)
    val appearances = key(observedIds) {
        val updates = remember(observedIds, onObserveAppearances) { onObserveAppearances(observedIds) }
        updates.collectAsStateWithLifecycle(initialValue = null).value
    }
    val resolveAppearance: (String) -> HomeAssistantEntityAppearance? = { entityId ->
        if (appearances != null && entityId in observedIds) {
            appearances[entityId] ?: HomeAssistantEntityAppearance("unavailable")
        } else {
            entities.find { it.entityId == entityId }?.appearance
        }
    }

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
                    onEdit = onEditCall,
                    enabled = enabled
                )
            }
            buttons.forEachIndexed { index, button ->
                key(button.id) {
                    StreamButtonListItem(
                        button = button,
                        iconEntityName = entities.find { it.entityId == button.iconEntityId }?.name,
                        appearance = resolveAppearance(button.iconEntityId),
                        lockState = if (button.action is StreamButtonAction.DoorLock) {
                            LockState.fromString(resolveAppearance(button.action.entityId)?.state)
                        } else {
                            LockState.Unknown
                        },
                        enabled = enabled,
                        onEdit = {
                            editingRow = row
                            editingId = button.id
                            if (entities.isEmpty() && button.iconEntityId.isNotBlank() && button.icon.isNullOrBlank()) {
                                onLoadEntities()
                            }
                        },
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
            }
            if (buttons.size < StreamButtonLayout.MAX_BUTTONS_PER_ROW) {
                TextButton(
                    enabled = enabled,
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

    editingRow?.takeIf { enabled }?.let { row ->
        val existing = row.buttons(layout).find { it.id == editingId }
        val dismiss = {
            editingRow = null
            editingId = null
            draftEntityIds = emptySet()
        }
        EditStreamButtonDialog(
            existing = existing,
            entities = entities,
            isLoadingEntities = isLoadingEntities,
            entityLoadError = entityLoadError,
            onLoadEntities = onLoadEntities,
            services = services,
            isLoadingServices = isLoadingServices,
            serviceLoadError = serviceLoadError,
            onLoadServices = onLoadServices,
            resolveAppearance = resolveAppearance,
            onObserveDraft = { draftEntityIds = it },
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
private fun FixedButton(
    label: String,
    icon: ImageVector,
    onEdit: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val editLabel = stringResource(R.string.stream_button_edit_call)
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = if (onEdit != null) {
            { Icon(Icons.Default.Edit, contentDescription = null) }
        } else null,
        modifier = if (onEdit != null) {
            Modifier.clickable(enabled = enabled, onClickLabel = editLabel, onClick = onEdit)
        } else Modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun StreamButtonListItem(
    button: StreamButton,
    iconEntityName: String?,
    appearance: HomeAssistantEntityAppearance?,
    lockState: LockState,
    enabled: Boolean,
    onEdit: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    moveToOtherRowLabel: String,
    onMoveToOtherRow: (() -> Unit)?,
    onToggleVisibility: () -> Unit,
    onRemove: () -> Unit
) {
    var menuOpen by remember(enabled) { mutableStateOf(false) }
    val actionKind = ActionKind.from(button.action)
    val actionLabel = stringResource(actionKind.label)
    val details = when (val action = button.action) {
        is StreamButtonAction.CallService -> listOfNotNull(
            "${action.domain}.${action.service}".takeUnless { it == button.displayName },
            action.data.get("option")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        ).joinToString(" · ").ifBlank { null }
        else -> action.entityId.takeUnless { it.isBlank() || it == button.displayName }
    }
    val actionSummary = details?.let { stringResource(R.string.stream_button_action_summary, actionLabel, it) }
        ?: actionLabel
    val icon = resolveStreamButtonIcon(button, appearance)
    ListItem(
        headlineContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(button.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (button.requiresAuthentication || button.action is StreamButtonAction.DoorLock ||
                    (button.action as? StreamButtonAction.CallService)?.doorRelease == true
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = stringResource(R.string.stream_button_require_auth),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        supportingContent = {
            Column {
                Text(
                    actionSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                button.stateEntityId?.takeIf(String::isNotBlank)?.let { entityId ->
                    val name = iconEntityName ?: entityId
                    val state = appearance?.state?.takeIf(String::isNotBlank)
                    Text(
                        state?.let { stringResource(R.string.stream_button_state_summary, name, it) } ?: name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!button.visible) Text(stringResource(R.string.stream_button_hidden))
            }
        },
        leadingContent = {
            if (icon == null) {
                Icon(streamButtonFallbackIcon(button.action, lockState), contentDescription = null)
            } else {
                StreamButtonIcon(icon, contentDescription = null)
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    IconButton(enabled = enabled && onMoveUp != null, onClick = { onMoveUp?.invoke() }) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.stream_button_move_up, button.displayName)
                        )
                    }
                    IconButton(enabled = enabled && onMoveDown != null, onClick = { onMoveDown?.invoke() }) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.stream_button_move_down, button.displayName)
                        )
                    }
                }
                Box {
                    IconButton(enabled = enabled, onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.stream_button_menu, button.displayName)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
            }
        },
        modifier = Modifier.clickable(
            enabled = enabled,
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
    services: List<HomeAssistantService>,
    isLoadingServices: Boolean,
    serviceLoadError: String?,
    onLoadServices: () -> Unit,
    resolveAppearance: (String) -> HomeAssistantEntityAppearance?,
    onObserveDraft: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    onSave: (StreamButton) -> Unit
) {
    val savedAction = existing?.action
    val savedService = savedAction as? StreamButtonAction.CallService
    val savedEvent = savedAction as? StreamButtonAction.FireEvent
    var kind by rememberSaveable { mutableStateOf(ActionKind.from(savedAction)) }
    var entityId by rememberSaveable { mutableStateOf(savedAction?.entityId?.ifBlank { null }) }
    var stateEntityId by rememberSaveable { mutableStateOf(existing?.stateEntityId) }
    var stateColors by rememberSaveable(stateSaver = StateColorsSaver) {
        mutableStateOf(existing?.stateColors.orEmpty())
    }
    var serviceId by rememberSaveable { mutableStateOf(savedService?.let(::serviceId).orEmpty()) }
    var eventType by rememberSaveable { mutableStateOf(savedEvent?.eventType.orEmpty()) }
    var skipConditions by rememberSaveable {
        mutableStateOf((savedAction as? StreamButtonAction.TriggerAutomation)?.skipConditions ?: false)
    }
    var option by rememberSaveable {
        mutableStateOf(
            savedService?.takeIf { isSelectOptionService(serviceId) }?.data?.get("option")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        )
    }
    var jsonText by rememberSaveable {
        val savedData = when (savedAction) {
            is StreamButtonAction.CallService -> savedAction.data.deepCopy().apply {
                if (isSelectOptionService(serviceId)) remove("option")
            }
            is StreamButtonAction.RunScript -> savedAction.variables
            is StreamButtonAction.FireEvent -> savedAction.data
            else -> JsonObject()
        }
        mutableStateOf(savedData.takeUnless { it.size() == 0 }?.toString().orEmpty())
    }
    var requiresAuthentication by rememberSaveable {
        mutableStateOf(existing?.requiresAuthentication ?: false)
    }
    var icon by rememberSaveable { mutableStateOf(existing?.icon) }
    var cachedEntityName by rememberSaveable { mutableStateOf(existing?.entityName) }
    var picker by rememberSaveable { mutableStateOf<EditorPicker?>(null) }
    val entity = entities.find { it.entityId == entityId }
    val iconEntityId = stateEntityId?.takeIf(String::isNotBlank) ?: entityId.orEmpty()
    val iconEntity = entities.find { it.entityId == iconEntityId }
    val appearance = resolveAppearance(iconEntityId)
    val cachedEntityIcon = existing?.entityIcon?.takeIf { existing.iconEntityId == iconEntityId }
    val currentEntityIcon = if (appearance != null) appearance.icon else cachedEntityIcon
    val currentEntityName = entity?.name?.takeIf(String::isNotBlank) ?: cachedEntityName
    val service = serviceId.trim()
    val selectedService = services.find { serviceId(it) == service }
    val isService = kind == ActionKind.CALL_SERVICE
    val isLockAction = kind == ActionKind.DOOR_LOCK
    SideEffect {
        onObserveDraft(buildSet {
            if (iconEntityId.isNotBlank()) add(iconEntityId)
            if (isLockAction) entityId?.let(::add)
        })
    }
    val isDoorRelease = isService && savedService?.doorRelease == true
    val selectOption = isService && isSelectOptionService(service)
    val optionalTarget = isService && !selectOption && !isDoorRelease
    val targetDomains = if (isService) serviceTargetDomains(service, selectedService) else kind.domains
    val hasJsonData = isService || kind == ActionKind.RUN_SCRIPT || kind == ActionKind.FIRE_EVENT
    val data = remember(jsonText, hasJsonData) {
        if (hasJsonData) parseJsonObject(jsonText) else JsonObject()
    }
    val trimmedEvent = eventType.trim()
    val target = entityId.orEmpty()
    val action = when (kind) {
        ActionKind.CALL_SERVICE -> when {
            !isServiceId(service) || data == null -> null
            isDoorRelease && entityId == null -> null
            selectOption && (entityId == null || option == null) -> null
            else -> StreamButtonAction.CallService(
                domain = service.substringBefore('.'),
                service = service.substringAfter('.'),
                entityId = target,
                data = data.deepCopy().apply {
                    if (target.isNotBlank()) remove("entity_id")
                    if (selectOption) addProperty("option", option)
                },
                doorRelease = isDoorRelease,
                needsResponse = selectedService?.needsResponse
                    ?: (savedService?.let { serviceId(it) == service && it.needsResponse } == true)
            )
        }
        ActionKind.DOOR_LOCK -> entityId
            ?.takeIf { it.substringBefore('.') in StreamButtonAction.LOCK_DOMAINS }
            ?.let(StreamButtonAction::DoorLock)
        ActionKind.RUN_SCRIPT -> if (entityId != null && data != null) StreamButtonAction.RunScript(target, data) else null
        ActionKind.TRIGGER_AUTOMATION -> entityId?.let { StreamButtonAction.TriggerAutomation(it, skipConditions) }
        ActionKind.FIRE_EVENT -> data?.takeIf { isHomeAssistantEventType(trimmedEvent) }
            ?.let { StreamButtonAction.FireEvent(trimmedEvent, it) }
        ActionKind.QUICK_REPLY -> entityId?.let(StreamButtonAction::QuickReply)
    }
    val automaticIcon = resolveStreamButtonIcon(action, iconEntityId, null, cachedEntityIcon, appearance)
    val previewIcon = if (icon == null) automaticIcon else
        resolveStreamButtonIcon(action, iconEntityId, icon, cachedEntityIcon, appearance)
    val lockState = if (isLockAction) LockState.fromString(resolveAppearance(target)?.state) else LockState.Unknown
    val fallbackIcon = action?.let { streamButtonFallbackIcon(it, lockState) } ?: Icons.Default.Widgets

    fun clearEntity() {
        entityId = null
        option = null
        cachedEntityName = null
    }

    when (picker) {
        EditorPicker.ENTITY -> {
            HomeAssistantEntityPicker(
                entities = filterEntitiesByDomain(entities, targetDomains),
                isLoading = isLoadingEntities,
                loadError = entityLoadError,
                onRetry = onLoadEntities,
                onDismiss = { picker = null },
                selectedEntityId = entityId,
                onSelect = { selected ->
                    if (entityId != selected.entityId) option = null
                    entityId = selected.entityId
                    cachedEntityName = selected.name.takeIf(String::isNotBlank)
                    picker = null
                }
            )
            return
        }
        EditorPicker.STATE_ENTITY -> {
            HomeAssistantEntityPicker(
                entities = entities,
                isLoading = isLoadingEntities,
                loadError = entityLoadError,
                onRetry = onLoadEntities,
                onDismiss = { picker = null },
                selectedEntityId = stateEntityId,
                title = R.string.stream_button_choose_state_entity,
                onSelect = { selected ->
                    if (stateEntityId != selected.entityId) stateColors = emptyMap()
                    stateEntityId = selected.entityId
                    picker = null
                }
            )
            return
        }
        EditorPicker.STATE_COLORS -> {
            StateColorEditor(
                entityId = stateEntityId.orEmpty(),
                currentState = appearance?.state,
                entityOptions = iconEntity?.options.orEmpty(),
                stateColors = stateColors,
                onChange = { stateColors = it },
                onDismiss = { picker = null }
            )
            return
        }
        EditorPicker.ICON -> {
            StreamButtonIconPicker(
                icon = icon,
                automaticIcon = automaticIcon,
                automaticFallback = fallbackIcon,
                onDismiss = { picker = null },
                onSelect = {
                    icon = it
                    picker = null
                }
            )
            return
        }
        EditorPicker.SERVICE -> {
            HomeAssistantServicePicker(
                services = services,
                isLoading = isLoadingServices,
                loadError = serviceLoadError,
                onRetry = onLoadServices,
                onDismiss = { picker = null },
                serviceId = service,
                onSelect = { nextService ->
                    if (nextService != service) {
                        val descriptor = services.find { serviceId(it) == nextService }
                        val nextDomains = serviceTargetDomains(nextService, descriptor)
                        if (nextDomains.isNotEmpty() && entityId?.substringBefore('.') !in nextDomains) {
                            clearEntity()
                        }
                        option = null
                        jsonText = ""
                    }
                    serviceId = nextService
                    picker = null
                }
            )
            return
        }
        EditorPicker.OPTION -> {
            SelectOptionPicker(
                options = entity?.options.orEmpty(),
                selectedOption = option,
                onDismiss = { picker = null },
                onSelect = {
                    option = it
                    picker = null
                }
            )
            return
        }
        null -> Unit
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
                    choices = listOf(kind) + ActionKind.entries.filterNot { it == kind },
                    selectedChoice = kind,
                    choiceLabel = { stringResource(it.label) },
                    onSelect = { nextKind ->
                        if (nextKind != kind) {
                            kind = nextKind
                            if (nextKind != ActionKind.DOOR_LOCK ||
                                entityId?.substringBefore('.') !in StreamButtonAction.LOCK_DOMAINS
                            ) {
                                clearEntity()
                                if (nextKind == ActionKind.DOOR_LOCK && savedAction is StreamButtonAction.DoorLock) {
                                    entityId = savedAction.entityId
                                    cachedEntityName = existing.entityName
                                }
                            }
                            jsonText = ""
                            if (nextKind == ActionKind.CALL_SERVICE) {
                                serviceId = ""
                            }
                        }
                    }
                )

                if (isService) {
                    EditorPickerButton(
                        label = stringResource(R.string.stream_button_service),
                        value = service.ifBlank { stringResource(R.string.stream_button_choose_service) },
                        onClick = {
                            onLoadServices()
                            picker = EditorPicker.SERVICE
                        }
                    )
                    selectedService?.description?.takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (kind != ActionKind.FIRE_EVENT && (!isService || service.isNotBlank())) {
                    EditorPickerButton(
                        label = stringResource(
                            if (optionalTarget) R.string.stream_button_optional_target else R.string.stream_button_entity
                        ),
                        value = currentEntityName ?: entityId ?: stringResource(R.string.stream_button_choose_entity),
                        onClick = {
                            onLoadEntities()
                            picker = EditorPicker.ENTITY
                        }
                    )
                    if (optionalTarget && entityId != null) {
                        TextButton(onClick = ::clearEntity) {
                            Text(stringResource(R.string.stream_button_clear_target))
                        }
                    }
                }

                if (selectOption && entityId != null) {
                    SelectOptionField(
                        entity = entity,
                        option = option,
                        isLoading = isLoadingEntities,
                        loadError = entityLoadError,
                        onLoad = onLoadEntities,
                        onOpen = { picker = EditorPicker.OPTION }
                    )
                }

                if (kind == ActionKind.TRIGGER_AUTOMATION) {
                    EditorSwitch(
                        text = stringResource(R.string.stream_button_skip_conditions),
                        checked = skipConditions,
                        onCheckedChange = { skipConditions = it }
                    )
                }

                if (kind == ActionKind.FIRE_EVENT) {
                    OutlinedTextField(
                        value = eventType,
                        onValueChange = { eventType = it },
                        label = { Text(stringResource(R.string.stream_button_event_name)) },
                        supportingText = { Text(stringResource(R.string.stream_button_event_admin)) },
                        isError = eventType.isNotBlank() && !isHomeAssistantEventType(trimmedEvent),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                if (hasJsonData) {
                    ServiceDataField(
                        value = jsonText,
                        onValueChange = { jsonText = it },
                        isError = data == null,
                        label = if (kind == ActionKind.RUN_SCRIPT) R.string.stream_button_script_variables
                            else R.string.stream_button_action_data,
                        availableFields = if (isService) selectedService?.fields.orEmpty().keys.filterTo(mutableSetOf()) {
                            it != "entity_id" && (!selectOption || it != "option")
                        } else emptySet()
                    )
                }

                EditorPickerButton(
                    label = stringResource(R.string.stream_button_state_entity),
                    value = if (stateEntityId == null) stringResource(R.string.stream_button_follow_action)
                        else iconEntity?.name ?: stateEntityId.orEmpty(),
                    onClick = {
                        onLoadEntities()
                        picker = EditorPicker.STATE_ENTITY
                    }
                )
                appearance?.state?.takeIf(String::isNotBlank)?.let { state ->
                    Text(
                        stringResource(R.string.stream_button_current_state, state),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (stateEntityId != null) {
                    TextButton(onClick = {
                        stateEntityId = null
                        stateColors = emptyMap()
                    }) {
                        Text(stringResource(R.string.stream_button_use_action_target))
                    }
                    EditorPickerButton(
                        label = stringResource(R.string.stream_button_state_colors_title),
                        value = if (stateColors.isEmpty()) {
                            stringResource(R.string.stream_button_state_colors_configure)
                        } else {
                            pluralStringResource(
                                R.plurals.stream_button_state_colors_count, stateColors.size, stateColors.size
                            )
                        },
                        onClick = { picker = EditorPicker.STATE_COLORS }
                    )
                }

                Column {
                    Text(
                        stringResource(R.string.stream_button_icon),
                        style = MaterialTheme.typography.labelMedium
                    )
                    OutlinedButton(
                        onClick = { picker = EditorPicker.ICON },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconPreview(previewIcon, fallbackIcon)
                        Text(
                            text = icon?.let(::iconDisplayName) ?: stringResource(R.string.stream_button_icon_auto),
                            modifier = Modifier.padding(start = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isLockAction || isDoorRelease) {
                    Text(
                        text = stringResource(
                            if (isLockAction) R.string.stream_button_auth_to_unlock
                            else R.string.stream_button_auth_to_open
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    EditorSwitch(
                        text = stringResource(R.string.stream_button_require_auth),
                        checked = requiresAuthentication,
                        onCheckedChange = { requiresAuthentication = it }
                    )
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
                enabled = action != null,
                onClick = {
                    if (action != null) {
                        onSave(
                            StreamButton(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                action = action,
                                requiresAuthentication = isLockAction || isDoorRelease || requiresAuthentication,
                                visible = existing?.visible ?: true,
                                icon = icon,
                                entityIcon = currentEntityIcon,
                                entityName = currentEntityName,
                                stateEntityId = stateEntityId,
                                stateColors = stateColors
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
internal fun EditorPickerButton(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = onClick, modifier = buttonModifier.fillMaxWidth(), enabled = enabled) {
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun HomeAssistantServicePicker(
    services: List<HomeAssistantService>,
    isLoading: Boolean,
    loadError: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    serviceId: String,
    onSelect: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val search = query.trim()
    val matches = remember(services, search) {
        services.filter {
            serviceId(it).contains(search, ignoreCase = true) ||
                it.name.orEmpty().contains(search, ignoreCase = true)
        }
    }
    val currentIsInCatalog = matches.any { serviceId(it) == serviceId }
    val showManual = isServiceId(search) && matches.none { serviceId(it) == search }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stream_button_choose_service)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.stream_button_search_services)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else if (loadError != null || services.isEmpty()) {
                    Text(loadError ?: stringResource(R.string.stream_button_no_services))
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.stream_button_retry)) }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    if (search.isEmpty() && isServiceId(serviceId) && !currentIsInCatalog) {
                        item { ServiceChoice(serviceId, null, true) { onSelect(serviceId) } }
                    }
                    if (showManual) {
                        item {
                            ServiceChoice(
                                stringResource(R.string.stream_button_use_service, search),
                                null,
                                search == serviceId
                            ) { onSelect(search) }
                        }
                    }
                    items(matches, key = ::serviceId) { service ->
                        val id = serviceId(service)
                        val name = service.name?.takeIf(String::isNotBlank)
                        ServiceChoice(name ?: id, id.takeIf { name != null }, id == serviceId) {
                            onSelect(id)
                        }
                    }
                }
                if (search.isNotBlank() && !showManual && matches.isEmpty() && !isLoading) {
                    Text(stringResource(R.string.stream_button_no_service_matches))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

@Composable
private fun ServiceChoice(title: String, detail: String?, checked: Boolean, onSelect: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = detail?.let { { Text(it) } },
        trailingContent = if (checked) { { Icon(Icons.Default.Check, contentDescription = null) } } else null,
        modifier = Modifier.clickable(onClick = onSelect).semantics { selected = checked }
    )
}

@Composable
private fun EditorSwitch(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SelectOptionField(
    entity: HomeAssistantEntity?,
    option: String?,
    isLoading: Boolean,
    loadError: String?,
    onLoad: () -> Unit,
    onOpen: () -> Unit
) {
    val options = entity?.options.orEmpty()
    if (options.isNotEmpty()) {
        EditorPickerButton(
            label = stringResource(R.string.stream_button_option),
            value = option ?: stringResource(R.string.stream_button_choose_option),
            onClick = onOpen
        )
    } else {
        option?.let { Text(stringResource(R.string.stream_button_current_option, it)) }
        if (entity != null) {
            Text(
                stringResource(R.string.stream_button_no_options),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(enabled = !isLoading, onClick = onLoad) {
            Text(stringResource(
                if (isLoading) R.string.stream_button_loading_entities else R.string.stream_button_load_options
            ))
        }
    }
}

@Composable
private fun SelectOptionPicker(
    options: List<String>,
    selectedOption: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stream_button_choose_option)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(options) { option ->
                    ServiceChoice(option, null, option == selectedOption) { onSelect(option) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

@Composable
private fun StreamButtonIconPicker(
    icon: String?,
    automaticIcon: String?,
    automaticFallback: ImageVector,
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
                    fallback = automaticFallback,
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
private fun IconChoice(
    label: String,
    icon: String?,
    selected: Boolean,
    fallback: ImageVector = Icons.Default.Widgets,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { IconPreview(icon, fallback) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun IconPreview(icon: String?, fallback: ImageVector) {
    if (icon == null) Icon(fallback, contentDescription = null)
    else StreamButtonIcon(icon, contentDescription = null)
}

private fun iconDisplayName(icon: String): String = icon.removePrefix("mdi:").replace('-', ' ')

@Composable
private fun <T> EditorChoice(
    label: String,
    displayValue: String,
    choices: List<T>,
    selectedChoice: T?,
    choiceLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    displayValue,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 320.dp)
            ) {
                choices.forEach { choice ->
                    val isSelected = choice == selectedChoice
                    DropdownMenuItem(
                        text = { Text(choiceLabel(choice)) },
                        modifier = Modifier.semantics { selected = isSelected },
                        trailingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null,
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
    selectedEntityId: String? = null,
    @StringRes title: Int = R.string.stream_button_choose_entity
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
        title = { Text(stringResource(title)) },
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

internal fun serviceId(service: HomeAssistantService): String = "${service.domain}.${service.service}"
private fun serviceId(service: StreamButtonAction.CallService): String = "${service.domain}.${service.service}"

private fun isServiceId(value: String): Boolean =
    isHomeAssistantServicePart(value.substringBefore('.', "")) &&
        isHomeAssistantServicePart(value.substringAfter('.', ""))

private fun isSelectOptionService(value: String): Boolean =
    value == "select.select_option" || value == "input_select.select_option"

internal fun serviceTargetDomains(id: String, descriptor: HomeAssistantService?): Set<String> {
    if (isSelectOptionService(id)) return setOf(id.substringBefore('.'))
    val filters = descriptor?.target?.get("entity") ?: return emptySet()
    val entries = if (filters.isJsonArray) filters.asJsonArray.toList() else listOf(filters)
    val domains = mutableSetOf<String>()
    for (entry in entries) {
        val domain = entry.takeIf { it.isJsonObject }?.asJsonObject?.get("domain") ?: return emptySet()
        val values = if (domain.isJsonArray) domain.asJsonArray.toList() else listOf(domain)
        if (values.any { !it.isJsonPrimitive || !it.asJsonPrimitive.isString }) return emptySet()
        domains.addAll(values.map { it.asString })
    }
    return domains
}

internal fun filterEntitiesByDomain(
    entities: List<HomeAssistantEntity>,
    domains: Set<String>
): List<HomeAssistantEntity> = if (domains.isEmpty()) entities else entities.filter { it.domain in domains }

private val StateColorsSaver = listSaver<Map<String, String>, String>(
    save = { colors -> colors.entries.flatMap { (state, color) -> listOf(state, color) } },
    restore = { values -> values.chunked(2).associate { (state, color) -> state to color } }
)

private enum class EditorPicker { ENTITY, STATE_ENTITY, STATE_COLORS, ICON, SERVICE, OPTION }

private enum class ActionKind(@param:StringRes val label: Int, val domains: Set<String> = emptySet()) {
    CALL_SERVICE(R.string.stream_button_call_service),
    DOOR_LOCK(R.string.stream_button_door_lock, StreamButtonAction.LOCK_DOMAINS),
    RUN_SCRIPT(R.string.stream_button_script, StreamButtonAction.SCRIPT_DOMAINS),
    TRIGGER_AUTOMATION(R.string.stream_button_trigger_automation, setOf("automation")),
    FIRE_EVENT(R.string.stream_button_fire_event),
    QUICK_REPLY(R.string.stream_button_quick_reply, StreamButtonAction.SELECT_DOMAINS);

    companion object {
        fun from(action: StreamButtonAction?): ActionKind = when (action) {
            null, is StreamButtonAction.CallService -> CALL_SERVICE
            is StreamButtonAction.DoorLock -> DOOR_LOCK
            is StreamButtonAction.RunScript -> RUN_SCRIPT
            is StreamButtonAction.TriggerAutomation -> TRIGGER_AUTOMATION
            is StreamButtonAction.FireEvent -> FIRE_EVENT
            is StreamButtonAction.QuickReply -> QUICK_REPLY
        }
    }
}
