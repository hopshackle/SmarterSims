package test

import groundWar.*
import math.Vec2d
import kotlin.math.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import kotlin.random.Random
import kotlin.test.assertEquals

// we create a simple world
private val cities = listOf(
        City(Vec2d(0.0, 0.0), 0, Force(10.0), PlayerId.Blue),
        City(Vec2d(0.0, 20.0), 0, Force(10.0), PlayerId.Red),
        City(Vec2d(0.0, 10.0), 0, owner = PlayerId.Neutral),
        City(Vec2d(5.0, 15.0), 0, owner = PlayerId.Neutral),
        City(Vec2d(10.0, 0.0), 0, Force(2.0), PlayerId.Blue)
)
private val routes = listOf(
        Route(0, 1, 20.0, 1.0),
        Route(0, 2, 10.0, 1.0),
        Route(1, 0, 20.0, 1.0),
        Route(1, 2, 10.0, 1.0),
        Route(2, 0, 10.0, 1.0),
        Route(2, 1, 10.0, 1.0),
        Route(3, 0, 10.0, 1.0),
        Route(0, 3, 10.0, 1.0),
        Route(0, 4, 10.0, 1.0),
        Route(4, 0, 10.0, 1.0)
)

private val params = EventGameParams(speed = doubleArrayOf(5.0, 5.0), width = 20, height = 20, seed = 10, fogOfWar = true,
        fatigueRate = doubleArrayOf(0.05, 0.05))
private val world = World(cities, routes, params = params)
private val game = LandCombatGame(world)

class FatigueMovementTests {

    @Test
    fun simpleMoveToFriendlyCity() {
        val gameCopy = game.copy()
        val move = LaunchExpedition(PlayerId.Blue, 0, 4, 0.5, 0)
        assertEquals(gameCopy.world.cities[0].pop, Force(10.0, 0.0, 0))
        move.apply(gameCopy)
        gameCopy.next(3)
        assertEquals(gameCopy.world.cities[0].pop, Force(5.0, 0.0, 0))
        assertEquals(gameCopy.world.cities[4].pop, Force(7.0, (0.1 * 5.0) / 7.0, 2))
    }

    @Test
    fun simpleMoveToEmptyCity() {
        val gameCopy = game.copy()
        val move = LaunchExpedition(PlayerId.Blue, 0, 2, 0.5, 0)
        assertEquals(gameCopy.world.cities[0].pop, Force(10.0, 0.0, 0))
        move.apply(gameCopy)
        gameCopy.next(3)
        assertEquals(gameCopy.world.cities[0].pop, Force(5.0, 0.0, 0))
        assertEquals(gameCopy.world.cities[2].pop, Force(5.0, 0.1, 2))
    }

    @Test
    fun moveToCityAndThenOnward() {
        val gameCopy = game.copy()
        val move1 = LaunchExpedition(PlayerId.Blue, 0, 2, 0.5, 0)
        move1.apply(gameCopy)
        gameCopy.next(3)
        assertEquals(gameCopy.world.cities[0].pop, Force(5.0, 0.0, 0))
        assertEquals(gameCopy.world.cities[2].pop, Force(5.0, 0.1, 2))
        // at this point they have rested for one tick, so should be at fatigue 0.05

        assertEquals(gameCopy.nTicks(), 3)
        val move2 = LaunchExpedition(PlayerId.Blue, 2, 0, 0.5, 0)
        move2.apply(gameCopy)
        gameCopy.next(3)
        assertEquals(gameCopy.world.cities[0].pop.copy(fatigue = 0.0), Force(7.5, 0.0, 5))
        assertEquals(gameCopy.world.cities[0].pop.fatigue, (0.15 * 2.5) / 7.5, 0.001)
        assertEquals(gameCopy.world.cities[2].pop, Force(2.5, 0.05, 3))
    }
}
