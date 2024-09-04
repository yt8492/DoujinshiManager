package com.yt8492.doujinshimanager.di

import com.yt8492.doujinshimanager.ui.circledetail.CircleDetailViewModel
import com.yt8492.doujinshimanager.ui.detail.DetailViewModel
import com.yt8492.doujinshimanager.ui.edit.EditViewModel
import com.yt8492.doujinshimanager.ui.register.RegisterViewModel
import com.yt8492.doujinshimanager.ui.search.SearchViewModel
import com.yt8492.doujinshimanager.ui.top.TopViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val uiModule = module {
    viewModel {
        TopViewModel(get())
    }
    viewModel {
        RegisterViewModel(
            circleRepository = get(),
            authorRepository = get(),
            tagRepository = get(),
            eventRepository = get(),
            doujinshiRepository = get(),
        )
    }
    viewModel { params ->
        DetailViewModel(
            id = params.get(),
            doujinshiRepository = get(),
        )
    }
    viewModel { params ->
        SearchViewModel(
            initialSearchSpec = params.get(),
            doujinshiRepository = get(),
            circleRepository = get(),
            authorRepository = get(),
            tagRepository = get(),
            eventRepository = get(),
        )
    }
    viewModel { params ->
        EditViewModel(
            id = params.get(),
            doujinshiRepository = get(),
            circleRepository = get(),
            authorRepository = get(),
            tagRepository = get(),
            eventRepository = get(),
        )
    }
    viewModel { params ->
        CircleDetailViewModel(
            id = params.get(),
            circleRepository = get(),
            doujinshiRepository = get(),
        )
    }
}
