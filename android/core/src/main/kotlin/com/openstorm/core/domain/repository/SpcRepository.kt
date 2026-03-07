package com.openstorm.core.domain.repository

import com.openstorm.core.domain.model.SpcMesoscaleDiscussion
import com.openstorm.core.domain.model.SpcOutlook
import com.openstorm.core.domain.model.SpcWatch

/**
 * Repository for Storm Prediction Center products.
 * Fetches directly from SPC GeoJSON endpoints.
 */
interface SpcRepository {

    /** Get convective outlooks for the given day (1-3). */
    suspend fun getOutlook(day: Int): SpcOutlook?

    /** Get all active mesoscale discussions. */
    suspend fun getActiveMesoscaleDiscussions(): List<SpcMesoscaleDiscussion>

    /** Get all active watches. */
    suspend fun getActiveWatches(): List<SpcWatch>

    /** Force refresh all SPC products. */
    suspend fun refresh()
}
