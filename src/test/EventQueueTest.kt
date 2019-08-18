package test

import groundWar.*
import math.Vec2d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.math.*

class InterruptWaitTests() {


    // we create a simple world of 3 cities. One Blue and one Red, with a Neutral world sandwiched between them
    private val cities = listOf(
            City(Vec2d(0.0, 0.0), 0, 10.0, PlayerId.Blue),
            City(Vec2d(0.0, 20.0), 0, 10.0, PlayerId.Red),
            City(Vec2d(0.0, 10.0), 0, 0.0, PlayerId.Neutral),
            City(Vec2d(5.0, 15.0), 0, 0.0, PlayerId.Neutral)
    )
    private val routes = listOf(
            Route(0, 1, 20.0, 1.0),
            Route(0, 2, 10.0, 1.0),
            Route(1, 0, 20.0, 1.0),
            Route(1, 2, 10.0, 1.0),
            Route(2, 0, 10.0, 1.0),
            Route(2, 1, 10.0, 1.0),
            Route(3, 0, 10.0, 1.0),
            Route(0, 3, 10.0, 1.0)
    )

    val params = EventGameParams(speed = doubleArrayOf(5.0, 5.0), width = 20, height = 20, seed = 10, fogOfWar = true)
    val world = World(cities, routes, params = params)
    val game = LandCombatGame(world)

    @BeforeEach
    fun setup() {
        game.registerAgent(0, HeuristicAgent(3.0, 1.0))
        game.registerAgent(1, HeuristicAgent(3.0, 1.0))

        game.eventQueue.removeIf{it.action is MakeDecision}
        game.planEvent(50, MakeDecision(0, 0))
        game.planEvent(50, MakeDecision(1, 0))

        assertEquals(game.eventQueue.size, 2)
        assertEquals(game.eventQueue.filter{it.action is MakeDecision}.count(), 2)
        assertEquals(game.eventQueue.filter { it.tick == 50 }.count(), 2)
    }

    @Test
    fun visibleActionMovesMakeDecisionOfOtherPlayerButNotOwn() {
        LaunchExpedition(PlayerId.Blue, 0, 1, .2, 0).apply(game)
        assertEquals(game.eventQueue.size, 4)
        assertEquals(game.eventQueue.filter{it.action is MakeDecision}.count(), 2)
        assertEquals(game.eventQueue.filter { it.tick == 50 }.count(), 2)
        assertEquals(game.eventQueue.filter { it.tick == 0 }.count(), 1) // The TransitEnd

        game.next(1)
        assertEquals(game.eventQueue.size, 3)   // 2 MakeDecisions, and a TransitEnd
        assertEquals(game.eventQueue.filter{it.action is MakeDecision}.count(), 2)
        assertEquals(game.eventQueue.filter { it.tick == 50 }.count(), 1)
        assertEquals(game.eventQueue.filter{ it.action is MakeDecision && it.action.player == 0}[0],
                Event(50, MakeDecision(0, 0)))
        assertEquals(game.eventQueue.filter{ it.action is MakeDecision && it.action.player == 1}[0],
                Event(1, MakeDecision(1, 1)))
    }

    @Test
    fun nonVisibleActionDoesNotMoveDecision() {
        LaunchExpedition(PlayerId.Blue, 0, 3, .2, 0).apply(game)
        assertEquals(game.eventQueue.size, 4)
        assertEquals(game.eventQueue.filter{it.action is MakeDecision}.count(), 2)
        assertEquals(game.eventQueue.filter { it.tick == 50 }.count(), 2)
        assertEquals(game.eventQueue.filter { it.tick == 0 }.count(), 1) // The TransitEnd

        game.next(1)
        assertEquals(game.eventQueue.size, 3)   // 2 MakeDecisions, and a TransitEnd
        assertEquals(game.eventQueue.filter{it.action is MakeDecision}.count(), 2)
        assertEquals(game.eventQueue.filter { it.tick == 50 }.count(), 2)
        assertEquals(game.eventQueue.filter{ it.action is MakeDecision && it.action.player == 0}[0],
                Event(50, MakeDecision(0, 0)))
        assertEquals(game.eventQueue.filter{ it.action is MakeDecision && it.action.player == 1}[0],
                Event(50, MakeDecision(1, 0)))
    }

    @Test
    fun visibleActionDoesNotMoveMakeDecisionIfItIsAtMinActivationTime() {
        game.eventQueue.removeIf{it.action is MakeDecision}
        game.planEvent(50, MakeDecision(0, 0))
        game.planEvent(50, MakeDecision(1, 50))

        LaunchExpedition(PlayerId.Blue, 0, 1, .2, 0).apply(game)
        game.next(1)
        assertEquals(game.eventQueue.size, 3)   // 2 MakeDecisions, and a TransitEnd
        assertEquals(game.eventQueue.filter{it.action is MakeDecision}.count(), 2)
        assertEquals(game.eventQueue.filter { it.tick == 50 }.count(), 2)
        assertEquals(game.eventQueue.filter{ it.action is MakeDecision && it.action.player == 0}[0],
                Event(50, MakeDecision(0, 0)))
        assertEquals(game.eventQueue.filter{ it.action is MakeDecision && it.action.player == 1}[0],
                Event(50, MakeDecision(1, 50)))
    }

    @Test
    fun visibleActionMovesMakeDecisionToMinActivationTime() {
        game.eventQueue.removeIf{it.action is MakeDecision}
        game.planEvent(50, MakeDecision(0, 0))
        game.planEvent(50, MakeDecision(1, 20))

        LaunchExpedition(PlayerId.Blue, 0, 1, .2, 0).apply(game)
        game.next(1)
        assertEquals(game.eventQueue.size, 3)   // 2 MakeDecisions, and a TransitEnd
        assertEquals(game.eventQueue.filter{it.action is MakeDecision}.count(), 2)
        assertEquals(game.eventQueue.filter { it.tick == 50 }.count(), 1)
        assertEquals(game.eventQueue.filter{ it.action is MakeDecision && it.action.player == 0}[0],
                Event(50, MakeDecision(0, 0)))
        assertEquals(game.eventQueue.filter{ it.action is MakeDecision && it.action.player == 1}[0],
                Event(20, MakeDecision(1, 20)))
    }
}
