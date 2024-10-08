package com.yt8492.doujinshimanager.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.yt8492.doujinshimanager.shared.domain.model.AuthorId
import com.yt8492.doujinshimanager.shared.domain.model.CircleId
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiId
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiSearchSpec
import com.yt8492.doujinshimanager.shared.domain.model.EventId
import com.yt8492.doujinshimanager.shared.domain.model.TagId
import com.yt8492.doujinshimanager.ui.circledetail.CircleDetailDestination
import com.yt8492.doujinshimanager.ui.circledetail.CircleDetailPage
import com.yt8492.doujinshimanager.ui.detail.DetailDestination
import com.yt8492.doujinshimanager.ui.detail.DetailPage
import com.yt8492.doujinshimanager.ui.edit.EditDestination
import com.yt8492.doujinshimanager.ui.edit.EditPage
import com.yt8492.doujinshimanager.ui.register.RegisterDestination
import com.yt8492.doujinshimanager.ui.register.RegisterPage
import com.yt8492.doujinshimanager.ui.search.SearchDestination
import com.yt8492.doujinshimanager.ui.search.SearchPage
import com.yt8492.doujinshimanager.ui.theme.DoujinshiManagerTheme
import com.yt8492.doujinshimanager.ui.top.TopDestination
import com.yt8492.doujinshimanager.ui.top.TopPage

@Composable
fun MainApp() {
    DoujinshiManagerTheme {
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = TopDestination,
        ) {
            composable<TopDestination> {
                TopPage(navController)
            }
            composable<RegisterDestination> {
                RegisterPage(navController)
            }
            composable<DetailDestination> {
                val destination: DetailDestination = it.toRoute()
                DetailPage(
                    id = DoujinshiId(destination.id),
                    navController = navController,
                )
            }
            composable<SearchDestination> {
                val destination: SearchDestination = it.toRoute()
                val searchSpec = DoujinshiSearchSpec(
                    keyword = destination.title,
                    circle = destination.circleId?.let(::CircleId),
                    authors = destination.authorIds.map(::AuthorId),
                    tags = destination.tagIds.map(::TagId),
                    event = destination.eventId?.let(::EventId),
                )
                SearchPage(
                    searchSpec = searchSpec,
                    navController = navController,
                )
            }
            composable<EditDestination> {
                val destination: EditDestination = it.toRoute()
                val id = DoujinshiId(destination.id)
                EditPage(
                    id = id,
                    navController = navController,
                )
            }
            composable<CircleDetailDestination> {
                val destination: CircleDetailDestination = it.toRoute()
                val id = CircleId(destination.circleId)
                CircleDetailPage(
                    id = id,
                    navController = navController,
                )
            }
        }
    }
}
