package uy.gestionpersonal.drive

data class WorkspaceRevision(val id: String, val parents: Set<String>, val name: String, val sha256: String)

fun workspaceHeads(revisions: List<WorkspaceRevision>): List<WorkspaceRevision> {
    require(revisions.map { it.id }.distinct().size == revisions.size)
    val parents = revisions.flatMap { it.parents }.toSet()
    return revisions.filter { it.id !in parents }.also { check(revisions.isEmpty() || it.isNotEmpty()) { "Historial de versiones no válido" } }
}

/** Immutable revisions preserve concurrent uploads. Resolving a fork is an explicit user action. */
fun workspacePublishParents(revisions: List<WorkspaceRevision>, base: String?, resolve: Boolean): Set<String> {
    val heads = workspaceHeads(revisions)
    if (heads.isEmpty()) { require(base == null) { "La versión base ya no está disponible" }; return emptySet() }
    check(base != null && heads.any { it.id == base }) { "Otro dispositivo publicó cambios. Descargá una versión antes de publicar." }
    check(heads.size == 1 || resolve) { "Hay versiones simultáneas. Revisalas y elegí cuál conservar como principal." }
    require(heads.size <= 10) { "Hay demasiadas versiones simultáneas. Revisá el historial antes de continuar." }
    return heads.mapTo(mutableSetOf()) { it.id }
}
