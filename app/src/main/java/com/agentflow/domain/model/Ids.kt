package com.agentflow.domain.model
import java.util.UUID
object Ids { fun new(): String = UUID.randomUUID().toString() }
