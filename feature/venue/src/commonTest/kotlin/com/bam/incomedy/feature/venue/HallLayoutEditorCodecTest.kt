package com.bam.incomedy.feature.venue

import com.bam.incomedy.domain.venue.HallTemplate
import com.bam.incomedy.domain.venue.HallTemplateStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HallLayoutEditorCodecTest {

    @Test
    fun `codec parses and restores template editor input`() {
        val input = HallLayoutEditorInput(
            stageLabel = "Main Stage",
            priceZonesText = "vip|VIP|3000",
            zonesText = "standing-a|Standing A|40|vip|standing",
            rowsText = "row-a|A|3|vip",
            tablesText = "table-1|Table 1|4|vip",
            serviceAreasText = "service-1|Sound Desk|technical",
            blockedSeatRefsText = "row-a-2",
        )

        val draft = HallLayoutEditorCodec.toDraft(
            name = "Late Show Layout",
            status = HallTemplateStatus.PUBLISHED,
            input = input,
        ).getOrThrow()

        assertEquals("Main Stage", draft.layout.stage?.label)
        assertEquals(1, draft.layout.priceZones.size)
        assertEquals(1, draft.layout.zones.size)
        assertEquals(3, draft.layout.rows.single().seats.size)
        assertEquals(listOf("row-a-2"), draft.layout.blockedSeatRefs)

        val restored = HallLayoutEditorCodec.fromTemplate(
            HallTemplate(
                id = "template-1",
                venueId = "venue-1",
                name = "Late Show Layout",
                version = 2,
                status = HallTemplateStatus.PUBLISHED,
                layout = draft.layout,
            ),
        )

        assertTrue(restored.rowsText.contains("row-a|A|3|vip"))
        assertTrue(restored.blockedSeatRefsText.contains("row-a-2"))
    }
}
