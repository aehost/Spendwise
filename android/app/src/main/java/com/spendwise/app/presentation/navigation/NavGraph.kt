package com.spendwise.app.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.spendwise.app.presentation.screens.auth.AuthScreen
import com.spendwise.app.presentation.screens.cards.CardsScreen
import com.spendwise.app.presentation.screens.home.HomeScreen
import com.spendwise.app.presentation.screens.loans.LoansScreen
import com.spendwise.app.presentation.screens.money.MoneyScreen
import com.spendwise.app.presentation.screens.report.MonthlyReportScreen
import com.spendwise.app.presentation.screens.settings.SettingsScreen
import com.spendwise.app.presentation.screens.setup.SetupScreen
import com.spendwise.app.presentation.screens.transactions.TransactionListScreen
import com.spendwise.app.presentation.theme.Primary
import com.spendwise.app.presentation.theme.TextMuted
import com.spendwise.app.presentation.theme.CardBg

sealed class Screen(val route: String) {
    object Setup         : Screen("setup")
    object Auth          : Screen("auth")
    object Home          : Screen("home")
    object Transactions  : Screen("transactions")
    object Cards         : Screen("cards")
    object Loans         : Screen("loans")
    object Money         : Screen("money")
    object Settings      : Screen("settings")
    object MonthlyReport : Screen("monthly_report")
}

data class NavItem(val route: String, val label: String, val icon: ImageVector)

val BOTTOM_NAV_ITEMS = listOf(
    NavItem(Screen.Home.route,         "Home",    Icons.Filled.Home),
    NavItem(Screen.Transactions.route, "Spends",  Icons.Filled.Receipt),
    NavItem(Screen.Cards.route,        "Cards",   Icons.Filled.CreditCard),
    NavItem(Screen.Loans.route,        "Loans",   Icons.Filled.AccountBalance),
    NavItem(Screen.Money.route,        "Money",   Icons.Filled.TrendingUp),
)

@Composable
fun SpendWiseNavGraph(startRoute: String) {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentDest = currentBackStack?.destination
    val showBottomBar = BOTTOM_NAV_ITEMS.any { it.route == currentDest?.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = CardBg) {
                    BOTTOM_NAV_ITEMS.forEach { item ->
                        val selected = currentDest?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            icon     = { Icon(item.icon, item.label) },
                            label    = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                            selected = selected,
                            colors   = NavigationBarItemDefaults.colors(
                                selectedIconColor   = Primary,
                                selectedTextColor   = Primary,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor      = Primary.copy(alpha = 0.15f)
                            ),
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController = navController, startDestination = startRoute) {
                composable(Screen.Setup.route) {
                    SetupScreen(onDone = { navController.navigate(Screen.Auth.route) { popUpTo(Screen.Setup.route) { inclusive = true } } })
                }
                composable(Screen.Auth.route) {
                    AuthScreen(onAuthSuccess = { navController.navigate(Screen.Home.route) { popUpTo(Screen.Auth.route) { inclusive = true } } })
                }
                composable(Screen.Home.route) {
                    HomeScreen(onSettings = { navController.navigate(Screen.Settings.route) })
                }
                composable(Screen.Transactions.route) { TransactionListScreen() }
                composable(Screen.Cards.route)        { CardsScreen() }
                composable(Screen.Loans.route)        { LoansScreen() }
                composable(Screen.Money.route)        { MoneyScreen() }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onLogout        = { navController.navigate(Screen.Auth.route) { popUpTo(0) { inclusive = true } } },
                        onMonthlyReport = { navController.navigate(Screen.MonthlyReport.route) }
                    )
                }
                composable(Screen.MonthlyReport.route) {
                    MonthlyReportScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
