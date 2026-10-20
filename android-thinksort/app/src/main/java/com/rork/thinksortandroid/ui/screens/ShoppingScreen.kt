package com.rork.thinksortandroid.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.thinksortandroid.data.ShoppingItem
import com.rork.thinksortandroid.domain.capitalizedWords
import com.rork.thinksortandroid.ui.BrainViewModel
import com.rork.thinksortandroid.ui.components.CardRow
import com.rork.thinksortandroid.ui.components.CardTextField
import com.rork.thinksortandroid.ui.components.CheckCircle
import com.rork.thinksortandroid.ui.components.CircleIconButton
import com.rork.thinksortandroid.ui.components.EmptyCard
import com.rork.thinksortandroid.ui.components.LargeTitle
import com.rork.thinksortandroid.ui.components.RowDivider
import com.rork.thinksortandroid.ui.components.ScreenColumn
import com.rork.thinksortandroid.ui.components.SectionCard
import com.rork.thinksortandroid.ui.components.SoftButton
import com.rork.thinksortandroid.ui.components.StrikeText
import com.rork.thinksortandroid.ui.components.TextLink
import com.rork.thinksortandroid.ui.components.card
import com.rork.thinksortandroid.ui.theme.Think

@Composable
fun ShoppingScreen(vm: BrainViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val groups = remember(data) { vm.shoppingByStore(data) }
    var newItem by rememberSaveable { mutableStateOf("") }
    var newStore by rememberSaveable { mutableStateOf("") }

    fun add() {
        val name = newItem.trim()
        if (name.isEmpty()) return
        val store = newStore.trim()
        vm.addShoppingItem(name, if (store.isEmpty()) null else store.capitalizedWords)
        newItem = ""
    }

    Column(Modifier.fillMaxSize()) {
        LargeTitle("Shopping")
        ScreenColumn {
            Column(Modifier.fillMaxWidth().card().padding(horizontal = 8.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AddShoppingCart, null, tint = Think.colors.accent, modifier = Modifier.padding(start = 8.dp).size(22.dp))
                    CardTextField(newItem, { newItem = it }, "Add an item", onDone = ::add)
                }
                RowDivider(leading = 40.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Place, null, tint = Think.colors.inkTertiary, modifier = Modifier.padding(start = 8.dp).size(22.dp))
                    CardTextField(newStore, { newStore = it }, "Store (optional)", modifier = Modifier.weight(1f), onDone = ::add)
                    TextLink("Add", ::add, color = Think.colors.accent, enabled = newItem.isNotBlank())
                }
            }

            if (groups.isEmpty()) {
                EmptyCard(
                    Icons.Rounded.ShoppingCart,
                    "Nothing on the list",
                    "Say “I need batteries, dog food and paper towels” in a brain dump and they'll show up here.",
                )
            } else {
                groups.forEach { (store, items) ->
                    SectionCard(title = store) {
                        items.forEachIndexed { index, item ->
                            ShoppingRow(item, onToggle = { vm.toggleShoppingItem(item.id) }, onDelete = { vm.deleteShoppingItem(item.id) })
                            if (index < items.size - 1) RowDivider()
                        }
                    }
                }
                if (data.shopping.any { it.isPurchased }) {
                    SoftButton("Clear picked-up items", { vm.clearPurchasedShopping() }, tint = Think.colors.inkSecondary, compact = true)
                }
                Text(
                    "Tap to check off · hold to delete",
                    style = MaterialTheme.typography.bodySmall,
                    color = Think.colors.inkTertiary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShoppingRow(item: ShoppingItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var confirmDelete by remember { mutableStateOf(false) }
    CardRow(
        onClick = null,
        modifier = Modifier.combinedClickable(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle()
            },
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                confirmDelete = !confirmDelete
            },
        ),
    ) {
        CheckCircle(item.isPurchased)
        StrikeText(item.name, item.isPurchased, Modifier.weight(1f))
        if (confirmDelete) {
            CircleIconButton(Icons.Rounded.DeleteOutline, "Delete ${item.name}", onDelete, background = Think.colors.coralSoft, tint = Think.colors.coral)
        } else if (item.isPurchased) {
            Text("Got it", style = MaterialTheme.typography.bodySmall, color = Think.colors.inkTertiary)
        }
    }
}
