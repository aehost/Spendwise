package com.spendwise.app.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.spendwise.app.presentation.screens.auth.AuthScreen
import com.spendwise.app.presentation.screens.budget.BudgetScreen
import com.spendwise.app.presentation.screens.cards.CardsScreen
import com.spendwise.app.presentation.screens.cashflow.CashFlowScreen
import com.spendwise.app.presentation.screens.coach.AiCoachScreen
import com.spendwise.app.presentation.screens.debt.DebtPayoffScreen
import com.spendwise.app.presentation.screens.goals.GoalsScreen
import com.spendwise.app.presentation.screens.home.HomeScreen
import com.spendwise.app.presentation.screens.iou.IouScreen
import com.spendwise.app.presentation.screens.loans.LoansScreen
import com.spendwise.app.presentation.screens.money.MoneyScreen
import com.spendwise.app.presentation.screens.networth.NetWorthScreen
import com.spendwise.app.presentation.screens.report.MonthlyReportScreen
import com.spendwise.app.presentation.screens.score.HealthScoreScreen
import com.spendwise.app.presentation.screens.settings.SettingsScreen
import com.spendwise.app.presentation.screens.setup.SetupScreen
import com.spendwise.app.presentation.screens.tax.TaxPlanningScreen
import com.spendwise.app.presentation.screens.tools.ToolsScreen
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
    object Tools         : Screen("tools")
    object Settings      : Screen("settings")
    object MonthlyReport : Screen("monthly_report")
    object AiCoach       : Screen("ai_coach")
    object HealthScore   : Screen("health_score")
    object CashFlow      : Screen("cash_flow")
    object DebtPayoff    : Screen("debt_payoff")
    object TaxPlanning   : Screen("tax_planning")
    object Iou           : Screen("iou")
    object Goals         : Screen("goals")
    object Budget        : Screen("budget")
    object NetWorth      : Screen("net_worth")
}

data class NavItem(val route: String, val label: String, val icon: ImageVector)

val BOTTOM_NAV_ITEMS = listOf(
    NavItem(Screen.Home.route,         "Home",    Icons.Filled.Home),
    NavItem(Screen.Transactions.route, "Spends",  Icons.Filled.Receipt),
    NavItem(Screen.Cards.route,        "Cards",   Icons.Filled.CreditCard),
    NavItem(Screen.Money.route,        "Money",   Icons.Filled.TrendingUp),
    NavItem(Screen.Tools.route,        "Tools",   Icons.Filled.Apps),
)

@Composable
fun SpendWiseNavGraph(startRoute: String) {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentDest = currentBackStack?.destination
    val showBottomBar = BOTTOM_NAV_ITEMS.any { it.route == currentDest?.route }

    // Auto-redirect to Login when the session is cleared (e.g. expired refresh
    // token). Fixes the "couldn't connect" dead-end that needed a manual logout.
    val session: SessionViewModel = hiltViewModel()
    val loggedIn by session.loggedIn.collectAsState()
    LaunchedEffect(loggedIn, currentDest?.route) {
        val route = currentDest?.route
        if (!loggedIn && route != null && route != Screen.Auth.route && route != Screen.Setup.route) {
            navController.navigate(Screen.Auth.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

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
            NavHost(
                navController = navController,
                startDestination = startRoute,
                enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 14 } },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { it / 14 } }
            ) {
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
                composable(Screen.Tools.route) {
                    ToolsScreen(
                        onAiCoach    = { navController.navigate(Screen.AiCoach.route) },
                        onHealthScore = { navController.navigate(Screen.HealthScore.route) },
                        onCashFlow   = { navController.navigate(Screen.CashFlow.route) },
                        onDebtPayoff = { navController.navigate(Screen.DebtPayoff.route) },
                        onTax        = { navController.navigate(Screen.TaxPlanning.route) },
                        onIou        = { navController.navigate(Screen.Iou.route) },
                        onLoans      = { navController.navigate(Screen.Loans.route) },
                        onReport     = { navController.navigate(Screen.MonthlyReport.route) },
                        onGoals      = { navController.navigate(Screen.Goals.route) },
                        onBudget     = { navController.navigate(Screen.Budget.route) },
                        onNetWorth   = { navController.navigate(Screen.NetWorth.route) },
                        onSettings   = { navController.navigate(Screen.Settings.route) }
                    )
                }
                composable(Screen.Goals.route) {
                    GoalsScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onLogout        = { navController.navigate(Screen.Auth.route) { popUpTo(0) { inclusive = true } } },
                        onMonthlyReport = { navController.navigate(Screen.MonthlyReport.route) }
                    )
                }
                composable(Screen.MonthlyReport.route) {
                    MonthlyReportScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.AiCoach.route) {
                    AiCoachScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.HealthScore.route) {
                    HealthScoreScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.CashFlow.route) {
                    CashFlowScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.DebtPayoff.route) {
                    DebtPayoffScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.TaxPlanning.route) {
                    TaxPlanningScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.Iou.route) {
                    IouScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.Budget.route) {
                    BudgetScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.NetWorth.route) {
                    NetWorthScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
