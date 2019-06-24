package test.junit

import games.eventqueuegame.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class IntervalTests {

    @Test
    fun simpleIntervalFromString() {
        assertEquals(interval(" 2 "), interval(2))
        assertEquals(interval(" 30"), interval(30))
        assertEquals(interval("8"), interval(8))
        assertEquals(interval(" 29.0 "), interval(29.00))
        assertEquals(interval(" 30.671"), interval(30.671))
        assertEquals(interval("8.5"), interval(8.5))
    }

    @Test
    fun intervalFromString() {
        assertEquals(interval(" 5, 2 "), interval(5, 2))
        assertEquals(interval("20, 30"), interval(20, 30))
        assertEquals(interval(" 78  , 100"), interval(78, 100))
        assertEquals(interval(" 5.6, 2 "), interval(5.6, 2.0))
        assertEquals(interval("20.0, 30.0"), interval(20.0, 30.0))
        assertEquals(interval(" 78.933  , 100.0"), interval(78.933, 100.0))
    }

    @Test
    fun listOfIntervalsFromString() {
        assertEquals(intervalList(" 2 :5, 2"), listOf(interval(2), interval(5, 2)))
        assertEquals(intervalList("78.9, 100: 10.0"), listOf(interval(78.9, 100), interval(10.0)))
        assertEquals(intervalList("[78, 100] : [10, 20]"), listOf(interval(78, 100), interval(10, 20)))
        assertEquals(intervalList("[0.1, .2]:[1.0, 2.0  ]"), listOf(interval(0.1, 0.2), interval(1.0, 2.0)))
    }
}