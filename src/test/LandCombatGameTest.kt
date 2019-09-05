package test

import agents.SimpleActionDoNothing
import agents.RHEA.*
import groundWar.*
import groundWar.EventGameParams
import math.Vec2d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.*

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
private val delayWorld = world.copy(params = params.copy(orderDelay = intArrayOf(10, 10))).deepCopy()
private val delayGame = LandCombatGame(delayWorld)

class TransitTest {

    @Test
    fun getCurrentTransitWhenTransitExists() {
        val gameCopy = game.copy()
        val transitToFind = Transit(Force(5.0), 0, 1, PlayerId.Blue, 5, 9)
        gameCopy.world.addTransit(transitToFind)
        assertTrue(getCurrentTransit(transitToFind, gameCopy) === transitToFind)
        assertEquals(getCurrentTransit(Transit(Force(5.0), 0, 1, PlayerId.Blue, 5, 9), gameCopy), transitToFind)
    }

    @Test
    fun getCurrentTransitWhenMatchExists() {
        val gameCopy = game.copy()
        val transitToFind = Transit(Force(5.0), 0, 1, PlayerId.Blue, 5, 9)
        gameCopy.world.addTransit(transitToFind)
        assertEquals(getCurrentTransit(Transit(Force(3.0), 0, 1, PlayerId.Blue, 5, 9), gameCopy), transitToFind)
    }

    @Test
    fun getCurrentTransitWhenNoMatchExists() {
        val gameCopy = game.copy()
        val transitToFind = Transit(Force(5.0), 0, 1, PlayerId.Blue, 5, 9)
        gameCopy.world.addTransit(transitToFind)
        assertEquals(getCurrentTransit(Transit(Force(5.0), 0, 1, PlayerId.Red, 5, 9), gameCopy), null)
        assertEquals(getCurrentTransit(Transit(Force(5.0), 0, 2, PlayerId.Blue, 5, 9), gameCopy), null)
        assertEquals(getCurrentTransit(Transit(Force(5.0), 0, 1, PlayerId.Blue, 5, 10), gameCopy), null)
    }

    @Test
    fun transitCreatedWithDelay() {
        val fullInvasion = delayGame.translateGene(0, intArrayOf(0, 1, 9, 1))
        val gameCopy = delayGame.copy()
        fullInvasion.apply(gameCopy)
        gameCopy.next(1)

        assertEquals(gameCopy.world.currentTransits.size, 0)
        gameCopy.next(10)
        assertEquals(gameCopy.world.currentTransits.size, 1)
        assertEquals(gameCopy.world.currentTransits[0], Transit(Force(10.0, timeStamp = 10), 0, 2, PlayerId.Blue, 10, 12))
    }

    @Test
    fun transitLaunchedAfterCityFallsIsIgnored() {
        // we set up a Red force to conquer a BLue city
        // with a Blue LaunchExpedition to leave the city that is delayed until after the city has fallen
        // we then check that the LaunchExpedition does nothing...no TransitStart or TransitEnd generated
        // and MakeDecision still correctly in place
        val gameCopy = delayGame.copy()
        gameCopy.world.cities[1].pop = Force(20.0)
        LaunchExpedition(PlayerId.Red, 1, 0, 1.0, 32).apply(gameCopy)
        gameCopy.next(11)

        LaunchExpedition(PlayerId.Blue, 0, 2, 1.0, 32).apply(gameCopy)
        gameCopy.next(11)

        assertEquals(gameCopy.world.cities[0].owner, PlayerId.Red)
        assertEquals(gameCopy.eventQueue.size, 1)
        assertEquals(gameCopy.eventQueue.filter { it.action is TransitEnd }.size, 1)
        assertEquals(gameCopy.eventQueue.peek().action, TransitEnd(PlayerId.Blue, 0, 2, 23))

        assertEquals(gameCopy.world.currentTransits.size, 0)
    }

    @Test
    fun transitLaunchedAfterPopReducedIsUpdated() {
        val gameCopy = delayGame.copy()
        LaunchExpedition(PlayerId.Red, 1, 0, 0.5, 32).apply(gameCopy)
        gameCopy.next(11)

        LaunchExpedition(PlayerId.Blue, 0, 2, 1.0, 32).apply(gameCopy)
        gameCopy.next(11)

        assertEquals(gameCopy.world.cities[0].owner, PlayerId.Blue)
        assertEquals(gameCopy.world.cities[0].pop.size, 0.0)

        assertEquals(gameCopy.eventQueue.size, 1)
        assertEquals(gameCopy.eventQueue.filter { it.action is TransitEnd }.size, 1)
        assertEquals(gameCopy.eventQueue.peek().action, TransitEnd(PlayerId.Blue, 0, 2, 23))

        assertEquals(gameCopy.world.currentTransits.size, 1)
        assertEquals(gameCopy.world.currentTransits[0].fromCity, 0)
        assertEquals(gameCopy.world.currentTransits[0].toCity, 2)
        assertEquals(gameCopy.world.currentTransits[0].playerId, PlayerId.Blue)
        assertEquals(gameCopy.world.currentTransits[0].startTime, 21)
        assertEquals(gameCopy.world.currentTransits[0].endTime, 23)
        assertEquals(gameCopy.world.currentTransits[0].force.size, 7.47, 0.01)
        assertEquals(gameCopy.world.currentTransits[0].force.effectiveSize, 7.47, 0.01)
    }

    @Test
    fun transitHasMaxForce() {
        val fullInvasion = game.translateGene(0, intArrayOf(0, 1, 9, 1))
        // 0 = cityFrom, 1 = 2nd route (hence to 2)
        assert(fullInvasion is LaunchExpedition)
        val gameCopy = game.copy()
        fullInvasion.apply(gameCopy)
        gameCopy.next(1)
        assertEquals(gameCopy.world.currentTransits.size, 1)
        val transit = gameCopy.world.currentTransits.first()
        assertEquals(transit.fromCity, 0)
        assertEquals(transit.toCity, 2)
        assertEquals(transit.playerId, PlayerId.Blue)
        assertEquals(transit.force.size, 10.0)
        assertEquals(transit.force.effectiveSize, 10.0)
        assertEquals(transit.startTime, 0)
        assertEquals(transit.endTime, 2)
    }

    @Test
    fun transitHasMinimumOfOne() {
        val tokenInvasion = game.translateGene(1, intArrayOf(1, 0, 0, 1))
        assert(tokenInvasion is LaunchExpedition)
        val gameCopy = game.copy()
        gameCopy.world.cities[1].pop = Force(1.0)
        tokenInvasion.apply(gameCopy)
        gameCopy.next(1)
        assertEquals(gameCopy.world.currentTransits.size, 1)
        val transit = gameCopy.world.currentTransits.first()
        assertEquals(transit.fromCity, 1)
        assertEquals(transit.toCity, 0)
        assertEquals(transit.playerId, PlayerId.Red)
        assertEquals(transit.force.size, 1.0)
        assertEquals(transit.startTime, 0)
        assertEquals(transit.endTime, 4)
    }

    @Test
    fun transitCollisionAtHalfwayMark() {
        val gameCopy = game.copy()
        val arrivalTime = gameCopy.nTicks() + (20.0 / world.params.speed[0]).toInt()
        assertEquals(arrivalTime, 4)
        assertEquals(gameCopy.nTicks(), 0)
        val oneWay = Transit(Force(5.0), 0, 1, PlayerId.Blue, 0, arrivalTime)
        val otherWay = Transit(Force(7.0), 1, 0, PlayerId.Red, 0, arrivalTime)
        // note that the endTime on the Transit
        assertEquals(oneWay.currentPosition(0, gameCopy.world.cities).x, 0.0)
        assertEquals(oneWay.currentPosition(0, gameCopy.world.cities).y, 0.0)
        assertEquals(otherWay.currentPosition(0, gameCopy.world.cities).x, 0.0)
        assertEquals(otherWay.currentPosition(0, gameCopy.world.cities).y, 20.0)
        assert(gameCopy.world.nextCollidingTransit(otherWay, gameCopy.nTicks()) == null)
        gameCopy.world.addTransit(oneWay)
        assert(gameCopy.world.nextCollidingTransit(otherWay, gameCopy.nTicks()) == oneWay)
        assertEquals(oneWay.collisionEvent(otherWay, gameCopy.world, gameCopy.nTicks()).tick, 2)
        assertEquals(otherWay.collisionEvent(oneWay, gameCopy.world, gameCopy.nTicks()).tick, 2)
    }

    @Test
    fun transitEndTakesPriorityOverTransitStart() {
        val gameCopy = game.copy()
        val blueMove = LaunchExpedition(PlayerId.Blue, 0, 1, 0.9, 0)
        val redMove = LaunchExpedition(PlayerId.Red, 1, 0, 0.5, 0)
        gameCopy.planEvent(4, redMove)
        gameCopy.planEvent(0, blueMove)
        gameCopy.next(1)
        assertEquals(gameCopy.world.currentTransits[0].endTime, 4)
        // should arrive at t = 4
        gameCopy.next(3)
        assertEquals(gameCopy.world.currentTransits[0].endTime, 4)
        gameCopy.next(1)
        assertFalse(gameCopy.eventQueue.history.any { it.action is Battle })
        assertEquals(gameCopy.eventQueue.history.filter { it.action is TransitStart }.size, 2)
        assertEquals(gameCopy.eventQueue.history.filter { it.action is TransitEnd }.size, 1)
        assertEquals(gameCopy.eventQueue.filter { it.action is TransitEnd }.size, 1)
        assertEquals(gameCopy.world.cities[0].owner, PlayerId.Blue)
        assertEquals(gameCopy.world.cities[1].owner, PlayerId.Red)
        assertEquals(gameCopy.world.currentTransits.size, 1)
        assertEquals(gameCopy.world.currentTransits[0].playerId, PlayerId.Red)
    }


    @Test
    fun slowBehindFastWillNotCollide() {
        val t1 = Transit(Force(10.0), 0, 2, PlayerId.Red, 0, 4)
        val t2 = Transit(Force(5.0), 0, 2, PlayerId.Blue, 1, 8)
        assertFalse(t1.willCollideAt(t2, world, 1).first)
    }
}

object BattleTest {

    @Test
    fun battleEventSetAtCorrectTime() {
        val gameCopy = game.copy()
        val fullInvasion = gameCopy.translateGene(0, intArrayOf(0, 0, 2, 1))
        // 0 = cityFrom, 0 = 1st route (hence to 1)
        val opposingForce = gameCopy.translateGene(1, intArrayOf(1, 0, 2, 1))
        // 1 = cityFrom, 0 = 1st route (hence to 0)
        fullInvasion.apply(gameCopy)
        opposingForce.apply(gameCopy)
        gameCopy.next(1)
        val nextEvent = gameCopy.eventQueue.peek()
        assertEquals(nextEvent.tick, 2)
        assert(nextEvent.action is Battle)
    }

    @Test
    fun battleEventRemovesAndCreatesTransitsCorrectly() {
        val gameCopy = game.copy()
        val fullInvasion = gameCopy.translateGene(0, intArrayOf(0, 0, 9, 1))
        // 0 = cityFrom, 0 = 1st route (hence to 1)
        val opposingForce = gameCopy.translateGene(1, intArrayOf(1, 0, 5, 1))
        // 1 = cityFrom, 0 = 1st route (hence to 0)
        fullInvasion.apply(gameCopy)
        opposingForce.apply(gameCopy)
        gameCopy.next(1)
        val nextEvent = gameCopy.eventQueue.peek()
        assert(nextEvent.action is Battle)
        assertEquals(nextEvent.tick, 2)
        val startingTransits = gameCopy.world.currentTransits.toList()
        assertEquals(startingTransits.size, 2)
        assertEquals(startingTransits[0], Transit(Force(10.0), 0, 1, PlayerId.Blue, 0, 4))
        assert(abs(startingTransits[1].force.size - 6.0) < 0.01)
        assert(abs(startingTransits[1].force.effectiveSize - 6.0) < 0.01)
        assertEquals(startingTransits[1], Transit(Force(startingTransits[1].force.size), 1, 0, PlayerId.Red, 0, 4))
        gameCopy.next(2)
        val endingTransits = gameCopy.world.currentTransits.toList()
        assertEquals(endingTransits.size, 1)
        assert(abs(endingTransits[0].force.size - 6.592) < 0.01)
        assert(abs(endingTransits[0].force.effectiveSize - 6.592) < 0.01)
        assertEquals(endingTransits[0], Transit(Force(endingTransits[0].force.size, timeStamp = 2), 0, 1, PlayerId.Blue, 0, 4))
        assert(endingTransits[0] !== startingTransits[0])
    }

    @Test
    fun battleEventCreatesNextBattleCorrectly() {
        val gameCopy = game.copy()
        val fullInvasion = gameCopy.translateGene(0, intArrayOf(0, 0, 9, 1))
        // 0 = cityFrom, 0 = 1st route (hence to 1)
        val opposingForce = gameCopy.translateGene(1, intArrayOf(1, 0, 5, 1))
        // 1 = cityFrom, 0 = 1st route (hence to 0)
        fullInvasion.apply(gameCopy)
        opposingForce.apply(gameCopy)
        assertEquals(gameCopy.eventQueue.filter { e -> e.action is TransitStart }.size, 2)
        assertEquals(gameCopy.eventQueue.filter { e -> e.action is TransitEnd }.size, 2)

        gameCopy.next(1)
        assertEquals(gameCopy.eventQueue.filter { e -> e.action is Battle }.size, 1)

        val opposingForce2 = gameCopy.translateGene(1, intArrayOf(1, 0, 9, 1))
        // 1 = cityFrom, 0 = 1st route (hence to 0)
        opposingForce2.apply(gameCopy)
        assertEquals(gameCopy.eventQueue.filter { e -> e.action is TransitStart }.size, 1)
        gameCopy.next(1)

        val nextEvent = gameCopy.eventQueue.peek()
        assert(nextEvent.action is Battle)
        assertEquals(nextEvent.tick, 2)
        val startingTransits = gameCopy.world.currentTransits.toList()
        assertEquals(startingTransits.size, 3)
        assertEquals(startingTransits[0], Transit(Force(10.0), 0, 1, PlayerId.Blue, 0, 4))
        assert(abs(startingTransits[1].force.size - 6.0) < 0.01)
        assertEquals(startingTransits[1], Transit(Force(startingTransits[1].force.size), 1, 0, PlayerId.Red, 0, 4))
        assert(abs(startingTransits[2].force.effectiveSize - 4.0) < 0.01)
        assertEquals(startingTransits[2], Transit(Force(startingTransits[2].force.size, timeStamp = 1), 1, 0, PlayerId.Red, 1, 5))
        gameCopy.eventQueue.poll()
        nextEvent.action.apply(gameCopy)
        val endingTransits = gameCopy.world.currentTransits.toList() // after battle
        assertEquals(endingTransits.size, 2)
        assert(abs(endingTransits[1].force.size - 6.592) < 0.01)
        val forceSizeAfterBattle = endingTransits[1].force.size
        assertEquals(endingTransits[1], Transit(Force(endingTransits[1].force.size, timeStamp = 2), 0, 1, PlayerId.Blue, 0, 4))
        assert(abs(endingTransits[0].force.effectiveSize - 4.0) < 0.01)
        assertEquals(endingTransits[0], Transit(Force(endingTransits[0].force.size, timeStamp = 1), 1, 0, PlayerId.Red, 1, 5))
        val battleEvents = gameCopy.eventQueue.filter { e -> e.action is Battle }
        assertEquals(battleEvents.size, 2)
        // we have two battles - one created when opposingForce2 was generated against the ful invasion force
        // the second created after the battle by the surviving red force against opposingForce2
        // the second of these to be processed will be ignored
        assertEquals(battleEvents[0].action, Battle(Transit(Force(3.999999999999999, 0.0, 1), 1, 0, PlayerId.Red, 1, 5),
                Transit(Force(10.0, 0.0, 0), 0, 1, PlayerId.Blue, 0, 4)))
        assertEquals(battleEvents[1].action, Battle(Transit(Force(forceSizeAfterBattle, 0.0, 2), 0, 1, PlayerId.Blue, 0, 4),
                Transit(Force(3.999999999999999, 0.0, 1), 1, 0, PlayerId.Red, 1, 5)))
    }

    @Test
    fun battleOccursAtCorrectPointBetweenForcesMovingAtDifferentSpeeds() {
        val newParams = params.copy(speed = doubleArrayOf(1.0, 5.0))
        val world = world.copy(params = newParams).deepCopy()
        // so Blue moves at 20% of Red
        val gameCopy = LandCombatGame(world)
        val blueMove = LaunchExpedition(PlayerId.Blue, 0, 1, 1.0, 0)
        val redMove = LaunchExpedition(PlayerId.Red, 1, 0, 0.5, 0)
        blueMove.apply(gameCopy)
        redMove.apply(gameCopy)
        gameCopy.next(1)
        assertEquals(gameCopy.eventQueue.filter { it.action is Battle }.size, 1)
        val battleEvent = gameCopy.eventQueue.first { it.action is Battle }
        assertEquals(battleEvent.tick, (20.0 / 6.0).toInt()) // commbined speed of the forces is 6.0
        gameCopy.next(3)
        assertEquals(gameCopy.eventQueue.filter { it.action is Battle }.size, 0)
        assertEquals(gameCopy.world.currentTransits.size, 1)
    }

    @Test
    fun fastMovingForceCanCatchUpWithSlowMovingEnemy() {
        val newParams = params.copy(speed = doubleArrayOf(1.0, 5.0))
        val world = world.copy(params = newParams).deepCopy()
        // so Blue moves at 20% of Red
        val gameCopy = LandCombatGame(world)
        val redMove = LaunchExpedition(PlayerId.Red, 1, 0, 0.5, 0) // will take 4 ticks
        redMove.apply(gameCopy)
        val blueMove = LaunchExpedition(PlayerId.Blue, 0, 2, 1.0, 0) // will take 10 ticks
        blueMove.apply(gameCopy)
        gameCopy.next(5)
        assertEquals(world.cities[0].owner, PlayerId.Red)
        val redMove2 = LaunchExpedition(PlayerId.Red, 0, 2, 0.5, 0) // will take 2 ticks, on the second of which it should encounter the blue force
        redMove2.apply(gameCopy)
        gameCopy.next(1)
        assertEquals(gameCopy.eventQueue.filter { it.action is Battle }.size, 1)
        gameCopy.next(3)
        // by this point the blue force should not have reached its destination, but the red force would have done
        assertEquals(world.cities[2].owner, PlayerId.Neutral)
    }

    @Test
    fun slowMovingForceDoesNotCatchUpWithFasterEnemy() {
        val newParams = params.copy(speed = doubleArrayOf(5.0, 4.0))
        val world = world.copy(params = newParams).deepCopy()
        // so Blue moves at 125% of Red
        val gameCopy = LandCombatGame(world)
        val redMove = LaunchExpedition(PlayerId.Red, 1, 0, 0.5, 0) // will take 5 ticks
        redMove.apply(gameCopy)
        gameCopy.next(4)
        gameCopy.planEvent(4, LaunchExpedition(PlayerId.Blue, 0, 2, 1.0, 0)) // will take 2 ticks
        gameCopy.planEvent(5, LaunchExpedition(PlayerId.Red, 0, 2, 1.0, 0))
        gameCopy.next(2)
        assertEquals(world.cities[0].owner, PlayerId.Red)
        assertEquals(gameCopy.eventQueue.filter { it.action is Battle }.size, 0)
    }

    @Test
    fun aMoreComplicatedScenario() {
        // RED force leaves city, with a BLUE force moving towards it and a RED force behind that BLUE force moving in the same direction
        // When RED leaves the city therefore, it calculates its first enemy encounter is with  the BLUE force
        // However the RED force behind it will attack it first, so the Transit will not be present when the main RED force arrives
        val newParams = params.copy(speed = doubleArrayOf(2.0, 5.0))
        val world = world.copy(params = newParams).deepCopy()
        val gameCopy = LandCombatGame(world)

        gameCopy.planEvent(0, LaunchExpedition(PlayerId.Blue, 0, 1, 0.5, 0)) // will arrive at tick 10
        gameCopy.planEvent(1, CityInflux(PlayerId.Red, Force(20.0), 0, -1))
        gameCopy.planEvent(2, LaunchExpedition(PlayerId.Red, 0, 1, 1.0, 0)) // will arrive at tick 6
        gameCopy.planEvent(2, LaunchExpedition(PlayerId.Red, 1, 0, 1.0, 0)) // will arrive at tick 6

        gameCopy.next(3)
        assertEquals(gameCopy.eventQueue.filter { it.action is Battle }.size, 2)
        gameCopy.next(2)
        assertEquals(gameCopy.eventQueue.filter { it.action is Battle }.size, 0)
        gameCopy.next(2)
        assertEquals(gameCopy.world.cities[0].owner, PlayerId.Red)
        assertEquals(gameCopy.world.cities[0].pop.size, 10.0)
    }

}

class MakeDecisionTest {

    @Test
    fun makeDecisionSpawnedOnAgentRegistrationNotLaunchExpedition() {
        val fullInvasion = game.translateGene(0, intArrayOf(0, 1, 2))
        // 0 = cityFrom, 1 = 2nd route (hence to 2)
        assert(fullInvasion is LaunchExpedition)
        val gameCopy = game.copy()
        assertEquals(gameCopy.eventQueue.size, 0)
        assertEquals(fullInvasion.nextDecisionPoint(0, gameCopy).first, 10)
        fullInvasion.apply(gameCopy)
        gameCopy.next(1)
        assertEquals(gameCopy.eventQueue.size, 1)         // no MakeDecision created
        gameCopy.registerAgent(0, SimpleActionEvoAgent())
        assertEquals(gameCopy.eventQueue.size, 2)           // Make Decision now added
        val secondEvent = gameCopy.eventQueue.poll()
        assert(secondEvent.action is MakeDecision)
        assert((secondEvent.action as MakeDecision).playerRef == 0)
        assertEquals(secondEvent.tick, gameCopy.nTicks())
        val firstAction = gameCopy.eventQueue.poll().action
        assert(firstAction is TransitEnd)
    }


    @Test
    fun timeUntilNextDecisionObeysDefaultOODALoop() {
        assertEquals(world.params.OODALoop[0], 10);
        val fullInvasion = game.translateGene(0, intArrayOf(0, 1, 2, 1))
        val gameCopy = game.copy()
        assertEquals(fullInvasion.nextDecisionPoint(0, gameCopy).first, 10)
    }

    @Test
    fun registeringAgentTwiceDoesNotCreateSecondMakeDecision() {
        val gameCopy = game.copy()
        assertEquals(gameCopy.eventQueue.size, 0)         // no MakeDecision created
        gameCopy.registerAgent(0, SimpleActionEvoAgent())
        assertEquals(gameCopy.eventQueue.size, 1)           // Make Decision now added
        val secondEvent = gameCopy.eventQueue.peek()
        assert(secondEvent.action is MakeDecision)
        gameCopy.registerAgent(0, SimpleActionEvoAgent())
        assertEquals(gameCopy.eventQueue.size, 1)           // Make Decision not added again

        gameCopy.registerAgent(1, SimpleActionEvoAgent())
        assertEquals(gameCopy.eventQueue.size, 2)           // Make Decision added for different agent
    }

    @Test
    fun registeringADoNothingAgentWillRemoveExistingMakeDecisions() {
        val gameCopy = game.copy()
        assertEquals(gameCopy.eventQueue.size, 0)         // no MakeDecision created
        gameCopy.registerAgent(0, SimpleActionEvoAgent())
        assertEquals(gameCopy.eventQueue.size, 1)           // Make Decision now added
        gameCopy.registerAgent(0, SimpleActionDoNothing(1000))
        assertEquals(gameCopy.eventQueue.size, 0)           // Make Decision removed
    }

    @Test
    fun makingADecisionWillCreateANewMakeDecision() {
        val gameCopy = game.copy()
        gameCopy.registerAgent(0, SimpleActionEvoAgent())
        assertEquals(gameCopy.eventQueue.size, 1)         // no MakeDecision created
        val event = gameCopy.eventQueue.poll()
        assert(event.action is MakeDecision)
        assertEquals(gameCopy.eventQueue.size, 0)
        event.action.apply(gameCopy)
        assertEquals(gameCopy.eventQueue.count { e -> e.action is MakeDecision && e.action.playerRef == 0 }, 1)
    }

    @Test
    fun cloningStateDoesNotAddNewMakeDecisions() {
        val gameCopy = game.copy()
        gameCopy.registerAgent(0, SimpleActionEvoAgent())
        assertEquals(gameCopy.eventQueue.size, 1)       // MakeDecision created
        val gameCopyCopy = gameCopy.copy()
        assertEquals(gameCopyCopy.eventQueue.size, 1)         // no MakeDecision created
    }

    @Test
    fun makeDecisionEventsAreSortedFirstInEventQueue() {
        val gameCopy = game.copy()
        gameCopy.planEvent(6, LaunchExpedition(PlayerId.Red, 6, 3, .5, 10))
        gameCopy.planEvent(3, LaunchExpedition(PlayerId.Blue, 2, 3, .5, 10))
        gameCopy.planEvent(3, MakeDecision(0, 0))
        gameCopy.planEvent(3, MakeDecision(1, 0))
        gameCopy.planEvent(4, TransitStart(Transit(Force(3.0), 2, 5, PlayerId.Red, 3, 34)))

        assertEquals(gameCopy.eventQueue.poll().action, MakeDecision(0, 0))
        assertEquals(gameCopy.eventQueue.poll().action, MakeDecision(1, 0))
        assertEquals(gameCopy.eventQueue.poll().action, LaunchExpedition(PlayerId.Blue, 2, 3, .5, 10))
        assertEquals(gameCopy.eventQueue.poll().action, TransitStart(Transit(Force(3.0), 2, 5, PlayerId.Red, 3, 34)))
        assertEquals(gameCopy.eventQueue.poll().action, LaunchExpedition(PlayerId.Red, 6, 3, .5, 10))
    }

    @Test
    fun makeDecisionsGenerateNewMakeDecisions() {
        val gameCopy = game.copy()
        gameCopy.registerAgent(0, SimpleActionEvoAgent())
        assertEquals(gameCopy.eventQueue.size, 1)
        assertEquals(gameCopy.eventQueue.history.size, 0)
        gameCopy.next(1)

        assertTrue(gameCopy.eventQueue.size in listOf(1, 2))
        assertTrue(gameCopy.eventQueue.any { e -> e.action is MakeDecision && e.action.playerRef == 0 })

        assertTrue(gameCopy.eventQueue.history.size in listOf(2, 3))
        assertEquals(gameCopy.eventQueue.history[0].action, MakeDecision(0, 0))
        assertFalse(gameCopy.eventQueue.history[1].action is MakeDecision)
    }
}

class LandCombatStateRepresentationTests {

    @Test
    fun stateRepresentationIsAString() {
        val stateRep = LandCombatStateFunction(game)
        assertFalse(stateRep.isEmpty())
        assertEquals(stateRep.count { it == '|' }, 1 + game.world.cities.size + game.world.routes.size)
    }
}

class TranslateGeneTests {

    val cityCreationParams = EventGameParams(seed = 3, minConnections = 2, autoConnect = 300, maxDistance = 1000)

    @Test
    fun basicTranslateGeneWithFewerThanTenCities() {
        val game1 = LandCombatGame(World(params = cityCreationParams.copy(nAttempts = 8)))
        assertTrue(game1.world.cities.size <= 8)

        game1.world.cities[1].pop = Force(10.0)
        game1.world.cities[1].owner = PlayerId.Blue
        val destinations = game1.world.allRoutesFromCity[1]?.size ?: 0
        var expedition = game1.translateGene(0, intArrayOf(1, 1, 9, 0)) as LaunchExpedition
        var expectedDestination = game1.world.allRoutesFromCity[1]!![1].toCity
        assertEquals(expedition, LaunchExpedition(PlayerId.Blue, 1, expectedDestination, 1.0, 10))

        expedition = game1.translateGene(0, intArrayOf(1, 1 + destinations, 9, 0)) as LaunchExpedition
        assertEquals(expedition, LaunchExpedition(PlayerId.Blue, 1, expectedDestination, 1.0, 10))

        expedition = game1.translateGene(0, intArrayOf(1 + game1.world.cities.size, 1 + destinations, 9, 0)) as LaunchExpedition
        assertEquals(expedition, LaunchExpedition(PlayerId.Blue, 1, expectedDestination, 1.0, 10))
    }

    @Test
    fun translateGeneWithMoreThanTenCities() {
        val game2 = LandCombatGame(World(params = cityCreationParams.copy(nAttempts = 50)))
        assertTrue(game2.world.cities.size > 10)
        assertTrue(game2.world.allRoutesFromCity.all { (_, v) -> v.size <= 10 })

        game2.world.cities[12].pop = Force(10.0)
        game2.world.cities[12].owner = PlayerId.Blue
        val destinations = game2.world.allRoutesFromCity[12]?.size ?: 0
        assertTrue(destinations <= 8)

        var expedition = game2.translateGene(0, intArrayOf(1, 2, 1, 9, 1)) as LaunchExpedition
        var expectedDestination = game2.world.allRoutesFromCity[12]!![1].toCity
        assertEquals(expedition, LaunchExpedition(PlayerId.Blue, 12, expectedDestination, 1.0, 10))

        expedition = game2.translateGene(0, intArrayOf(1, 2, 1 + destinations, 9, 1)) as LaunchExpedition
        assertEquals(expedition, LaunchExpedition(PlayerId.Blue, 12, expectedDestination, 1.0, 10))

        val totalCityCode = 12 + 2 * game2.world.cities.size
        expedition = game2.translateGene(0, intArrayOf(totalCityCode / 10, totalCityCode % 10, 1 + destinations, 9, 2)) as LaunchExpedition
        assertEquals(expedition, LaunchExpedition(PlayerId.Blue, 12, expectedDestination, 1.0, 10))
    }

    @Test
    fun translateGeneWithMoreThanTenCitiesAndMoreThanTenRoutes() {
        val game3 = LandCombatGame(World(params = cityCreationParams.copy(nAttempts = 100)))
        assertTrue(game3.world.cities.size > 10)
        assertTrue(game3.world.allRoutesFromCity.any { (_, v) -> v.size > 10 })

        game3.world.cities[2].pop = Force(10.0)
        game3.world.cities[2].owner = PlayerId.Blue
        val destinations = game3.world.allRoutesFromCity[2]?.size ?: 0

        var expedition = game3.translateGene(0, intArrayOf(0, 2, 0, 1, 9, 1)) as LaunchExpedition
        var expectedDestination = game3.world.allRoutesFromCity[2]!![1].toCity
        assertEquals(expedition, LaunchExpedition(PlayerId.Blue, 2, expectedDestination, 1.0, 10))

        val totalRouteCode = 1 + destinations * 6
        expedition = game3.translateGene(0, intArrayOf(0, 2, totalRouteCode / 10, totalRouteCode % 10, 9, 1)) as LaunchExpedition
        assertEquals(expedition, LaunchExpedition(PlayerId.Blue, 2, expectedDestination, 1.0, 10))

        val totalCityCode = 2 + game3.world.cities.size
        expedition = game3.translateGene(0, intArrayOf(totalCityCode / 10, totalCityCode % 10, totalRouteCode / 10, totalRouteCode % 10, 9)) as LaunchExpedition
        assertEquals(expedition, LaunchExpedition(PlayerId.Blue, 2, expectedDestination, 1.0, 10))
    }
}