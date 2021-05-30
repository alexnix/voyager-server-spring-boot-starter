package voyager.framework.server.controllers

import com.github.alexnix.models.HasId
import com.github.alexnix.services.VoyagerService
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

interface ACL<T> {
    fun createAcl(item: T): Boolean
    fun readOneAcl(item: T): Boolean
    fun readManyAcl(): Boolean
    fun updateAcl(old: T, new: T): Boolean
    fun deleteAcl(item: T): Boolean
}

open class DefaultACL<T>: ACL<T> {
    override fun createAcl(item: T): Boolean = true
    override fun readOneAcl(item: T): Boolean = true
    override fun readManyAcl(): Boolean = true
    override fun updateAcl(old: T, new: T): Boolean = true
    override fun deleteAcl(item: T): Boolean = true
}

interface Hooks<T> {
    fun beforeCreate(item: T): T
    fun beforeReadOne(id: Long)
    fun beforeReadMany(filters: Map<String, Pair<String, Any?>>): Map<String, Pair<String, Any?>>
    fun beforeUpdate(id: Long, new: T): T
    fun beforeDelete(id: Long)

    fun afterCreate(item: T): Any
    fun afterReadOne(item: T): Any
    fun afterReadMany(items: List<T>): List<T>
    fun afterUpdate(old: T, new: T): Any
    fun afterDelete(item: T): Any
}

open class DefaultHooks<T>: Hooks<T> {
    override fun beforeCreate(item: T) = item
    override fun beforeReadOne(id: Long) {}
    override fun beforeReadMany(filters: Map<String, Pair<String, Any?>>): Map<String, Pair<String, Any?>> = filters
    override fun beforeUpdate(id: Long, new: T) = new
    override fun beforeDelete(id: Long) {}

    override fun afterCreate(item: T) = item as Any
    override fun afterReadOne(item: T) = item as Any
    override fun afterReadMany(items: List<T>) = items
    override fun afterUpdate(old: T, new: T) = new as Any
    override fun afterDelete(item: T) = item as Any
}

open class VoyagerController<T: HasId, S: VoyagerService<T>>(
    private val service: S,
    private val acl: ACL<T> = DefaultACL(),
    private val hooks: Hooks<T> = DefaultHooks()
): ACL<T> by acl, Hooks<T> by hooks {

    @GetMapping("/")
    fun readMany(
        @RequestParam(defaultValue = "0") page_no: Int,
        @RequestParam(defaultValue = "20") page_size: Int,
        @RequestParam(defaultValue = "id:asc") sort_by: String,
        @RequestParam(defaultValue = "") select: List<String>,
        @RequestParam params: Map<String, String>,
    ): Page<T> {
        if(!readManyAcl()) {
            throw  ResponseStatusException(HttpStatus.UNAUTHORIZED)
        }

        val query = mutableMapOf<String, Pair<String, Any?>>()

        params.forEach { (k, v) ->
            if(listOf("page_size", "page_no", "select", "sort_by").contains(k)) return@forEach

            if(v.contains(":")) {
                val (op, rhs) = v.split(":")
                val rhsArr = rhs.split(",").map {
                   if(it[0] == '"' && it[it.length - 1] == '"') it.subSequence(1, it.length - 1)
                    else if(it == "null") null
                    else it.toInt()
                }
                val rhsReduced = rhsArr.reduce { _, it ->
                    if (rhsArr.size == 1 && !listOf("in", "nin").contains(op)) it
                    else rhsArr
                }
                query[k] = Pair(op, rhsReduced)
            } else {
                query[k] = Pair("eq", v)
            }
        }

        val hookedQuery = beforeReadMany(query)

        return service.read(
            hookedQuery,
            page_no,
            page_size,
            sort_by,
            select
        )
    }

    @GetMapping("/{id}")
    fun readOne(@PathVariable id: Long): Any {
        beforeReadOne(id);
        val item = service.readOne(id)
        if (readOneAcl(item)) {
            return afterReadOne(item)
        } else {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        }
    }

    @PostMapping("/")
    fun create(@RequestBody i: T): Any {
        val item = beforeCreate(i)
        if (createAcl(item)) {
            service.create(item)
            return afterCreate(item)
        } else {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        }
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody i: T): Any {
        beforeUpdate(id, i)
        val item = service.readOne(id) ?: throw  ResponseStatusException(HttpStatus.NOT_FOUND)
        if (updateAcl(item, i)) {
            i.id = item.id
            service.update(i)
            return afterUpdate(item, i)
        } else {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        }
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): Any {
        beforeDelete(id)
        val item = service.readOne(id) ?: throw  ResponseStatusException(HttpStatus.NOT_FOUND)
        if (deleteAcl(item)) {
            service.delete(item)
            return afterDelete(item)
        } else {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        }
    }
}