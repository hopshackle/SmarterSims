package test

import groundWar.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

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

        LaunchExpedition(PlayerId.Red, 1, 2, 0.5, 0).apply(startState)
        startState.next(5)
        assertEquals(startState.score(0), 5.0)
        assertEquals(startState.score(1), 10.0)
    }

    @Test
    fun simpleScoreTestInTransit() {
        val world = World(params = EventGameParams(seed = 3))
        val blueCity = world.cities.first{c -> c.owner == PlayerId.Blue}
        val redCity = world.cities.first{c -> c.owner == PlayerId.Red}
        val game = LandCombatGame(world)
        assertEquals(simpleScoreFunction(5.0, 1.0, -5.0, -1.0)(game, 0), 0.0)
        assertEquals(simpleScoreFunction(5.0, 1.0, -5.0, -1.0)(game, 1), 0.0)

        val action1 = LaunchExpedition(PlayerId.Blue, world.cities.indexOf(blueCity), 1, 0.5, 2)
        action1.apply(game)
        assertEquals(blueCity.pop, 50.0)
        assertEquals(simpleScoreFunction(5.0, 1.0, -5.0, -1.0)(game, 0), 0.0)
        assertEquals(simpleScoreFunction(5.0, 1.0, -5.0, -1.0)(game, 1), 0.0)

        val action2 = LaunchExpedition(PlayerId.Red, world.cities.indexOf(redCity), 0, 0.25, 2)
        action2.apply(game)
        assertEquals(redCity.pop, 75.0)
        assertEquals(simpleScoreFunction(5.0, 1.0, -5.0, -1.0)(game, 0), 0.0)
        assertEquals(simpleScoreFunction(5.0, 1.0, -5.0, -1.0)(game, 1), 0.0)
    }
}