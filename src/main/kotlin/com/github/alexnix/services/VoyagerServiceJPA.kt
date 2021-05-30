package com.github.alexnix.services

import com.github.alexnix.repositories.VoyagerRepositoryJPA
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import javax.persistence.criteria.Expression

open class VoyagerServiceJPA<T>(private val repo: VoyagerRepositoryJPA<T>) : VoyagerService<T> {
    private fun getSort(sortBy: String): Sort {
        return if(sortBy.contains(":")) {
            val (field, order) = sortBy.split(":")
            when(order) {
                "asc" -> Sort.by(field).ascending()
                "desc" -> Sort.by(field).descending()
                else -> Sort.by(field).ascending()
            }
        } else {
            Sort.by("id").ascending()
        }
    }

    override fun read(
        params: Map<String, Pair<String, Any?>>,
        page_no: Int,
        page_size: Int,
        sort_by: String,
        select: List<String>
    ): Page<T> {
        return repo.findAll({ root, cq, criteriaBuilder ->
            val predicates = params.map { (k, v) ->
                val (op, rhs) = v
                try {
                    val key = root.get<Any>(k)
                    when (op) {
                        "eq" -> criteriaBuilder.equal(key, rhs)
                        "neq" -> criteriaBuilder.notEqual(key, rhs)
                        "in" -> key.`in`(rhs)
                        "gt" -> criteriaBuilder.gt(key as Expression<out Int>, rhs as Int)
                        "lt" -> criteriaBuilder.lt(key as Expression<out Int>, rhs as Int)
                        "gte" -> criteriaBuilder.gt(key as Expression<out Int>, rhs as Int - 1)
                        "lte" -> criteriaBuilder.lt(key as Expression<out Int>, rhs as Int + 1)
                        else -> null
                    }
                } catch (e: Exception) {
                    null
                }
            }.filterNotNull()
            criteriaBuilder.and(*predicates.toTypedArray())
        }, PageRequest.of(page_no, page_size, getSort(sort_by)))
    }

    override fun readOne(id: Long): T = repo.findById(id).orElse(null)

    override fun create(i: T): T = repo.save(i)

    override fun update(i: T): T = repo.save(i)

    override fun delete(i: T) = repo.delete(i)
}