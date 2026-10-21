package com.example.gabai

/**
 * Announcement entity model for Division, School, and Classroom bulletins.
 */
data class Announcement(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val authorRole: String = "admin", // "super_admin", "school_admin", "teacher"
    val authorSchoolId: String = "",
    val scope: String = "division",    // "division", "school", "class"
    val schoolId: String = "ALL",      // "ALL" or specific School DepEd ID
    val classId: String? = null,       // Optional target class ID
    val targetRoles: List<String> = emptyList(), // "student", "teacher", "school_admin"
    val priority: String = "normal",   // "normal", "urgent"
    val allowReplies: Boolean = true,
    val replyCount: Long = 0L,
    val seenCount: Long = 0L,
    val seenUserIds: List<String> = emptyList(),
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Discreet reply entity model. Student replies are private 1-on-1 communications.
 */
data class AnnouncementReply(
    val id: String = "",
    val announcementId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderRole: String = "student", // "student", "teacher", "school_admin", "super_admin"
    val senderSchoolId: String = "",
    val message: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isAuthorResponse: Boolean = false,
    val isPrivate: Boolean = true,
    val replyToUid: String? = null
)

/**
 * View receipt tracking who opened an announcement.
 */
data class AnnouncementViewReceipt(
    val uid: String = "",
    val userName: String = "",
    val userRole: String = "student",
    val schoolId: String = "",
    val seenAt: Long = System.currentTimeMillis()
)
