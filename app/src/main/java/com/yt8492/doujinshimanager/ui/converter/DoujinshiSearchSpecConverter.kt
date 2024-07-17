package com.yt8492.doujinshimanager.ui.converter

import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiSearchSpec
import com.yt8492.doujinshimanager.ui.search.SearchBindingModel

object DoujinshiSearchSpecConverter {
    fun convertToSearchSpec(
        bindingModel: SearchBindingModel,
    ): DoujinshiSearchSpec {
        return DoujinshiSearchSpec(
            keyword = bindingModel.title,
            circle = bindingModel.circle?.id,
            event = bindingModel.event?.id,
            authors = bindingModel.authors.map { it.id },
            tags = bindingModel.tags.map { it.id },
        )
    }
}
