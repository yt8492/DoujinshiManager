package com.yt8492.doujinshimanager.shared.domain.model

import kotlinx.datetime.LocalDate

data class Period(
    override val start: LocalDate,
    val end: LocalDate
) : ClosedRange<LocalDate> by start..end
