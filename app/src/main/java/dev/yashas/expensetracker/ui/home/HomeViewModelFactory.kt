package dev.yashas.expensetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.yashas.expensetracker.data.repo.TxnRepository
import dev.yashas.expensetracker.data.repo.UserPrefs

fun homeFactory(repo: TxnRepository, prefs: UserPrefs): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(repo, prefs) as T
    }
