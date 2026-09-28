package dev.yashas.expensetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.yashas.expensetracker.data.db.entity.TransactionEntity
import dev.yashas.expensetracker.domain.model.MoneyFormat
import dev.yashas.expensetracker.domain.model.Provenance
import dev.yashas.expensetracker.domain.model.TxnType
import dev.yashas.expensetracker.ui.theme.SeriesAmber
import dev.yashas.expensetracker.ui.theme.SeriesBlue
import dev.yashas.expensetracker.ui.theme.SeriesCyan
import dev.yashas.expensetracker.ui.theme.SeriesLime
import dev.yashas.expensetracker.ui.theme.SeriesPink
import dev.yashas.expensetracker.ui.theme.SeriesViolet
import dev.yashas.expensetracker.ui.theme.SemanticCoral
import dev.yashas.expensetracker.ui.theme.SemanticGreen
import dev.yashas.expensetracker.ui.theme.TextSecondary
import dev.yashas.expensetracker.ui.components.parseHexColor

fun categoryColor(token: String): Color {
    // User-created tags store real hex values; parse them so every surface picks the color up.
    if (token != null && token.startsWith("#")) {
        return parseHexColor(token)
    }
    return when (token) {
        "series_violet" -> SeriesViolet
        "series_cyan" -> SeriesCyan
        "series_amber" -> SeriesAmber
        "series_pink" -> SeriesPink
        "series_lime" -> SeriesLime
        "series_blue" -> SeriesBlue
        "series_grey" -> TextSecondary
        else -> SeriesViolet
    }
}

fun categoryIcon(name: String): ImageVector = when (name) {
    "restaurant", "fastfood" -> IconsAuto.Restaurant
    "shopping_basket", "shopping_bag" -> IconsAuto.ShoppingBag
    "local_cafe" -> IconsAuto.Cafe
    "directions_car", "local_taxi", "local_gas_station" -> IconsAuto.Car
    "receipt" -> IconsAuto.Receipt
    "smartphone" -> IconsAuto.Phone
    "bolt" -> IconsAuto.Bolt
    "movie" -> IconsAuto.Movie
    "medication" -> IconsAuto.Health
    "school" -> IconsAuto.School
    "payments" -> IconsAuto.Payments
    else -> IconsAuto.Category
}

/** Cross-cutting provenance mark (01-SCREENS): icon only — ✓ auto · ⚠ review · ✎ manual. */
@Composable
fun ProvenanceIcon(provenance: Provenance, modifier: Modifier = Modifier) {
    val (desc, icon, tint) = when (provenance) {
        Provenance.AUTO_CONFIRMED -> Triple("Auto-confirmed", IconsAuto.Check, SemanticGreen)
        Provenance.AUTO_REVIEW -> Triple("Needs review", IconsAuto.Warning, SeriesAmber)
        Provenance.MANUAL -> Triple("Added manually", IconsAuto.Edit, TextSecondary)
    }
    Icon(
        imageVector = icon,
        contentDescription = desc,
        tint = tint,
        modifier = modifier.size(13.dp),
    )
}

/** Ledger row (S9): category icon, merchant, amount, provenance chip. */
@Composable
fun TxnRow(
    txn: TransactionEntity,
    categoryName: String?,
    categoryIconName: String?,
    categoryColorToken: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val isPositive = txn.type == TxnType.INCOME || txn.type == TxnType.REFUND
    val amountColor = when {
        txn.type == TxnType.TRANSFER -> TextSecondary
        isPositive -> SemanticGreen
        else -> MaterialTheme.colorScheme.onSurface
    }
    val accent = categoryColor(categoryColorToken ?: "")
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        border = BorderStroke(
            width = 1.2.dp,
            color = if (categoryColorToken != null) accent.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(categoryColor(categoryColorToken ?: "series_violet").copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (categoryIconName != null) {
                // confirmed row: its category icon
                Icon(
                    imageVector = categoryIcon(categoryIconName),
                    contentDescription = categoryName,
                    tint = categoryColor(categoryColorToken ?: ""),
                    modifier = Modifier.size(20.dp),
                )
            } else {
                // unconfirmed row: merchant-initial avatar (UI-AUDIT R3)
                Text(
                    text = (txn.merchantName ?: txn.vpa ?: "?").trim().take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = categoryColor(categoryColorToken ?: "series_violet"),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = txn.merchantName ?: txn.vpa ?: "Transaction",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ProvenanceIcon(txn.provenance)
                if (txn.type == TxnType.TRANSFER) {
                    Text(text = "transfer", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                if (txn.type == TxnType.REFUND) {
                    Text(text = "refund", style = MaterialTheme.typography.labelSmall, color = SemanticGreen)
                }
            }
        }
        Text(
            text = (if (isPositive) "+" else "") + MoneyFormat.formatPaiseExact(txn.amountPaise),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = amountColor,
        )
        }
    }
}
