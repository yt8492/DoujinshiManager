package com.yt8492.doujinshimanager.shared.di

import com.yt8492.doujinshimanager.database.AuthorQueries
import com.yt8492.doujinshimanager.database.CircleQueries
import com.yt8492.doujinshimanager.database.DoujinshiQueries
import com.yt8492.doujinshimanager.database.EventQueries
import com.yt8492.doujinshimanager.database.TagQueries
import com.yt8492.doujinshimanager.shared.domain.repository.AuthorRepository
import com.yt8492.doujinshimanager.shared.domain.repository.CircleRepository
import com.yt8492.doujinshimanager.shared.domain.repository.DoujinshiRepository
import com.yt8492.doujinshimanager.shared.domain.repository.EventRepository
import com.yt8492.doujinshimanager.shared.domain.repository.TagRepository
import com.yt8492.doujinshimanager.shared.infra.repository.AuthorRepositoryImpl
import com.yt8492.doujinshimanager.shared.infra.repository.CircleRepositoryImpl
import com.yt8492.doujinshimanager.shared.infra.repository.DoujinshiRepositoryImpl
import com.yt8492.doujinshimanager.shared.infra.repository.EventRepositoryImpl
import com.yt8492.doujinshimanager.shared.infra.repository.TagRepositoryImpl
import org.koin.dsl.module

val commonModule = module { 
    factory<AuthorRepository> {
        AuthorRepositoryImpl(AuthorQueries(get()))
    }
    factory<CircleRepository> {
        CircleRepositoryImpl(CircleQueries(get()))
    }
    factory<DoujinshiRepository> {
        DoujinshiRepositoryImpl(DoujinshiQueries(get()))
    }
    factory<EventRepository> {
        EventRepositoryImpl(EventQueries(get()))
    }
    factory<TagRepository> {
        TagRepositoryImpl(TagQueries(get()))
    }
}
