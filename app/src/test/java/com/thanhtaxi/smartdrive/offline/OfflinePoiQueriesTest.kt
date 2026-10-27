package com.thanhtaxi.smartdrive.offline

import org.junit.Assert.*
import org.junit.Test

class OfflinePoiQueriesTest {
    @Test fun vietnameseAccentFoldingAndMultiWordPrefix() {
        assertEquals("duong tran hung dao ha noi", OfflinePoiQueries.normalize("Đường Trần Hưng Đạo, Hà Nội"))
        assertEquals("\"ben\"* AND \"xe\"* AND \"gia\"*",
            OfflinePoiQueries.ftsExpression("Bến xe Gia"))
    }
    @Test fun ftsInjectionCannotEscapeBoundTokens() {
        assertEquals("\"abc\"* AND \"drop\"* AND \"table\"*",
            OfflinePoiQueries.ftsExpression("abc' DROP TABLE;"))
        assertNull(OfflinePoiQueries.ftsExpression("☀️ !!"))
        assertEquals("\"ha\"* AND \"noi\"*", OfflinePoiQueries.ftsExpression("Hà-Nội"))
    }
}
