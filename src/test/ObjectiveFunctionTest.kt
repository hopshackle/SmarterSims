package test

import groundWar.*
import math.Vec2d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

// we create a simple world of 3 cities. One Blue and one Red, with a Neutral world sandwiched between them
private val cities = listOf(
        City(Vec2d(0.0, 0.0), 0, Force(10.0), PlayerId.Blue),
        City(Vec2d(0.0, 20.0), 0, Force(10.0), PlayerId.Red),
        City(Vec2d(0.0, 10.0), 0, owner = PlayerId.Neutral)
)
private val routes = listOf(
        Route(0, 1, 20.0, 1.0),
        Route(0, 2, 10.0, 1.0),
        Route(1, 0, 20.0, 1.0),
        Route(1, 2, 10.0, 1.0),
        Route(2, 0, 10.0, 1.0),
        Route(2, 1, 10.0, 1.0)
)

private val params = EventGameParams(speed = doubleArrayOf(5.0, 5.0), width = 20, height = 20, seed = 10)
private val world = World(cities, routes, params = params)
private val game = LandCombatGame(world)

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
        game.next(1)
        assertEquals(blueCity.pop.size, 50.0)
        assertEquals(simpleScoreFunction(5.0, 1.0, -5.0, -1.0)(game, 0), 0.0)
        assertEquals(simpleScoreFunction(5.0, 1.0, -5.0, -1.0)(game, 1), 0.0)

        val action2 = LaunchExpedition(PlayerId.Red, world.cities.indexOf(redCity), 0, 0.25, 2)
        action2.apply(game)
        game.next(1)
        assertEquals(redCity.pop.size, 75.0)
        assertEquals(simpleScoreFunction(5.0, 1.0, -5.0, -1.0)(game, 0), 0.0)
        assertEquals(simpleScoreFunction(5.0, 1.0, -5.0, -1.0)(game, 1), 0.0)
    }
}