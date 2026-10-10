package app.ownbridge

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import app.ownbridge.core.ProxyProtocol
import app.ownbridge.core.SavedRunPresence
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OwnBridgeApp(
    language: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Boolean,
) {
    var page by remember { mutableStateOf(Page.CONNECT) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var settingsDestination by rememberSaveable { mutableStateOf(SettingsDestination.HOME) }
    var selectedHistoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var host by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("root") }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val uiContext = LocalContext.current
    val uiResources = LocalResources.current
    val context = uiContext.applicationContext
    val snackbarHostState = remember { SnackbarHostState() }
    val showNotice: (Int) -> Unit = remember(uiResources, scope, snackbarHostState) {
        { messageRes ->
            snackbarHostState.currentSnackbarData?.dismiss()
            scope.launch {
                snackbarHostState.showSnackbar(
                    uiResources.getString(messageRes), duration = SnackbarDuration.Short,
                )
            }
        }
    }
    val compositePreview = BuildConfig.COMPOSITE_DEPLOYMENT_ENABLED
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val hostKeys = remember(context) { HostKeyStore(context) }
    val networkRoute = remember(context) { SshNetworkRouteInspector(context) }
    val ipv6Network = remember(context) { Ipv6NetworkInspector(context) }
    var currentNetworkRoute by remember(networkRoute) { mutableStateOf(networkRoute.current()) }
    val deploymentRecords = remember(context) { DeploymentRecordStore(context) }
    val detection = remember(scope, hostKeys, networkRoute) {
        DetectionController(scope, hostKeys, networkRoute)
    }
    val detectionState = detection.state
    val deployment = remember(scope, hostKeys, deploymentRecords, context) {
        DeploymentController(scope, hostKeys, deploymentRecords, context.assets)
    }
    val deploymentState = deployment.state
    val compositeRecords = remember(context) { CompositeDeploymentRecordStore(context) }
    val compositeDeployment = remember(scope, hostKeys, compositeRecords, context) {
        CompositeDeploymentController(scope, hostKeys, compositeRecords, context.assets)
    }
    val history = remember(context, scope) { HistoryController(context, scope) }
    val completed = remember { CompletedRunSession() }
    var deleteCandidateId by remember { mutableStateOf<String?>(null) }
    var deletingHistoryId by remember { mutableStateOf<String?>(null) }
    val compositeState = compositeDeployment.state
    val activeCompositeRecord = when (compositeState) {
        is CompositeDeploymentState.NeedsReconciliation -> compositeState.record
        is CompositeDeploymentState.Settled -> compositeState.record
        else -> null
    }
    val activeRunIdForHost = activeCompositeRecord?.takeIf {
        it.plan.sshHost.equals(host.trim(), ignoreCase = true)
    }?.runId
    val reconcilingSavedRun = (detectionState as? DetectionUiState.Ready)?.savedRunCheck
        ?.let { check -> check.presence == SavedRunPresence.MISSING &&
            check.runId == activeCompositeRecord?.runId } == true
    val savedCompositePlan = when (compositeState) {
        is CompositeDeploymentState.NeedsReconciliation -> compositeState.record.plan
        is CompositeDeploymentState.Running -> compositeState.record.plan
        is CompositeDeploymentState.Settled -> compositeState.record.plan
        else -> null
    }
    val defaultAddressesForCurrentDetection = (detectionState as? DetectionUiState.Ready)
        ?.inventory?.let { defaultAddressSelection(it, compositePreview) }.orEmpty()
    var useCompositeView by remember { mutableStateOf(compositePreview && compositeState != CompositeDeploymentState.Idle) }
    var autoAdvancePending by remember { mutableStateOf(false) }
    val verifiedComposite = if (useCompositeView) (compositeState as? CompositeDeploymentState.Settled)
        ?.takeIf { it.completedExport() != null } else null
    val verifiedLegacy = if (!useCompositeView) deploymentState as? DeploymentState.ServerReady else null
    val verifiedRunId = verifiedComposite?.record?.runId ?: verifiedLegacy?.runId
    val ping = remember(scope) { PingController(scope) }
    val pingState = ping.state
    val nodeProbe = remember(scope, context, networkRoute, ipv6Network) {
        NodeProbeController(scope, MihomoNodeProbe(context), networkRoute, ipv6Network)
    }
    val nodeProbeState = nodeProbe.state
    val subscription = remember(scope, hostKeys, context) {
        SubscriptionDeliveryController(scope, hostKeys, context.assets)
    }
    var selectedAddresses by remember { mutableStateOf(emptySet<String>()) }
    var selectedProtocols by rememberSaveable {
        mutableStateOf(when {
            compositePreview && savedCompositePlan != null -> savedCompositePlan.protocols.map { protocol ->
                when (protocol) {
                    ProxyProtocol.SHADOWSOCKS -> "SS"
                    ProxyProtocol.VLESS -> "VLESS"
                    ProxyProtocol.HYSTERIA2 -> "HY2"
                }
            }.toSet()
            compositePreview -> setOf("SS", "VLESS", "HY2")
            else -> setOf("SS")
        })
    }
    var selectedClients by rememberSaveable {
        mutableStateOf(if (compositePreview && savedCompositePlan != null) setOf("Clash / Mihomo")
            else emptySet())
    }
    val legacyPlan = when (deploymentState) {
        is DeploymentState.Running -> deploymentState.plan
        is DeploymentState.Failed -> deploymentState.plan
        is DeploymentState.ServerReady -> deploymentState.plan
        DeploymentState.Idle -> null
    }
    val subscriptionRequest = if (useCompositeView) {
        (compositeState as? CompositeDeploymentState.Settled)?.subscriptionRequest()
            ?: completed.compositeSnapshot?.subscriptionRequest()
    } else (deploymentState as? DeploymentState.ServerReady)?.subscriptionRequest()
        ?: completed.legacySnapshot?.subscriptionRequest()
    val progressHost = if (useCompositeView) activeCompositeRecord?.plan?.sshHost else legacyPlan?.sshHost
    val credentialsAvailable = progressHost != null && host.equals(progressHost, ignoreCase = true) &&
        username == "root" && password.isNotBlank()
    val progressAction = if (completed.compositeSnapshot != null || completed.legacySnapshot != null) {
        ProgressAction.HIDDEN
    } else if (verifiedRunId != null && completed.saveErrorRunId != verifiedRunId) {
        ProgressAction.RUNNING
    } else if (useCompositeView) ProgressActionPolicy.composite(
        compositeState, credentialsAvailable,
        progressHost != null && hostKeys.get(progressHost) != null,
    ) else ProgressActionPolicy.legacy(
        deploymentState, credentialsAvailable,
        progressHost != null && hostKeys.get(progressHost) != null,
    )
    val listState = rememberLazyListState()
    val layoutDirection = LocalLayoutDirection.current
    val contentRoute: ContentRoute = if (settingsOpen) {
        ContentRoute.Settings(settingsDestination)
    } else {
        ContentRoute.Step(page)
    }
    var previousRoute by remember { mutableStateOf(contentRoute) }
    val enterFrom = remember(contentRoute) { previousRoute }
    val pageEntrance = remember(contentRoute) {
        Animatable(if (enterFrom == contentRoute) 1f else 0f)
    }
    val enterDirection = remember(contentRoute) {
        contentRoute.enterDirectionFrom(enterFrom)
    }
    val scrollToTopEnabled = !settingsOpen && (
        page == Page.PROGRESS ||
            (page == Page.CONNECT && (detectionState !is DetectionUiState.Idle || pingState.hasLog))
    )

    DisposableEffect(detection, ping, deployment, compositeDeployment, nodeProbe, subscription) {
        onDispose {
            detection.cancel()
            ping.dispose()
            deployment.dispose()
            compositeDeployment.dispose()
            nodeProbe.dispose()
            subscription.dispose()
        }
    }

    suspend fun saveAndReleaseCompletedRun(runId: String) {
        val composite = verifiedComposite?.takeIf { it.record.runId == runId }
        val legacy = verifiedLegacy?.takeIf { it.runId == runId }
        if (!completed.finish(runId, composite, legacy, password, subscriptionRequest,
                subscription.state, history, compositeDeployment, deployment)) return
        detection.invalidate()
        ping.invalidate()
        selectedAddresses = emptySet()
        selectedProtocols = if (compositePreview) setOf("SS", "VLESS", "HY2") else setOf("SS")
        selectedClients = if (compositePreview) setOf("Clash / Mihomo") else emptySet()
        host = ""
        username = "root"
        password = ""
    }

    LaunchedEffect(subscriptionRequest) {
        subscription.selectRun(subscriptionRequest?.runId, subscriptionRequest?.yaml)
    }

    LaunchedEffect(compositeState) { history.captureComposite(compositeState, password) }
    LaunchedEffect(deploymentState) { history.captureLegacy(deploymentState, password) }
    LaunchedEffect(verifiedRunId, history.loading) {
        if (verifiedRunId != null && !history.loading && completed.markAutoSaveAttempt(verifiedRunId)) {
            saveAndReleaseCompletedRun(verifiedRunId)
        }
    }
    LaunchedEffect(subscription.state, subscriptionRequest) {
        subscriptionRequest?.let { request ->
            history.captureDelivery(request.runId, request.sshHost, request.yaml, subscription.state)
        }
        if (subscription.state is SubscriptionDeliveryState.Ready) completed.clearDeliveryPassword()
    }

    DisposableEffect(networkRoute) {
        val observation = runCatching {
            networkRoute.observe { currentNetworkRoute = it }
        }.getOrNull()
        onDispose { observation?.close() }
    }

    LaunchedEffect(activeCompositeRecord?.runId, completed.compositeSnapshot?.record?.runId) {
        nodeProbe.selectRun(activeCompositeRecord?.runId ?: completed.compositeSnapshot?.record?.runId)
    }

    LaunchedEffect(contentRoute) {
        previousRoute = contentRoute
        listState.scrollToItem(0)
        pageEntrance.animateTo(
            1f,
            animationSpec = tween(UiMotion.pageEnterDurationMillis, easing = FastOutSlowInEasing),
        )
    }

    // Advance once for a run started in this session, never for a restored result or a later visit to the log.
    LaunchedEffect(autoAdvancePending, progressAction, verifiedRunId, completed.saveErrorRunId,
        completed.compositeSnapshot, completed.legacySnapshot) {
        if (!autoAdvancePending) return@LaunchedEffect
        val saved = completed.compositeSnapshot != null || completed.legacySnapshot != null
        val saveFailed = verifiedRunId != null && completed.saveErrorRunId == verifiedRunId
        if (saved || saveFailed) {
            autoAdvancePending = false
            if (page == Page.PROGRESS && !settingsOpen) page = Page.RESULT
        } else if (verifiedRunId == null && progressAction != ProgressAction.RUNNING) {
            autoAdvancePending = false
        }
    }

    val leaveSettingsLevel = {
        when {
            settingsDestination == SettingsDestination.HISTORY && selectedHistoryId != null ->
                selectedHistoryId = null
            settingsDestination != SettingsDestination.HOME ->
                settingsDestination = SettingsDestination.HOME
            else -> settingsOpen = false
        }
    }
    BackHandler(settingsOpen) { leaveSettingsLevel() }

    LaunchedEffect(detectionState) {
        if (detectionState is DetectionUiState.Ready) {
            val missingRun = detectionState.savedRunCheck
                ?.takeIf { it.presence == SavedRunPresence.MISSING }
            val archivedOldRun = missingRun != null &&
                compositeDeployment.archiveMissingRemote(host, missingRun.runId)
            if (archivedOldRun) {
                history.finishRun(missingRun.runId)
                selectedProtocols = setOf("SS", "VLESS", "HY2")
                selectedClients = setOf("Clash / Mihomo")
            }
            selectedAddresses = when {
                !archivedOldRun && compositePreview && savedCompositePlan?.sshHost?.equals(
                    host.trim(), ignoreCase = true,
                ) == true -> savedCompositePlan.addresses
                    .map { it.value }.toSet()
                else -> defaultAddressesForCurrentDetection
            }
        }
    }

    CompositionLocalProvider(LocalTransientNotice provides showNotice) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                    ),
                    title = {
                        Text(
                            if (settingsOpen) stringResource(when (settingsDestination) {
                                SettingsDestination.HOME -> R.string.settings
                                SettingsDestination.HISTORY -> R.string.settings_history
                                SettingsDestination.SERVERS -> R.string.settings_saved_servers
                                SettingsDestination.LANGUAGE -> R.string.settings_language
                                SettingsDestination.ABOUT -> R.string.settings_about
                            })
                            else stringResource(R.string.app_name),
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        if (settingsOpen) {
                            IconButton(onClick = leaveSettingsLevel) {
                                ProjectIcon(IconAsset.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        }
                    },
                    actions = {
                        if (settingsOpen && settingsDestination == SettingsDestination.HISTORY &&
                            selectedHistoryId != null) {
                            IconButton(onClick = { deleteCandidateId = selectedHistoryId },
                                modifier = Modifier.padding(end = 8.dp)) {
                                ProjectIcon(IconAsset.Delete,
                                    contentDescription = stringResource(R.string.history_delete_record),
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        } else if (!settingsOpen) {
                            IconButton(
                                onClick = {
                                    settingsDestination = SettingsDestination.HOME
                                    settingsOpen = true
                                },
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                ProjectIcon(IconAsset.Settings, contentDescription = stringResource(R.string.settings))
                            }
                        }
                    },
                )
                if (!settingsOpen) StepNavigation(current = page, onSelect = { page = it })
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding)
                .consumeWindowInsets(padding).imePadding(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val progress = pageEntrance.value
                    alpha = progress
                    val localeDirection = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                    translationX = localeDirection * enterDirection * (1f - progress) *
                        UiMotion.pageEnterDistance.toPx()
                },
                state = listState,
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 10.dp,
                    bottom = if (scrollToTopEnabled) 90.dp else 30.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (settingsOpen) {
                    item {
                        when (settingsDestination) {
                            SettingsDestination.HOME -> SettingsPage(
                                onOpen = {
                                    selectedHistoryId = null
                                    settingsDestination = it
                                },
                            )
                            SettingsDestination.SERVERS -> SettingsSavedServersPage(
                                hostKeys = hostKeys,
                                onForget = { rememberedHost ->
                                    val forgotten = hostKeys.forget(rememberedHost)
                                    if (forgotten && host.equals(rememberedHost,
                                            ignoreCase = true)) {
                                        detection.invalidate()
                                        selectedAddresses = emptySet()
                                    }
                                    forgotten
                                },
                            )
                            SettingsDestination.HISTORY -> SettingsHistoryPage(
                                records = history.records,
                                loading = history.loading,
                                storageError = history.storageError,
                                unreadableCount = history.unreadableCount,
                                selectedId = selectedHistoryId,
                                onSelectedIdChange = { selectedHistoryId = it })
                            SettingsDestination.LANGUAGE -> SettingsLanguagePage(language, onLanguageSelected)
                            SettingsDestination.ABOUT -> SettingsAboutPage()
                        }
                    }
                } else {
                    when (page) {
                        Page.INTRO -> item { IntroPage() }
                        Page.CONNECT -> {
                            item {
                                ConnectPage(
                                host = host,
                                onHostChange = {
                                    host = it
                                    detection.invalidate()
                                    ping.invalidate()
                                    selectedAddresses = emptySet()
                                },
                                    username = username,
                                    onUsernameChange = {
                                        username = it
                                        detection.invalidate()
                                        selectedAddresses = emptySet()
                                    },
                                    password = password,
                                    onPasswordChange = {
                                        password = it
                                        detection.invalidate()
                                        selectedAddresses = emptySet()
                                    },
                                    detectionState = detectionState,
                                    vpnActive = currentNetworkRoute == SshNetworkRoute.VPN,
                                    pingState = pingState,
                                    onHostBlur = { ping.checkCapability(host) },
                                    onPing = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        detection.invalidate()
                                        selectedAddresses = emptySet()
                                        ping.start(host)
                                    },
                                    onCancelPing = ping::cancel,
                                    onDetect = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        ping.clearRun()
                                        selectedAddresses = emptySet()
                                        detection.detect(host, username, password, activeRunIdForHost)
                                    },
                                    onTrustChangedHost = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        detection.trustChangedHost(host, username, password, activeRunIdForHost)
                                    },
                                    onCancel = detection::cancel,
                                    onNext = { page = Page.PLAN },
                                )
                            }
                            if (detectionState !is DetectionUiState.Idle || pingState.hasLog) {
                                item { DetectionTerminal(detectionState, pingState) }
                            }
                            if (detectionState is DetectionUiState.Ready) {
                                item { ServerInventoryPanel(detectionState.inventory) }
                            }
                        }
                        Page.PLAN -> item {
                            PlanPage(
                                host = host,
                                username = username,
                                pendingPlan = when (deploymentState) {
                                    is DeploymentState.Failed -> deploymentState.plan
                                    is DeploymentState.ServerReady -> deploymentState.plan
                                    else -> null
                                },
                                pendingCompositePlan = when (compositeState) {
                                    is CompositeDeploymentState.NeedsReconciliation -> compositeState.record.plan
                                    is CompositeDeploymentState.Running -> compositeState.record.plan
                                    is CompositeDeploymentState.Settled -> compositeState.record.plan
                                    else -> null
                                },
                                compositePreview = compositePreview,
                                localRecordCorrupt = compositePreview &&
                                    compositeState == CompositeDeploymentState.LocalRecordCorrupt,
                                deploymentRunning = if (compositePreview)
                                    compositeState is CompositeDeploymentState.Running
                                else deploymentState is DeploymentState.Running,
                                reconcilingSavedRun = reconcilingSavedRun,
                                archivedPreviousRun = (detectionState as? DetectionUiState.Ready)
                                    ?.savedRunCheck?.presence == SavedRunPresence.MISSING &&
                                    compositeState == CompositeDeploymentState.Idle,
                                inventory = (detectionState as? DetectionUiState.Ready)?.inventory,
                                selectedAddresses = selectedAddresses,
                                onAddressToggle = { key ->
                                    selectedAddresses = if (key in selectedAddresses) {
                                        selectedAddresses - key
                                    } else {
                                        selectedAddresses + key
                                    }
                                },
                                selectedProtocols = selectedProtocols,
                                onProtocolToggle = { choice ->
                                    selectedProtocols = if (choice in selectedProtocols) {
                                        selectedProtocols - choice
                                    } else {
                                        selectedProtocols + choice
                                    }
                                },
                                selectedClients = selectedClients,
                                onClientToggle = { choice ->
                                    selectedClients = if (choice in selectedClients) {
                                        selectedClients - choice
                                    } else {
                                        selectedClients + choice
                                    }
                                },
                                onNext = { execution, replacingExisting ->
                                    when (execution) {
                                        is PlanExecution.Legacy -> {
                                            val previousRunId = when (val previous = deployment.state) {
                                                is DeploymentState.Failed -> previous.runId
                                                is DeploymentState.ServerReady -> previous.runId
                                                else -> null
                                            }
                                            val started = deployment.start(
                                                execution.plan, username, password, replacingExisting,
                                            )
                                            if (started) {
                                                if (replacingExisting && previousRunId != null) history.finishRun(previousRunId)
                                                completed.clear()
                                                useCompositeView = false
                                                autoAdvancePending = true
                                                page = Page.PROGRESS
                                            }
                                            started
                                        }
                                        is PlanExecution.Composite -> {
                                            val previousRunId = when (val previous = compositeDeployment.state) {
                                                is CompositeDeploymentState.NeedsReconciliation -> previous.record.runId
                                                is CompositeDeploymentState.Settled -> previous.record.runId
                                                else -> null
                                            }
                                            val started = compositeDeployment.start(
                                                execution.plan, username, password, replacingExisting,
                                            )
                                            if (started) {
                                                if (replacingExisting && previousRunId != null) history.finishRun(previousRunId)
                                                completed.clear()
                                                useCompositeView = true
                                                autoAdvancePending = true
                                                page = Page.PROGRESS
                                            }
                                            started
                                        }
                                    }
                                },
                            )
                        }
                        Page.PROGRESS -> {
                            if (progressAction != ProgressAction.HIDDEN) item {
                                ProgressActionButton(progressAction) {
                                    when (progressAction) {
                                        ProgressAction.RETRY -> if (useCompositeView) {
                                            activeCompositeRecord?.let { record ->
                                                autoAdvancePending = compositeDeployment.start(
                                                    record.plan, username, password)
                                            }
                                        } else {
                                            legacyPlan?.let { plan ->
                                                deployment.start(plan, username, password)
                                                autoAdvancePending = deployment.state is DeploymentState.Running
                                            }
                                        }
                                        ProgressAction.ENTER_CREDENTIALS,
                                        ProgressAction.CHECK_CONNECTION -> {
                                            progressHost?.let { target ->
                                                if (!host.equals(target, ignoreCase = true) ||
                                                    progressAction == ProgressAction.ENTER_CREDENTIALS) password = ""
                                                host = target
                                                username = "root"
                                                detection.invalidate()
                                                ping.invalidate()
                                                selectedAddresses = emptySet()
                                                page = Page.CONNECT
                                            }
                                        }
                                        ProgressAction.REVIEW_PLAN -> page = Page.PLAN
                                        ProgressAction.NEXT -> page = Page.RESULT
                                        ProgressAction.HIDDEN, ProgressAction.RUNNING -> Unit
                                    }
                                }
                            }
                            item {
                                if (useCompositeView) CompositeProgressPage(
                                    completed.compositeSnapshot ?: compositeState)
                                else ProgressPage(completed.legacySnapshot ?: deploymentState)
                            }
                        }
                        Page.RESULT -> item {
                            val resultRunId = if (useCompositeView) {
                                (completed.compositeSnapshot ?: compositeState as? CompositeDeploymentState.Settled)
                                    ?.takeIf { it.completedExport() != null }?.record?.runId
                            } else (completed.legacySnapshot ?: deploymentState as? DeploymentState.ServerReady)?.runId
                            val saveRecord: () -> Unit = {
                                resultRunId?.let { scope.launch { saveAndReleaseCompletedRun(it) } }
                            }
                            val requestSubscription: () -> Unit = {
                                subscriptionRequest?.let { request ->
                                    val requestPassword = completed.deliveryPassword ?: password.takeIf {
                                        host.equals(request.sshHost, ignoreCase = true) && username == "root"
                                    }
                                    if (!requestPassword.isNullOrBlank()) {
                                        subscription.request(request.runId, request.sshHost,
                                            request.publicHost, "root", requestPassword, request.yaml)
                                    } else {
                                        host = request.sshHost
                                        username = "root"
                                        password = ""
                                        detection.invalidate()
                                        page = Page.CONNECT
                                    }
                                }
                            }
                            if (useCompositeView) CompositeResultPage(
                                completed.compositeSnapshot ?: compositeState,
                                onDiscardUnstarted = {
                                    val runId = (compositeState as? CompositeDeploymentState.Settled)
                                        ?.record?.runId
                                    if (compositeDeployment.discardUnstarted()) {
                                        runId?.let(history::finishRun)
                                        page = Page.PLAN
                                    }
                                },
                                savingRecord = completed.savingRunId == resultRunId,
                                saveRecordError = completed.saveErrorRunId == resultRunId,
                                onSaveRecord = saveRecord,
                                probeState = nodeProbeState,
                                onTestNodes = { export ->
                                    (completed.compositeSnapshot ?: compositeState as?
                                        CompositeDeploymentState.Settled)?.record?.runId
                                        ?.let { nodeProbe.start(it, export) }
                                },
                                subscription = subscription.state,
                                onRequestSubscription = requestSubscription,
                            )
                            else ResultPage(completed.legacySnapshot ?: deploymentState,
                                subscription.state, requestSubscription,
                                savingRecord = completed.savingRunId == resultRunId,
                                saveRecordError = completed.saveErrorRunId == resultRunId,
                                onSaveRecord = saveRecord,
                            )
                        }
                    }
                }
            }
            ScrollToTopAction(
                listState = listState,
                enabled = scrollToTopEnabled && pageEntrance.value == 1f,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            )
        }
    }
    deleteCandidateId?.let { candidate ->
        HistoryDeleteDialog(
            deleting = deletingHistoryId != null,
            onDismiss = { deleteCandidateId = null },
            onConfirm = {
                if (deletingHistoryId == null) {
                    deletingHistoryId = candidate
                    scope.launch {
                        val deleted = history.deleteRecord(candidate)
                        deletingHistoryId = null
                        if (deleted) {
                            if (selectedHistoryId == candidate) selectedHistoryId = null
                            deleteCandidateId = null
                            showNotice(R.string.history_delete_success)
                        } else showNotice(R.string.history_delete_failed)
                    }
                }
            },
        )
    }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 820)
@Composable
private fun OwnBridgePreview() {
    OwnBridgeTheme { OwnBridgeApp(AppLanguage.SYSTEM) { true } }
}
