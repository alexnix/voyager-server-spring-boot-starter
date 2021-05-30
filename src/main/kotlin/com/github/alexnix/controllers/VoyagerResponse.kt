package com.github.alexnix.controllers

class VoyagerResponse(
    val create: List<Any> = listOf(),
    val update: List<Any> = listOf(),
    val delete: List<Any> = listOf(),
) {
    val __voyager_api: Boolean = true
}