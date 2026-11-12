package com.example.gabai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AdminRemark] role classification used across Admin Remarks feeds and filters.
 */
class AdminRemarkTest {

    @Test
    fun schoolAdminRemark_isAdminAuthorNotDivision() {
        val remark = AdminRemark(
            id = "r1",
            authorRole = "school_admin",
            authorSchoolId = "SCH_001",
            text = "Please simplify passage Q3."
        )
        assertTrue(remark.isSchoolAdmin)
        assertFalse(remark.isDivisionAdmin)
        assertTrue(remark.isAdminAuthor)
    }

    @Test
    fun superAdminAndDivisionOffice_areDivisionAdmin() {
        val sdoByRole = AdminRemark(id = "r2", authorRole = "super_admin", text = "Division check")
        val legacyAdminRole = AdminRemark(id = "r3", authorRole = "admin", text = "Legacy SDO check")
        val sdoBySchoolId = AdminRemark(id = "r4", authorSchoolId = "DIVISION_OFFICE", text = "SDO check")

        assertTrue(sdoByRole.isDivisionAdmin)
        assertTrue(sdoByRole.isAdminAuthor)
        assertTrue(legacyAdminRole.isDivisionAdmin)
        assertTrue(legacyAdminRole.isAdminAuthor)
        assertTrue(sdoBySchoolId.isDivisionAdmin)
        assertTrue(sdoBySchoolId.isAdminAuthor)
    }

    @Test
    fun teacherReply_isNotAdminAuthor() {
        val teacherReply = AdminRemark(
            id = "r5",
            authorRole = "teacher",
            authorSchoolId = "SCH_001",
            text = "Acknowledged, updated quiz items."
        )
        assertFalse(teacherReply.isSchoolAdmin)
        assertFalse(teacherReply.isDivisionAdmin)
        assertFalse(teacherReply.isAdminAuthor)
    }
}
