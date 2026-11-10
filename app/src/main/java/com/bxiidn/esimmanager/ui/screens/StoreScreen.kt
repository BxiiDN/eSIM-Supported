package com.bxiidn.esimmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bxiidn.esimmanager.core.Fmt
import com.bxiidn.esimmanager.data.db.OrderEntity
import com.bxiidn.esimmanager.device.DeviceReport
import com.bxiidn.esimmanager.provider.CatalogScope
import com.bxiidn.esimmanager.provider.StorePackage
import com.bxiidn.esimmanager.ui.StoreViewModel
import com.bxiidn.esimmanager.ui.components.EmptyState
import com.bxiidn.esimmanager.ui.components.KeyValue
import com.bxiidn.esimmanager.ui.components.Notice
import com.bxiidn.esimmanager.ui.components.Panel
import com.bxiidn.esimmanager.ui.components.ScreenHeader

@Composable
fun StoreScreen(
    vm: StoreViewModel,
    report: DeviceReport,
    onOpenSettings: () -> Unit,
    onOpenProfile: (Long) -> Unit,
) {
    val configured by vm.configured.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val orders by vm.orders.collectAsStateWithLifecycle()

    var showCountries by remember { mutableStateOf(false) }
    var showOrders by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<StorePackage?>(null) }

    LaunchedEffect(configured) { vm.ensureProvider() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 28.dp, top = 24.dp),
    ) {
        ScreenHeader(
            title = "eSIM Store",
            subtitle = "Katalog paket data internasional langsung dari API penyedia.",
            actions = {
                if (orders.isNotEmpty()) {
                    OutlinedButton(onClick = { showOrders = true }) {
                        Icon(Icons.Outlined.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Pesanan (${orders.size})")
                    }
                }
                if (state.provider != null) {
                    IconButton(onClick = { vm.load(); vm.refreshBalance() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Muat ulang", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            },
        )
        Spacer(Modifier.height(18.dp))

        if (configured.isEmpty()) {
            Panel {
                EmptyState(
                    icon = Icons.Outlined.Storefront,
                    title = "Hubungkan penyedia eSIM",
                    message = "Katalog dan pembelian memakai API resmi penyedia (eSIM Access atau Airalo Partner). " +
                        "Masukkan kredensial API akun Anda di Pengaturan — aplikasi ini tidak menampilkan katalog tiruan.",
                    action = { Button(onClick = onOpenSettings) { Text("Buka Pengaturan") } },
                )
            }
            return@Column
        }

        // Kontrol: penyedia, cakupan, negara.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            configured.forEach { id ->
                FilterChip(
                    selected = state.provider == id,
                    onClick = { vm.selectProvider(id) },
                    label = { Text(id.label) },
                )
            }
            Box(
                Modifier
                    .padding(horizontal = 6.dp)
                    .width(1.dp)
                    .height(24.dp)
                    .background(MaterialTheme.colorScheme.outline),
            )
            CatalogScope.entries.forEach { scope ->
                FilterChip(
                    selected = state.scope == scope,
                    onClick = { vm.selectScope(scope) },
                    label = { Text(scope.label) },
                )
            }
            if (state.scope == CatalogScope.COUNTRY) {
                OutlinedButton(onClick = { showCountries = true }) {
                    Text(
                        if (state.countryCode.isBlank()) {
                            "Pilih negara"
                        } else {
                            "${Fmt.flag(state.countryCode)}  ${Fmt.countryName(state.countryCode)}"
                        },
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            state.balance?.let { balance ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Saldo ${Fmt.money(balance.amount, balance.currency)}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (!report.canInstallEsim) {
            Notice(
                "Paket yang dibeli tersimpan sebagai profil di aplikasi ini. Perangkat ini tidak dapat memasang eSIM, " +
                    "jadi profil dipasang dengan memindai QR-nya dari ponsel yang mendukung eSIM.",
            )
            Spacer(Modifier.height(16.dp))
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
                state.loading -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface, strokeWidth = 2.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Memuat katalog dari ${state.provider?.label.orEmpty()}…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.error != null -> Column {
                    Notice(state.error.orEmpty())
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { vm.load() }) { Text("Coba lagi") }
                }
                state.scope == CatalogScope.COUNTRY && state.countryCode.isBlank() -> EmptyState(
                    icon = Icons.Outlined.Public,
                    title = "Pilih negara tujuan",
                    message = "Katalog dimuat dari penyedia setelah Anda memilih negara, atau pilih \"Regional & global\".",
                    action = { Button(onClick = { showCountries = true }) { Text("Pilih negara") } },
                )
                state.loadedOnce && state.packages.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.Search,
                    title = "Tidak ada paket",
                    message = "Penyedia tidak mengembalikan paket untuk pilihan ini.",
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.packages, key = { it.provider.name + ":" + it.id }) { pkg ->
                        PackageCard(pkg = pkg, onBuy = { confirm = pkg })
                    }
                }
            }
        }
    }

    if (showCountries) {
        CountryPickerDialog(
            onDismiss = { showCountries = false },
            onPick = { code ->
                showCountries = false
                vm.selectCountry(code)
            },
        )
    }

    if (showOrders) {
        OrdersDialog(orders = orders, onDismiss = { showOrders = false })
    }

    confirm?.let { pkg ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Beli paket ini?") },
            text = {
                Column {
                    Text(pkg.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    KeyValue("Penyedia", pkg.provider.label)
                    KeyValue("Wilayah", pkg.countryName)
                    KeyValue("Kuota", if (pkg.unlimited) "Tanpa batas" else Fmt.bytes(pkg.dataBytes))
                    KeyValue("Masa berlaku", "${pkg.validityDays} hari")
                    KeyValue("Harga", Fmt.money(pkg.price, pkg.currency))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Ini transaksi sungguhan: ${Fmt.money(pkg.price, pkg.currency)} akan ditagihkan ke akun " +
                            "${pkg.provider.label} Anda dan satu profil eSIM diterbitkan. Pengembalian dana mengikuti " +
                            "kebijakan penyedia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = null
                        vm.purchase(pkg)
                    },
                ) { Text("Beli sekarang") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Batal") } },
        )
    }

    if (state.purchasing) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(Modifier.padding(26.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(18.dp))
                    Text("Memproses pesanan di penyedia…", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    state.outcome?.let { outcome ->
        AlertDialog(
            onDismissRequest = { vm.consumeOutcome() },
            title = { Text(outcome.title) },
            text = { Text(outcome.message) },
            confirmButton = {
                if (outcome.success && outcome.profileId > 0) {
                    TextButton(
                        onClick = {
                            vm.consumeOutcome()
                            onOpenProfile(outcome.profileId)
                        },
                    ) { Text("Lihat profil") }
                } else {
                    TextButton(onClick = { vm.consumeOutcome() }) { Text("Tutup") }
                }
            },
            dismissButton = {
                if (outcome.success && outcome.profileId > 0) {
                    TextButton(onClick = { vm.consumeOutcome() }) { Text("Tutup") }
                }
            },
        )
    }
}

@Composable
private fun PackageCard(pkg: StorePackage, onBuy: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.flag(pkg.countryCode.takeIf { it.length == 2 }), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        pkg.countryName.ifBlank { "Multi-negara" },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        pkg.operatorName.ifBlank { pkg.provider.label },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                if (pkg.unlimited) "Tanpa batas" else Fmt.bytes(pkg.dataBytes),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                "${pkg.validityDays} hari" + if (pkg.networkTypes.isNotBlank()) " · ${pkg.networkTypes}" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                pkg.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (pkg.coverage.size > 1) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Cakupan: " + pkg.coverage.take(6).joinToString(", ") +
                        if (pkg.coverage.size > 6) " +${pkg.coverage.size - 6} lainnya" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Fmt.money(pkg.price, pkg.currency), style = MaterialTheme.typography.titleLarge)
                    if (pkg.retailPrice > 0 && pkg.retailPrice != pkg.price) {
                        Text(
                            "Ritel ${Fmt.money(pkg.retailPrice, pkg.currency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Button(onClick = onBuy) { Text("Beli") }
            }
        }
    }
}

@Composable
private fun CountryPickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val countries = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            Fmt.countries
        } else {
            Fmt.countries.filter { it.name.contains(q, ignoreCase = true) || it.code.equals(q, ignoreCase = true) }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.9f)
                .heightIn(max = 680.dp)
                .padding(vertical = 24.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(Modifier.padding(22.dp)) {
                Text("Pilih negara", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Cari negara") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(countries, key = { it.code }) { country ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onPick(country.code) }
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(Fmt.flag(country.code), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(14.dp))
                            Text(country.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(
                                country.code,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Tutup") }
            }
        }
    }
}

@Composable
private fun OrdersDialog(orders: List<OrderEntity>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth(0.92f)
                .heightIn(max = 680.dp)
                .padding(vertical = 24.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(Modifier.padding(22.dp)) {
                Text("Riwayat pesanan", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Pesanan yang dibuat dari aplikasi ini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(orders, key = { it.id }) { order ->
                        Column(Modifier.padding(vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    order.packageTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(Fmt.money(order.price, order.currency), style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                listOf(providerLabel(order.providerId), order.countryName, order.orderRef, Fmt.dateTime(order.createdAt))
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Tutup") }
            }
        }
    }
}
