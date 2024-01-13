package com.yt8492.doujinshimanager.shared.domain.model

import kotlinx.datetime.LocalDate

data class Period(
    override val start: LocalDate,
    override val endInclusive: LocalDate
) : ClosedRange<LocalDate> {
    override fun contains(value: LocalDate): Boolean {
        return start < value && value <= endInclusive
    }

    override fun isEmpty(): Boolean {
        return false
    }
}
