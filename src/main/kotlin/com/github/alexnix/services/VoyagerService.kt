package com.github.alexnix.services

import org.springframework.data.domain.Page

interface VoyagerService<T> {
    fun read(
        params: Map<String, Pair<String, Any?>>,
        page_no: Int,
        page_size: Int,
        sort_by: String,
        select: List<String>
    ): Page<T>

    fun readOne(id: Long): T

    fun create(i: T): T

    fun update(i: T): T

    fun delete(i: T)
}