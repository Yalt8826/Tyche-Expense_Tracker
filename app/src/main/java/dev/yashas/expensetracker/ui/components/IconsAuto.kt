package dev.yashas.expensetracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.outlined.Warning

/** Central indirection: DB stores icon *names*, UI maps to Material icons. */
object IconsAuto {
    val Restaurant = Icons.Filled.Restaurant
    val ShoppingBag = Icons.Filled.ShoppingBag
    val Cafe = Icons.Filled.LocalCafe
    val Car = Icons.Filled.DirectionsCar
    val Receipt = Icons.Filled.Receipt
    val Phone = Icons.Filled.PhoneAndroid
    val Bolt = Icons.Filled.Bolt
    val Movie = Icons.Filled.Movie
    val Health = Icons.Filled.Favorite
    val School = Icons.Filled.School
    val Payments = Icons.Filled.Payments
    val Category = Icons.Filled.Category
    val Check = Icons.Filled.Check
    val Warning = Icons.Outlined.Warning
    val Edit = Icons.Filled.Edit
}
