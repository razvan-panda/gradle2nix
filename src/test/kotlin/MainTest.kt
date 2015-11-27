package org.mcpkg.gradle2nix.tests

import junit.framework.TestCase
import kotlin.test.assertEquals

class MainTest : TestCase() {
    fun testAssert() : Unit {
        assertEquals("Hello, world!", "Hello, world!")
    }
}
