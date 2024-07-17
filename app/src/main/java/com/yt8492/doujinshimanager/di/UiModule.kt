package com.yt8492.doujinshimanager.di

import com.yt8492.doujinshimanager.ui.detail.DetailViewModel
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
    viewModel {
        SearchViewModel(
            doujinshiRepository = get(),
            circleRepository = get(),
            authorRepository = get(),
            tagRepository = get(),
            eventRepository = get(),
        )
    }
}
