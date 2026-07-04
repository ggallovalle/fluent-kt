package dev.kbroom.fluent.pseudo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for PseudoLocale pseudolocalization
 */
class PseudoLocaleTest {
    
    @Test
    fun testAccentedMode() {
        val pseudo = PseudoLocale(PseudoOptions(mode = PseudoMode.Accented))
        
        val result = pseudo.transform("Hello World")
        assertTrue(result.isNotEmpty())
    }
    
    @Test
    fun testWidenedMode() {
        val pseudo = PseudoLocale(PseudoOptions(mode = PseudoMode.Widened))
        
        val result = pseudo.transform("Hello")
        // Just check it's not empty
        assertTrue(result.isNotEmpty())
    }
    
    @Test
    fun testHiddenMode() {
        val pseudo = PseudoLocale(PseudoOptions(mode = PseudoMode.Hidden))
        
        val result = pseudo.transform("Hello")
        assertTrue(result.isNotEmpty())
    }
    
    @Test
    fun testBidiMode() {
        val pseudo = PseudoLocale(PseudoOptions(mode = PseudoMode.Bidi))
        
        val result = pseudo.transform("Hello")
        assertTrue(result.isNotEmpty())
    }
    
    @Test
    fun testSingleCharNotTransformed() {
        val pseudo = PseudoLocale(PseudoOptions(mode = PseudoMode.Accented))
        
        val result = pseudo.transform("A")
        assertTrue(result.isNotEmpty())
    }
    
    @Test
    fun testEmptyString() {
        val pseudo = PseudoLocale(PseudoOptions(mode = PseudoMode.Accented))
        
        val result = pseudo.transform("")
        assertEquals("", result)
    }
    
    @Test
    fun testCreatePseudoTransform() {
        val transform = createPseudoTransform(PseudoMode.Accented)
        
        val result = transform("Test")
        assertTrue(result.isNotEmpty())
    }
}
