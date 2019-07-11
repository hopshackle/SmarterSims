package test

import groundWar.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ObjectiveFunctionTest {
    @Test
    fun simpleObjectiveTest() {
        val startState = game.copy()
        startState.scoreFunction = mutableMapOf(
                PlayerId.Blue to simpleScoreFunction(5.0, 1.0, 0.0, -1.0),
                PlayerId.Red to simpleScoreFunction(5.0, 1.0, 0.0, -1.0)
        )
        assertEquals(startState.score(0), 5.0)
        assertEquals(startState.score(1), 5.0)

        LaunchExpedition(PlayerId.Red, 1, 1, 0.5, 0).apply(startState)
        startState.next(5)
        assertEquals(startState.score(0), 5.0)
        assertEquals(startState.score(1), 10.0)
    }
}