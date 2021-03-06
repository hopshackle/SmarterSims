package test

import agents.RHEA.*
import groundWar.*
import groundWar.EventGameParams
import math.Vec2d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.pow
import kotlin.random.Random

class SimpleEvoAgentTest {

    // we create a simple world of 3 cities. One Blue and one Red, with a Neutral world sandwiched between them
    val cities = listOf(
            City(Vec2d(0.0, 0.0), 0, Force(10.0), PlayerId.Blue),
            City(Vec2d(0.0, 20.0), 0, Force(10.0), PlayerId.Red),
            City(Vec2d(0.0, 10.0), 0, owner= PlayerId.Neutral)
    )
    val routes = listOf(
            Route(0, 1, 20.0, 1.0),
            Route(0, 2, 10.0, 1.0),
            Route(1, 0, 20.0, 1.0),
            Route(1, 2, 10.0, 1.0),
            Route(2, 0, 10.0, 1.0),
            Route(2, 1, 10.0, 1.0)
    )
    val gameParams = EventGameParams(seed = 10, OODALoop = intArrayOf(10, 10), width = 20, height = 20, speed = doubleArrayOf(5.0, 5.0))
    val world = World(cities, routes, params = gameParams)
    val game = LandCombatGame(world)

    @Test
    fun shiftLeftOperatorWithOneAction() {
        val startArray = intArrayOf(0, 3, 7, 23, 56, 2, -89)
        val endArray = shiftLeftAndRandomAppend(startArray, 1, 5)
        for ((i, n) in startArray.withIndex()) {
            if (i >= 1) assertEquals(n, endArray[i - 1])
        }
        assertEquals(endArray.size, 7)
        assert(endArray.last() < 5 && endArray.last() >= 0)
    }

    @Test
    fun shiftLeftOperatorWithFourActions() {
        val startArray = intArrayOf(0, 3, 7, 23, 56, 2, -89)
        val endArray = shiftLeftAndRandomAppend(startArray, 4, 1)
        for ((i, n) in startArray.withIndex()) {
            if (i >= 4) assertEquals(n, endArray[i - 4])
        }
        assertEquals(endArray.size, 7)
        for (i in 1..4)
            assert(endArray[7 - i] == 0 || endArray[7 - i] == 1)

    }

    @Test
    fun blueExpeditionRollsForwardToTakeNeutralWorld() {
        // launches half of force to the Neutral city
        val blueGenome1 = intArrayOf(0, 1, 1, 1, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0)
        val projectedState1 = game.copy()
        var reward = evaluateSequenceDelta(projectedState1, blueGenome1, 0, 1.0, 5)
        assertEquals(reward, +1.0)
        assert(projectedState1.world.cities[2].owner == PlayerId.Blue)
        assert(game.world.cities[2].owner == PlayerId.Neutral)

        val blueGenome2 = intArrayOf(0, 1, 1, 1)
        val projectedState2 = game.copy()
        reward = evaluateSequenceDelta(projectedState2, blueGenome2, 0, 1.0, 1)
        assertEquals(reward, 0.0)       // not yet reached
        assert(projectedState2.world.cities[2].owner == PlayerId.Neutral)
        assert(game.world.cities[2].owner == PlayerId.Neutral)
        assertEquals(projectedState2.world.currentTransits.size, 1)
    }

    @Test
    fun redExpeditionLaunchedWhileBlueInProgressUpdatesCorrectly() {
        val blueGenome2 = intArrayOf(0, 1, 5, 1, 1, 1, 1, 0)
        val projectedState2 = game.copy()
        var reward = evaluateSequenceDelta(projectedState2, blueGenome2, 0, 1.0, 1)
        assert(projectedState2.world.cities[2].owner == PlayerId.Neutral)
        assertEquals(projectedState2.nTicks(), 1)
        assert(game.world.cities[2].owner == PlayerId.Neutral)
        assertEquals(projectedState2.world.currentTransits.size, 1)
        assertEquals(reward, 0.0)
        // blue force now in transit

        val redGenome1 = intArrayOf(1, 0, 9, 1, 1, 1, 1, 0)
        val projectedState3 = projectedState2.copy()
        reward = evaluateSequenceDelta(projectedState3, redGenome1, 1, 1.0, 2)
        assertEquals(projectedState2.nTicks(), 1)
        assertEquals(projectedState3.nTicks(), 3)
        assertEquals(reward, -1.0) // blue force reaches neutral city

        val redGenome2 = intArrayOf(1, 0, 9, 1, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0)
        val projectedState4 = projectedState2.copy()
        reward = evaluateSequenceDelta(projectedState4, redGenome2, 1, 1.0, 6)
        assertEquals(reward, 1.0) // blue force reaches neutral city, and red force conquers blue base
    }

    @Test
    fun follForwardAgentIsUsedOnGameCopy() {
        val blueAgent = SimpleActionEvoAgent(SimpleEvoAgent(horizon = 100))
        val redAgent = SimpleActionEvoAgent(SimpleEvoAgent())
        game.registerAgent(0, blueAgent)
        game.registerAgent(1, redAgent)
        val copy = game.copy()

        val copiedBlueAgent = copy.getAgent(0)
        val copiedRedAgent = copy.getAgent(1)
        assertTrue(copiedBlueAgent is SimpleActionEvoAgentRollForward)
        assertFalse(game.getAgent(0) is SimpleActionEvoAgentRollForward)
        assertTrue(copiedRedAgent is SimpleActionEvoAgentRollForward)
        assertFalse(game.getAgent(1) is SimpleActionEvoAgentRollForward)
        assertFalse(copiedBlueAgent is SimpleActionEvoAgent)
        assertTrue(game.getAgent(0) is SimpleActionEvoAgent)
        assertFalse(copiedRedAgent is SimpleActionEvoAgent)
        assertTrue(game.getAgent(1) is SimpleActionEvoAgent)

        assertTrue(copy.eventQueue.any { e -> e.action is MakeDecision && e.action.playerRef == 1 })
        assertTrue(copy.eventQueue.any { e -> e.action is MakeDecision && e.action.playerRef == 0 })
    }

    @Test
    fun rollForwardForSeveralTicksWithOneAction() {
        val blueGenome = intArrayOf(1, 0, 2, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0)
        // Blue first of all gives a Wait order for 10 time units, then a March order (which will take 2 time units to arrive) to invade the Neutral city
        // MakeDecision should be at tick = 20 (10 + 10), as the default wait after a LaunchExpedition is 10
        val redGenome = intArrayOf(1, 1, 2, 0, 1, 4, 0, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0)
        // Red gives an expedition order immediately to attack the Neutral city, that takes 3 time units
        // The second action is a Wait for 25
        val blueAgent = SimpleActionEvoAgentRollForward(blueGenome, actionFilter = "")
        val redAgent = SimpleActionEvoAgentRollForward(redGenome, actionFilter = "")
        game.registerAgent(0, blueAgent)
        game.registerAgent(1, redAgent)
        game.next(11)
        assertEquals(game.nTicks(), 11)
        assertEquals(game.score(0), -1.0)
        assertEquals(game.score(1), 1.0)
        assert(game.world.cities[2].owner == PlayerId.Red)
        assertEquals(game.world.currentTransits.size, 1)
        assertTrue(game.world.currentTransits[0].playerId == PlayerId.Blue)

        assertEquals(game.eventQueue.first { e -> e.action is MakeDecision && e.action.playerRef == 1}.tick, 35 )
        assertEquals(game.eventQueue.first { e -> e.action is MakeDecision && e.action.playerRef == 0}.tick, 20 )
    }

    @Test
    fun discountIsAppliedCorrectly() {
        val blueGenome2 = intArrayOf(0, 1, 5, 1, 1, 1, 1, 0)
        val projectedState2 = game.copy()
        var reward = evaluateSequenceDelta(projectedState2, blueGenome2, 0, 0.99, 1)
        assert(projectedState2.world.cities[2].owner == PlayerId.Neutral)
        assertEquals(projectedState2.nTicks(), 1)
        assert(game.world.cities[2].owner == PlayerId.Neutral)
        assertEquals(projectedState2.world.currentTransits.size, 1)
        assertEquals(reward, 0.0)
        // blue force now in transit

        val redGenome1 = intArrayOf(1, 0, 9, 1, 1, 1, 1, 0)
        val projectedState3 = projectedState2.copy()
        reward = evaluateSequenceDelta(projectedState3, redGenome1, 1, 0.99, 2)
        assertEquals(projectedState2.nTicks(), 1)
        assertEquals(projectedState3.nTicks(), 3)
        assertEquals(reward, -1.0 * 0.99.pow(2)) // blue force reaches neutral city

        val redGenome2 = intArrayOf(1, 0, 9, 1, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0)
        val projectedState4 = projectedState2.copy()
        reward = evaluateSequenceDelta(projectedState4, redGenome2, 1, 0.99, 6)
        assertEquals(reward, 1.0 * 0.99.pow(6)) // blue force reaches neutral city, and red force conquers blue base
    }

}