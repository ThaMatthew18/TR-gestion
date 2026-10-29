package uy.gestionpersonal.drive

import org.junit.Assert.*
import org.junit.Test

class WorkspaceVersionsTest {
    private fun rev(id: String, vararg parents: String) = WorkspaceRevision(id, parents.toSet(), id, "hash")
    @Test fun firstPublishAndTheNextVersionKeepAChain() {
        assertTrue(workspacePublishParents(emptyList(), null, false).isEmpty())
        val versions = listOf(rev("first"), rev("second", "first"))
        assertEquals(listOf("second"), workspaceHeads(versions).map { it.id })
        assertEquals(setOf("second"), workspacePublishParents(versions, "second", false))
    }
    @Test fun anotherDeviceCannotPublishOverANewerVersion() {
        assertThrows(IllegalStateException::class.java) { workspacePublishParents(listOf(rev("one"), rev("two", "one")), "one", false) }
        assertThrows(IllegalStateException::class.java) { workspacePublishParents(listOf(rev("one")), null, false) }
    }
    @Test fun concurrentVersionsAreKeptAndNeedExplicitResolution() {
        val revisions = listOf(rev("base"), rev("phone-a", "base"), rev("phone-b", "base"))
        assertEquals(setOf("phone-a", "phone-b"), workspaceHeads(revisions).map { it.id }.toSet())
        assertThrows(IllegalStateException::class.java) { workspacePublishParents(revisions, "phone-a", false) }
        val parents = workspacePublishParents(revisions, "phone-a", true)
        assertEquals(setOf("phone-a", "phone-b"), parents)
        assertEquals(listOf("resolved"), workspaceHeads(revisions + rev("resolved", *parents.toTypedArray())).map { it.id })
    }
}
