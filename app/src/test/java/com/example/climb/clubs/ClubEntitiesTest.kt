package com.example.climb.clubs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClubEntitiesTest {

    private fun membership(role: OrganizationRole, orgId: Long = 1L) = OrganizationMembershipEntity(
        organizationId = orgId, userId = "u1", role = role, joinedAt = 0L,
    )

    @Test
    fun `regression 1 and 7 - zero memberships means no staff access anywhere`() {
        assertFalse(hasStaffAccess(emptyList()))
        assertTrue(staffOrganizationIds(emptyList()).isEmpty())
    }

    @Test
    fun `regression 10 - a plain member-only list grants no staff access`() {
        val memberships = listOf(membership(OrganizationRole.MEMBER))
        assertFalse(hasStaffAccess(memberships))
        assertTrue(staffOrganizationIds(memberships).isEmpty())
    }

    @Test
    fun `staff and admin roles both count as staff access, scoped to their own organization`() {
        val memberships = listOf(
            membership(OrganizationRole.MEMBER, orgId = 1L),
            membership(OrganizationRole.STAFF, orgId = 2L),
            membership(OrganizationRole.ADMIN, orgId = 3L),
        )
        assertTrue(hasStaffAccess(memberships))
        assertTrue(staffOrganizationIds(memberships) == setOf(2L, 3L))
    }
}
