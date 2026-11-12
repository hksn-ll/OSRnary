package com.example.gabai

/**
 * Pedagogical oversight remark posted by a School Admin, Super Admin (SDO),
 * or educator reply attached to a Library Material or Weekly Assessment.
 */
data class AdminRemark(
    val id: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val authorRole: String = "",
    val authorSchoolId: String = "",
    val authorPosition: String = "",
    val text: String = "",
    val createdAt: Long = 0L,
    val targetType: String = "",
    val targetId: String = "",
    val targetTitle: String = ""
) {
    val isDivisionAdmin: Boolean
        get() = authorRole == "super_admin" || authorRole == "admin" || authorSchoolId == "DIVISION_OFFICE"

    val isSchoolAdmin: Boolean
        get() = authorRole == "school_admin"

    val isAdminAuthor: Boolean
        get() = isDivisionAdmin || isSchoolAdmin
}
