package com.example.thelastone.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.dialog
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.thelastone.ui.screens.AddActivityScreen
import com.example.thelastone.ui.screens.EditProfileScreen
import com.example.thelastone.ui.screens.explore.ExploreScreen
import com.example.thelastone.ui.screens.FriendsScreen
import com.example.thelastone.ui.screens.InviteFriendsDialog
import com.example.thelastone.ui.screens.PickPlaceScreen
import com.example.thelastone.ui.screens.PreviewTripScreen
import com.example.thelastone.ui.screens.ProfileScreen
import com.example.thelastone.ui.screens.SavedScreen
import com.example.thelastone.ui.screens.SearchPlacesScreen
import com.example.thelastone.ui.screens.SearchUsersScreen
import com.example.thelastone.ui.screens.TripChatScreen
import com.example.thelastone.ui.screens.TripDetailScreen
import com.example.thelastone.ui.screens.form.CreateTripFormScreen
import com.example.thelastone.ui.screens.myTrips.MyTripsScreen
import com.example.thelastone.ui.screens.auth.LoginScreen
import com.example.thelastone.ui.screens.auth.RegisterScreen
import com.example.thelastone.utils.encodePlaceArg
import com.example.thelastone.vm.RootViewModel
import com.example.thelastone.vm.TripDetailViewModel
import com.example.thelastone.vm.TripFormViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold() {
    val rootVm: RootViewModel = hiltViewModel()
    val auth by rootVm.auth.collectAsState()

    if (auth == null) {
        // 未登入：自己一顆 NavController
        val authNav = rememberNavController()
        AuthNavHost(nav = authNav)
    } else {
        // 已登入：自己一顆 NavController
        val mainNav = rememberNavController()
        MainScaffold(nav = mainNav)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(nav: NavHostController) {
    val backStack by nav.currentBackStackEntryAsState()
    val currentDest = backStack?.destination
    val isTopLevel = remember(currentDest) { TOP_LEVEL_DESTINATIONS.any { it.route == currentDest?.route } }
    val scroll = pinnedScrollBehavior()

    val showTripChat: Boolean = if (currentDest?.route == TripRoutes.Detail) {
        val detailEntry = remember(backStack) { nav.getBackStackEntry(TripRoutes.Detail) }
        val detailVm: TripDetailViewModel = hiltViewModel(detailEntry)
        val perms by detailVm.perms.collectAsState()
        perms?.canChat == true
    } else false

    val showInvite: Boolean = if (currentDest?.route == TripRoutes.Detail) {
        val detailEntry = remember(backStack) { nav.getBackStackEntry(TripRoutes.Detail) }
        val detailVm: TripDetailViewModel = hiltViewModel(detailEntry)
        val perms by detailVm.perms.collectAsState()
        perms?.canChat == true
    } else false

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,      // MTB: app 殼 = surface
        contentColor   = MaterialTheme.colorScheme.onSurface,
        topBar = {
            if (currentDest?.route !in setOf(MiscRoutes.SearchPlaces, MiscRoutes.SearchPlacesPick, MiscRoutes.SearchUsers)) {
                AppTopBar(
                    destination = currentDest,
                    isTopLevel = isTopLevel,
                    onBack = { nav.navigateUp() },
                    onExploreSearch = { nav.navigate(MiscRoutes.SearchPlaces) },
                    onFriendsSearch = { nav.navigate(MiscRoutes.SearchUsers) },
                    onEditProfile = { nav.navigate(MiscRoutes.EditProfile) },
                    onOpenTripChat = {
                        val tripId = backStack?.arguments?.getString("tripId") ?: return@AppTopBar
                        nav.navigate(TripRoutes.chat(tripId))
                    },
                    onInvite = {
                        val tripId = backStack?.arguments?.getString("tripId") ?: return@AppTopBar
                        nav.navigate(TripRoutes.invite(tripId))
                    },
                    onOpenTripMore = { /* TODO */ },
                    showTripChat = showTripChat,
                    showInvite = showInvite,
                    scrollBehavior = scroll
                )
            }
        },
        bottomBar = {
            if (currentDest?.route !in NO_BOTTOM_BAR_ROUTES) {
                AppBottomBar(nav = nav, currentDestination = currentDest)
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        contentWindowInsets = WindowInsets.safeDrawing,
        floatingActionButton = {
            if (currentDest?.route == Root.MyTrips.route) {
                FloatingActionButton(
                    onClick = { nav.navigate(TripRoutes.Flow) { launchSingleTop = true } },
                    containerColor = MaterialTheme.colorScheme.primary,     // MTB: FAB = primary
                    contentColor   = MaterialTheme.colorScheme.onPrimary
                ) { Icon(Icons.Filled.Add, null) }
            }
        }
    ) { padding ->
        MainNavHost(nav = nav, padding = padding)
    }
    BackHandler(enabled = !isTopLevel) { nav.navigateUp() }
}

@Composable
private fun MainNavHost(
    nav: NavHostController,
    padding: PaddingValues
) {
    NavHost(
        navController = nav,
//        startDestination = Root.Explore.route// ← 改回這個!
        startDestination = "trip_chat/69008b9417d53443dece1be4"// ← 改回這個!
    ) {

        // ===== 頂層分頁 =====
        composable(Root.Explore.route) {
            ExploreScreen(
                padding = padding,
                openPlace = { placeId -> /* 之後做附近景點用 */ },
                openTrip = { tripId -> nav.navigate(TripRoutes.detail(tripId)) }
            )
        }
        composable(Root.MyTrips.route) {
            MyTripsScreen(padding = padding, openTrip = { id -> nav.navigate(TripRoutes.detail(id)) })
        }
        composable(Root.Friends.route) { FriendsScreen(padding) }
        composable(Root.Saved.route)   { SavedScreen(padding = padding, openPlace = { /* TODO */ }) }
        composable(Root.Profile.route) { ProfileScreen(padding) }

        // ===== Trip 巢狀流程 =====
        navigation(startDestination = TripRoutes.Create, route = TripRoutes.Flow) {
            composable(TripRoutes.Create) { entry ->
                val parent = remember(entry) { nav.getBackStackEntry(TripRoutes.Flow) }
                val vm: TripFormViewModel = hiltViewModel(parent)
                CreateTripFormScreen(padding = padding, onPreview = { nav.navigate(TripRoutes.Preview) }, viewModel = vm)
            }
            composable(TripRoutes.Preview) { entry ->
                val parent = remember(entry) { nav.getBackStackEntry(TripRoutes.Flow) }
                val vm: TripFormViewModel = hiltViewModel(parent)
                PreviewTripScreen(
                    padding = padding,
                    onConfirmSaved = { tripId ->
                        nav.navigate(TripRoutes.detail(tripId)) {
                            popUpTo(TripRoutes.Flow) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onBack = { nav.navigateUp() },
                    viewModel = vm
                )
            }
        }

        // ===== Trip 細節與聊天 =====
        composable(
            route = TripRoutes.Detail,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
        ) { entry ->
            TripDetailScreen(
                padding = padding,
                onAddActivity = { id -> nav.navigate(TripRoutes.pickPlace(id)) },
                onEditActivity = { id, activityId ->
                    nav.navigate(TripRoutes.editActivity(id, activityId)) // ✅ 只帶 ID
                }
            )
        }

        dialog(
            route = TripRoutes.Invite,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType }),
            dialogProperties = DialogProperties(usePlatformDefaultWidth = false)
        ) { entry ->
            val tripId = entry.arguments!!.getString("tripId")!!
            InviteFriendsDialog(
                tripId = tripId,
                onDismiss = { nav.navigateUp() }
            )
        }


        // MainNavHost() 裡 TripRoutes.PickPlace 的 composable 區塊改成：
        composable(
            route = TripRoutes.PickPlace,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
        ) { entry ->
            val tripId = entry.arguments?.getString("tripId")!!

            PickPlaceScreen(
                padding = padding,
                onSearchClick = { nav.navigate(MiscRoutes.searchPlacesPick(tripId)) },
                onPick = { place ->
                    // 將 PlaceLite → JSON → Uri encode
                    val placeJson = android.net.Uri.encode(
                        encodePlaceArg(place)
                    )
                    nav.navigate(TripRoutes.addActivity(tripId, placeJson))
                }
            )
        }

        // 挑地點（從 Trip → PickPlace 進來）
        composable(
            route = MiscRoutes.SearchPlacesPick,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
        ) { entry ->
            val tripId = entry.arguments!!.getString("tripId")!!
            SearchPlacesScreen(
                onBack = { nav.navigateUp() },
                isPickingForTrip = true,
                onPick = { place ->
                    val placeJson = android.net.Uri.encode(encodePlaceArg(place))
                    nav.navigate(TripRoutes.addActivity(tripId, placeJson))
                }
            )
        }

        composable(
            route = TripRoutes.AddActivity,
            arguments = listOf(
                navArgument("tripId")    { type = NavType.StringType },
                navArgument("placeJson") { type = NavType.StringType }
            )
        ) { entry ->
            AddActivityScreen(
                padding = padding,
                tripId = entry.arguments!!.getString("tripId")!!,
                placeJson = entry.arguments!!.getString("placeJson")!!,
                nav = nav
            )
        }

        composable(
            route = TripRoutes.EditActivity,
            arguments = listOf(
                navArgument("tripId")    { type = NavType.StringType },
                navArgument("activityId"){ type = NavType.StringType }
            )
        ) { entry ->
            AddActivityScreen(
                padding = padding,
                tripId = entry.arguments!!.getString("tripId")!!,
                placeJson = null,                 // 編輯不帶 placeJson
                activityId = entry.arguments!!.getString("activityId"), // ✅ 新增
                nav = nav
            )
        }

//        composable(
//            route = TripRoutes.Chat,
//            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
//        ) {
//            TripChatScreen(padding = padding)
//        }

        // 🆕 聊天室路由 - 添加這個！
        composable(
            route = "trip_chat/{tripId}",
            arguments = listOf(
                navArgument("tripId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            TripChatScreen(
                padding = padding,
                viewModel = hiltViewModel()
            )
        }

        // ===== 其他 =====
        composable(MiscRoutes.SearchPlaces) {
            SearchPlacesScreen(
                onBack = { nav.navigateUp() },
                isPickingForTrip = false
            )
        }
        composable(MiscRoutes.SearchUsers) {
            SearchUsersScreen(
                padding = padding,
                onBack = { nav.navigateUp() } // ← 加上 onBack 讓箭頭直接返回
            )
        }
        composable(MiscRoutes.EditProfile) {
            EditProfileScreen(
                padding = padding,
                onCancel = { nav.popBackStack() },
                onSaved = {
                    // 回傳結果給上一頁（Profile）
                    nav.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("profile_updated", true)
                    // 返回
                    nav.popBackStack()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    destination: NavDestination?,
    isTopLevel: Boolean,
    onBack: () -> Unit,
    onExploreSearch: () -> Unit,
    onFriendsSearch: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenTripChat: () -> Unit,
    onInvite: () -> Unit,
    showInvite: Boolean,
    onOpenTripMore: () -> Unit,
    showTripChat: Boolean,
    scrollBehavior: TopAppBarScrollBehavior
) {
    val route = destination?.route.orEmpty()
    val title = when (route) {
        Root.Explore.route      -> "Explore"
        Root.MyTrips.route      -> "My Trips"
        Root.Friends.route      -> "Friends"
        Root.Saved.route        -> "Saved"
        Root.Profile.route      -> "Profile"
        TripRoutes.Create       -> "Create Trip"
        TripRoutes.Preview      -> "Preview Trip"
        TripRoutes.Detail       -> "Trip Detail"
        TripRoutes.PickPlace    -> "Pick Place"
        TripRoutes.AddActivity  -> "Add Activity"
        TripRoutes.EditActivity -> "Edit Activity"
        TripRoutes.Chat         -> "Trip Chat"
        MiscRoutes.SearchPlaces -> "Search Places"
        MiscRoutes.SearchUsers  -> "Search Users"
        MiscRoutes.EditProfile  -> "Edit Profile"
        else -> ""
    }

    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge, // ← Brand/Display 字體
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (!isTopLevel) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = {
            when (route) {
                Root.Explore.route -> {
                    IconButton(onClick = onExploreSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "Search places")
                    }
                }
                Root.Friends.route -> {
                    IconButton(onClick = onFriendsSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "Search users")
                    }
                }
                Root.Profile.route -> {
                    IconButton(onClick = onEditProfile) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit profile")
                    }
                }
                TripRoutes.Detail -> {
                    if (showTripChat) {
                        IconButton(onClick = onOpenTripChat) {
                            Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "Trip chat")
                        }
                    }
                    IconButton(onClick = onInvite) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = "Invite friends")
                    }
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        ),
        scrollBehavior = scrollBehavior
    )
}

private val NO_BOTTOM_BAR_ROUTES = setOf(
    TripRoutes.Create,
    TripRoutes.Preview,
    TripRoutes.Detail,
    TripRoutes.Chat,
    MiscRoutes.SearchPlaces,
    MiscRoutes.SearchPlacesPick,
    MiscRoutes.SearchUsers,
    MiscRoutes.EditProfile,
    TripRoutes.PickPlace,
    TripRoutes.AddActivity,
    TripRoutes.EditActivity
)
@Composable
private fun AppBottomBar(
    nav: NavHostController,
    currentDestination: NavDestination?
) {
    val cs = MaterialTheme.colorScheme
    NavigationBar(
        containerColor = cs.surface,
        contentColor   = cs.onSurface
    ) {
        TOP_LEVEL_DESTINATIONS.forEach { dest ->
            val selected = currentDestination.isInHierarchy(dest.route)
            NavigationBarItem(
                selected = selected,
                onClick = {
                    nav.navigate(dest.route) {
                        launchSingleTop = true
                        restoreState = true
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    }
                },
                icon = { Icon(dest.icon, null) },
                label = { Text(text = stringResource(dest.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor        = cs.primaryContainer,       // MTB 建議
                    selectedIconColor     = cs.onPrimaryContainer,
                    selectedTextColor     = cs.onPrimaryContainer,
                    unselectedIconColor   = cs.onSurfaceVariant,
                    unselectedTextColor   = cs.onSurfaceVariant
                )
            )
        }
    }
}

private fun NavDestination?.isInHierarchy(route: String): Boolean {
    return this?.hierarchy?.any { it.route == route } == true
}