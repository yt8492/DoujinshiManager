package com.yt8492.doujinshimanager.shared.domain.repository

import com.yt8492.doujinshimanager.shared.domain.model.Doujinshi
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiSearchResult
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiSearchSpec

interface DoujinshiRepository {
    suspend fun search(
        searchSpec: DoujinshiSearchSpec = DoujinshiSearchSpec(),
        page: Int = 0,
        size: Int = 30,
    ): DoujinshiSearchResult
    suspend fun save(doujinshi: Doujinshi)
}
