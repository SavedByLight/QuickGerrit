package com.quickgerrit.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quickgerrit.app.QuickGerritApp
import com.quickgerrit.app.ui.accounts.AccountsScreen
import com.quickgerrit.app.ui.accounts.AccountsViewModel
import com.quickgerrit.app.ui.change.ChangeDetailScreen
import com.quickgerrit.app.ui.change.ChangeDetailViewModel
import com.quickgerrit.app.ui.change.DiffScreen
import com.quickgerrit.app.ui.changes.ChangesScreen
import com.quickgerrit.app.ui.changes.ChangesViewModel
import com.quickgerrit.app.ui.logs.LogsScreen
import com.quickgerrit.app.ui.projects.ProjectsScreen
import com.quickgerrit.app.ui.projects.ProjectsViewModel

sealed class Screen(val route: String) {
    data object Changes : Screen("changes")
    data object Projects : Screen("projects")
    data object Accounts : Screen("accounts")
    data object Logs : Screen("logs")
    data object ChangeDetail : Screen("change/{changeId}") {
        fun create(changeId: String) = "change/${java.net.URLEncoder.encode(changeId, "UTF-8")}"
    }
    data object Diff : Screen("diff/{changeId}/{revisionId}/{filePath}") {
        fun create(changeId: String, revisionId: String, filePath: String) =
            "diff/${java.net.URLEncoder.encode(changeId, "UTF-8")}/$revisionId/${java.net.URLEncoder.encode(filePath, "UTF-8")}"
    }
}

@Composable
fun QuickGerritNavGraph() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as QuickGerritApp
    val repo = app.repository

    NavHost(navController = navController, startDestination = Screen.Changes.route) {
        composable(Screen.Changes.route) {
            val vm: ChangesViewModel = viewModel(factory = ChangesViewModel.Factory(repo))
            ChangesScreen(
                viewModel = vm,
                onOpenChange = { id -> navController.navigate(Screen.ChangeDetail.create(id)) },
                onOpenAccounts = { navController.navigate(Screen.Accounts.route) },
                onOpenProjects = { navController.navigate(Screen.Projects.route) },
                onOpenLogs = { navController.navigate(Screen.Logs.route) }
            )
        }
        composable(Screen.Projects.route) {
            val vm: ProjectsViewModel = viewModel(factory = ProjectsViewModel.Factory(repo))
            ProjectsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenChange = { id -> navController.navigate(Screen.ChangeDetail.create(id)) }
            )
        }
        composable(Screen.Accounts.route) {
            val vm: AccountsViewModel = viewModel(factory = AccountsViewModel.Factory(repo))
            AccountsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Logs.route) {
            LogsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Screen.ChangeDetail.route,
            arguments = listOf(navArgument("changeId") { type = NavType.StringType })
        ) { entry ->
            val changeId = java.net.URLDecoder.decode(entry.arguments?.getString("changeId") ?: "", "UTF-8")
            val vm: ChangeDetailViewModel = viewModel(factory = ChangeDetailViewModel.Factory(repo, changeId))
            ChangeDetailScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenDiff = { rev, path ->
                    navController.navigate(Screen.Diff.create(changeId, rev, path))
                }
            )
        }
        composable(
            route = Screen.Diff.route,
            arguments = listOf(
                navArgument("changeId") { type = NavType.StringType },
                navArgument("revisionId") { type = NavType.StringType },
                navArgument("filePath") { type = NavType.StringType }
            )
        ) { entry ->
            val changeId = java.net.URLDecoder.decode(entry.arguments?.getString("changeId") ?: "", "UTF-8")
            val revisionId = entry.arguments?.getString("revisionId") ?: "current"
            val filePath = java.net.URLDecoder.decode(entry.arguments?.getString("filePath") ?: "", "UTF-8")
            DiffScreen(
                changeId = changeId,
                revisionId = revisionId,
                filePath = filePath,
                repository = repo,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
