package test


import agents.SimpleActionDoNothing
import agents.RHEA.*
import ggi.InterruptibleWait
import groundWar.*
import groundWar.EventGameParams
import math.Vec2d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*
import kotlin.math.*


// we create a simple world of 3 cities. One Blue and one Red, with a Neutral world sandwiched between them
// all routes to and from city 0 have a limit of 30.0, the remainder are unlimited
// City 0 itself has a limit of 50.0
private val cities = listOf(
        City(Vec2d(0.0, 0.0), 0, Force(100.0), PlayerId.Blue, limit = 50.0),
        City(Vec2d(0.0, 20.0), 0, Force(100.0), PlayerId.Red),
        City(Vec2d(0.0, 10.0), 0, owner = PlayerId.Neutral)
)
private val routes = listOf(
        Route(0, 1, 20.0, limit = 30.0),
        Route(0, 2, 10.0, limit = 30.0),
        Route(1, 0, 20.0, limit = 30.0),
        Route(1, 2, 10.0),
        Route(2, 0, 10.0, limit = 30.0),
        Route(2, 1, 10.0)
)

private val params = EventGameParams(speed = doubleArrayOf(5.0, 5.0))
private val world = World(cities, routes, params = params)

class ArcLimitTest {

    @Test
    fun launchExpeditionCapsAtRouteLimit() {
        for (p in 1..10) {
            val game = LandCombatGame(world.deepCopy())
            val constrainedExpedition = LaunchExpedition(PlayerId.Blue, 0, 1, p * 0.10, 0)
            val unconstrainedExpedition = LaunchExpedition(PlayerId.Red, 1, 2, p * 0.10, 0)
            constrainedExpedition.apply(game)
            unconstrainedExpedition.apply(game)

            assertEquals(game.eventQueue.find { it.action is TransitStart && it.action.player == 0 }?.action,
                    TransitStart(Transit(Force(if (p < 3) (p * 0.10) * 100.0 else 30.0), 0, 1, PlayerId.Blue, 0, 4)))
            assertEquals(game.eventQueue.find { it.action is TransitStart && it.action.player == 1 }?.action,
                    TransitStart(Transit(Force((p * 0.10) * 100.0), 1, 2, PlayerId.Red, 0, 2)))
        }
    }

    @Test
    fun coreActionFilterReturnsLaunchExpeditionActionsThatTakeRouteLimitIntoAccount() {
        val game = LandCombatGame(world.deepCopy())
        val blueActions = game.possibleActions(0, filterType = "core")
        val redActions = game.possibleActions(1, filterType = "core")

        assertEquals(blueActions.size, 5)
        assertEquals(blueActions.filter { it is InterruptibleWait }.size, 3)
        assertTrue(blueActions.contains(LaunchExpedition(PlayerId.Blue, 0, 1, 1 / 3.0, params.OODALoop[0])))
        assertTrue(blueActions.contains(LaunchExpedition(PlayerId.Blue, 0, 2, 1 / 3.0, params.OODALoop[0])))

        assertEquals(redActions.size, 7)
        assertEquals(redActions.filter { it is InterruptibleWait }.size, 3)
        assertTrue(redActions.contains(LaunchExpedition(PlayerId.Red, 1, 0, 1 / 3.0, params.OODALoop[1])))
        assertTrue(redActions.contains(LaunchExpedition(PlayerId.Red, 1, 2, 1 / 3.0, params.OODALoop[1])))
        assertTrue(redActions.contains(LaunchExpedition(PlayerId.Red, 1, 2, 2 / 3.0, params.OODALoop[1])))
        assertTrue(redActions.contains(LaunchExpedition(PlayerId.Red, 1, 2, 3 / 3.0, params.OODALoop[1])))
    }

    @Test
    fun lanchesterBattleAgainstLimitedNodeTakesLimitIntoAccount() {
        // At City 0 only 50.0 can be brought to bear in the defence
        // Therefore attacking with 30.0 Red should not win, but inflict more casualties than with no limit
        // It's not an exact test...but a binary sanity check
        val noLimitResult = -lanchesterClosedFormBattle(Force(30.0), Force(100.0),
                params.lanchesterCoeff[1], params.lanchesterExp[1], params.lanchesterCoeff[0], params.lanchesterExp[0])
        val limitResult = -lanchesterClosedFormBattle(Force(30.0), Force(100.0),
                params.lanchesterCoeff[1], params.lanchesterExp[1], params.lanchesterCoeff[0], params.lanchesterExp[0],
                50.0)
        assertTrue(noLimitResult > limitResult)

        val game = LandCombatGame(world.deepCopy())
        game.planEvent(2, LaunchExpedition(PlayerId.Red, 1, 0, 1.0, 10))
        game.next(3)
        assertEquals(game.world.currentTransits.size, 1)
        val gameCopy = game.copy(1)
        game.next(5)
        gameCopy.next(5)
        assertEquals(game.world.cities[0].owner, PlayerId.Blue)
        assertEquals(game.world.currentTransits.size, 0)
        assertEquals(game.world.cities[0].pop.size, limitResult)

        assertEquals(gameCopy.world.cities[0].owner, PlayerId.Blue)
        assertEquals(gameCopy.world.currentTransits.size, 0)
        assertEquals(gameCopy.world.cities[0].pop.size, limitResult)
    }

}