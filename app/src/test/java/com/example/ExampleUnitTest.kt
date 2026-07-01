package com.example

import org.junit.Assert.*
import org.junit.Test
import org.matrix.rustcomponents.sdk.TimelineItemContent

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    val contentMethods = try { TimelineItemContent::class.java.declaredMethods.map { it.name + "(" + it.parameterTypes.joinToString { p -> p.simpleName } + "): " + it.returnType.simpleName } } catch(e: Exception) { emptyList() }
    
    val output = "CONTENT METHODS:\n" + contentMethods.joinToString("\n")
    
    fail(output)
  }
}
