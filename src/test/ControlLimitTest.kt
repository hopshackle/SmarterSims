package test

import groundWar.*
import math.Vec2d
import kotlin.math.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
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

private val params = EventGameParams(speed = doubleArrayOf(5.0, 5.0), width = 20, height = 20, seed = 10, fogOfWar = false,
        controlLimit = intArrayOf(0, 2))
// so BLUE is unlimited in controllable forces, while RED has a maximum of two
private val world = World(cities, routes, params = params)
private val game = LandCombatGame(world)


class ControlLimitTest {
    @Test
    fun launchExpeditionIsValidUntilLimitIsReached() {
        val gameCopy = game.copy()
        assertTrue(LaunchExpedition(PlayerId.Red, 1, 0, 0.1, 0).isValid(gameCopy))
        gameCopy.world.addTransit(Transit(Force(2.0), 1, 0, PlayerId.Red, 0, 10))
        assertTrue(LaunchExpedition(PlayerId.Red, 1, 0, 0.1, 0).isValid(gameCopy))
        gameCopy.world.addTransit(Transit(Force(2.0), 1, 0, PlayerId.Red, 5, 15))
        assertFalse(LaunchExpedition(PlayerId.Red, 1, 0, 0.1, 0).isValid(gameCopy))
    }

    @Test
    fun launchExpeditionIsInvalidIfLimitBreachedWithPlannedExpeditions() {
        val gameCopy = game.copy()
        gameCopy.world.addTransit(Transit(Force(2.0), 1, 0, PlayerId.Red, 0, 10))
        assertTrue(LaunchExpedition(PlayerId.Red, 1, 0, 0.1, 0).isValid(gameCopy))
        gameCopy.planEvent(5, TransitStart(Transit(Force(2.0), 1, 0, PlayerId.Red, 5, 15)))
        assertFalse(LaunchExpedition(PlayerId.Red, 1, 0, 0.1, 0).isValid(gameCopy))
    }

    @Test
    fun launchExpeditionIsValidWithLimitOfZero() {
        val gameCopy = game.copy()
        assertTrue(LaunchExpedition(PlayerId.Blue, 0, 1, 0.1, 0).isValid(gameCopy))
        gameCopy.world.addTransit(Transit(Force(2.0), 1, 0, PlayerId.Red, 0, 10))
        assertTrue(LaunchExpedition(PlayerId.Blue, 0, 1, 0.1, 0).isValid(gameCopy))
        gameCopy.world.addTransit(Transit(Force(2.0), 1, 0, PlayerId.Red, 5, 15))
        assertTrue(LaunchExpedition(PlayerId.Blue, 0, 1, 0.1, 0).isValid(gameCopy))
        gameCopy.planEvent(5, TransitStart(Transit(Force(2.0), 0, 1, PlayerId.Blue, 5, 15)))
        assertTrue(LaunchExpedition(PlayerId.Blue, 0, 1, 0.1, 0).isValid(gameCopy))
    }
}