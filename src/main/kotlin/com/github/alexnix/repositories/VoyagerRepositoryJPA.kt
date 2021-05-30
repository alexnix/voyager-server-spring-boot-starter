package com.github.alexnix.repositories

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.NoRepositoryBean

@NoRepositoryBean
interface VoyagerRepositoryJPA<T> : JpaRepository<T, Long>, JpaSpecificationExecutor<T>