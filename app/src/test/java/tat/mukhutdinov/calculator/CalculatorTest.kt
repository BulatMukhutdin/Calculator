package tat.mukhutdinov.calculator

import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorTest {

    @Test
    fun addition_isCorrect() {
        val calculator = Calculator()

        val actual = calculator.sum(1, 1)

        assertEquals(7, actual)
    }
}
