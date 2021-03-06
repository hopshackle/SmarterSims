package test


import ggi.InterruptibleWait
import groundWar.*
import math.Vec2d
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import kotlin.streams.toList


private val cities = listOf(
        City(Vec2d(0.0, 0.0), 0, Force(10.0), PlayerId.Blue),
        City(Vec2d(0.0, 20.0), 0, Force(10.0), PlayerId.Red),
        City(Vec2d(0.0, 10.0), 0, owner = PlayerId.Neutral),
        City(Vec2d(20.0, 20.0), 0, Force(10.0), PlayerId.Red)
)
private val routes = listOf(
        Route(0, 2, 10.0, 1.0),
        Route(1, 2, 10.0, 1.0),
        Route(2, 0, 10.0, 1.0),
        Route(2, 1, 10.0, 1.0),
        Route(3, 1, 10.0, 1.0),
        Route(1, 3, 10.0, 1.0)
)

private val params = EventGameParams(speed = doubleArrayOf(5.0, 5.0), width = 30, height = 30, seed = 10)
private var world: World = World(cities, routes, params)
private var game: LandCombatGame = LandCombatGame(world)

private val attackOnly = HeuristicAgent(3.0, 1.0, listOf(HeuristicOptions.ATTACK))
private val retreatAttack = HeuristicAgent(3.0, 1.0, listOf(HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK))
private val reinforceRetreatAttack = HeuristicAgent(3.0, 1.0, listOf(HeuristicOptions.REINFORCE, HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK))
private val reinforceSmall = HeuristicAgent(3.0, 0.5, listOf(HeuristicOptions.REINFORCE, HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK))
private val retreatAttackRedeploy = HeuristicAgent(3.0, 1.0, listOf(HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK, HeuristicOptions.REDEPLOY))
private val reinforceRedeployRetreatAttack = HeuristicAgent(3.0, 1.0, listOf(HeuristicOptions.REINFORCE, HeuristicOptions.REDEPLOY, HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK))

class ReinforceTests {
    @BeforeEach
    fun init() {
        world =  World(cities, routes, params)
        game = LandCombatGame(world).copy()
    }
    @Test
    fun simpleAttackTest() {
        assertEquals(attackOnly.getAction(game ,0), LaunchExpedition(PlayerId.Blue, 0, 2, 0.0, 10))
        assertEquals(attackOnly.getAction(game ,1), LaunchExpedition(PlayerId.Red, 1, 2, 0.0, 10))
        assertEquals(retreatAttack.getAction(game ,0), LaunchExpedition(PlayerId.Blue, 0, 2, 0.0, 10))
        assertEquals(retreatAttack.getAction(game ,1), LaunchExpedition(PlayerId.Red, 1, 2, 0.0, 10))
        assertEquals(retreatAttackRedeploy.getAction(game ,0), LaunchExpedition(PlayerId.Blue, 0, 2, 0.0, 10))
        assertEquals(retreatAttackRedeploy.getAction(game ,1), LaunchExpedition(PlayerId.Red, 1, 2, 0.0, 10))
        assertEquals(reinforceRetreatAttack.getAction(game ,0), LaunchExpedition(PlayerId.Blue, 0, 2, 0.0, 10))
        assertEquals(reinforceRetreatAttack.getAction(game ,1), LaunchExpedition(PlayerId.Red, 1, 2, 0.0, 10))
        assertEquals(reinforceRedeployRetreatAttack.getAction(game ,0), LaunchExpedition(PlayerId.Blue, 0, 2, 0.0, 10))
        assertEquals(reinforceRedeployRetreatAttack.getAction(game ,1), LaunchExpedition(PlayerId.Red, 1, 2, 0.0, 10))
    }
    @Test
    fun attackOrReinforceLoneOutpost() {
        LaunchExpedition(PlayerId.Red, 1, 2, 0.0, 10).apply(game)
        game.next(10)
        assertEquals(game.world.cities[2].owner, PlayerId.Red)
        assertEquals(game.world.cities[2].pop.size, 1.0)

        assertEquals(attackOnly.getAction(game ,0), LaunchExpedition(PlayerId.Blue, 0, 2, 0.3, 10))
        assertEquals(attackOnly.getAction(game ,1), InterruptibleWait(1, 10))
        assertEquals(retreatAttack.getAction(game ,0), LaunchExpedition(PlayerId.Blue, 0, 2, 0.3, 10))
        assertEquals(retreatAttack.getAction(game ,1), InterruptibleWait(1, 10))
        assertEquals(retreatAttackRedeploy.getAction(game ,0), LaunchExpedition(PlayerId.Blue, 0, 2, 0.3, 10))
        assertEquals(retreatAttackRedeploy.getAction(game ,1), LaunchExpedition(PlayerId.Red, 1, 2, 29.0/9.0, 10))
        assertEquals(reinforceRetreatAttack.getAction(game ,0), LaunchExpedition(PlayerId.Blue, 0, 2, 0.3, 10))
        assertEquals(reinforceRetreatAttack.getAction(game ,1), LaunchExpedition(PlayerId.Red, 1, 2, 1.0, 10))
        assertEquals(reinforceRedeployRetreatAttack.getAction(game ,0), LaunchExpedition(PlayerId.Blue, 0, 2, 0.3, 10))
        assertEquals(reinforceRedeployRetreatAttack.getAction(game ,1), LaunchExpedition(PlayerId.Red, 1, 2, 1.0, 10))
    }
    @Test
    fun staticFrontLine() {
        LaunchExpedition(PlayerId.Red, 1, 2, 1.0, 10).apply(game)
        game.next(10)
        assertEquals(game.world.cities[2].owner, PlayerId.Red)
        assertEquals(game.world.cities[2].pop.size, 10.0)
        assertEquals(game.world.cities[1].owner, PlayerId.Red)
        assertEquals(game.world.cities[1].pop.size, 0.0)

        assertEquals(attackOnly.getAction(game ,0), InterruptibleWait(0, 10))
        assertEquals(attackOnly.getAction(game ,1), InterruptibleWait(1, 10))
        assertEquals(retreatAttack.getAction(game ,0), InterruptibleWait(0, 10))
        assertEquals(retreatAttack.getAction(game ,1), InterruptibleWait(1, 10))
        assertEquals(retreatAttackRedeploy.getAction(game ,0), InterruptibleWait(0, 10))
        assertEquals(retreatAttackRedeploy.getAction(game ,1), InterruptibleWait(1, 10))
        assertEquals(reinforceRetreatAttack.getAction(game ,0), InterruptibleWait(0, 10))
        assertEquals(reinforceRetreatAttack.getAction(game ,1), InterruptibleWait(1, 10))
        assertEquals(reinforceRedeployRetreatAttack.getAction(game ,0), InterruptibleWait(0, 10))
        assertEquals(reinforceRedeployRetreatAttack.getAction(game ,1), InterruptibleWait(1, 10))
    }

    @Test
    fun reinforceOrRetreatFromLoneOutpost() {
        LaunchExpedition(PlayerId.Red, 1, 2, 0.0, 10).apply(game)
        game.next(10)
        LaunchExpedition(PlayerId.Blue, 0, 2, 0.3, 10).apply(game)
        game.next(1)
        assertEquals(game.world.cities[2].owner, PlayerId.Red)
        assertEquals(game.world.cities[2].pop.size, 1.0)
        assertEquals(game.world.currentTransits.size, 1)
        assertEquals(game.world.currentTransits[0].force.size, 3.0)

        assertEquals(attackOnly.getAction(game ,1), InterruptibleWait(1, 10))
        assertEquals(retreatAttack.getAction(game ,1), LaunchExpedition(PlayerId.Red, 2, 1, 1.0, 10))
        assertEquals(retreatAttackRedeploy.getAction(game ,1), LaunchExpedition(PlayerId.Red, 2, 1, 1.0, 10))
        assertEquals(reinforceRetreatAttack.getAction(game ,1), LaunchExpedition(PlayerId.Red, 1, 2, 6.0/9.0, 10))
        assertEquals(reinforceRedeployRetreatAttack.getAction(game ,1), LaunchExpedition(PlayerId.Red, 1, 2, 6.0/9.0, 10))
        // because the biggest threat is the 7 still sitting at home base
    }

    @Test
    fun reinforcementSendsJustEnoughToMeetDefenseRatio() {
        LaunchExpedition(PlayerId.Red, 1, 2, 0.0, 10).apply(game)
        game.next(10)
        assertEquals(game.world.cities[2].owner, PlayerId.Red)
        assertEquals(game.world.cities[2].pop.size, 1.0)
        assertEquals(game.world.cities[1].pop.size, 9.0)

        assertEquals(reinforceRetreatAttack.getAction(game ,1), LaunchExpedition(PlayerId.Red, 1, 2, 1.0, 10))
        assertEquals(reinforceSmall.getAction(game ,1), LaunchExpedition(PlayerId.Red, 1, 2, 4.0/9.0, 10))
    }

    @Test
    fun redeploymentToLaunchFutureAttack() {
        LaunchExpedition(PlayerId.Blue, 0, 2, 0.5, 10).apply(game)
        game.next(10)

        assertEquals(attackOnly.getAction(game ,0), InterruptibleWait(0, 10))
        assertEquals(attackOnly.getAction(game ,1), InterruptibleWait(1, 10))
        assertEquals(retreatAttack.getAction(game ,0), InterruptibleWait(0, 10))
        assertEquals(retreatAttack.getAction(game ,1), InterruptibleWait(1, 10))
        assertEquals(retreatAttackRedeploy.getAction(game ,0),LaunchExpedition(PlayerId.Blue, 0, 2, 5.0, 10))
        assertEquals(retreatAttackRedeploy.getAction(game ,1), LaunchExpedition(PlayerId.Red, 3, 1, 0.5, 10))
        assertEquals(reinforceSmall.getAction(game ,0), InterruptibleWait(0, 10))
        assertEquals(reinforceSmall.getAction(game ,1), InterruptibleWait(1, 10))
        assertEquals(reinforceRetreatAttack.getAction(game ,0), LaunchExpedition(PlayerId.Blue, 0, 2, 1.0, 10))
        assertEquals(reinforceRetreatAttack.getAction(game ,1), InterruptibleWait(1, 10))
        assertEquals(reinforceRedeployRetreatAttack.getAction(game ,0), LaunchExpedition(PlayerId.Blue, 0, 2, 1.0, 10))
        assertEquals(reinforceRedeployRetreatAttack.getAction(game ,1), LaunchExpedition(PlayerId.Red, 3, 1, 0.5, 10))
    }

    @Test
    fun heuristicAgentIncludesAttackAsDefault() {
        val baseHeuristic = File(javaClass.classLoader.getResource("test/BaseHeuristic.txt").file)
        val fileAsLines = BufferedReader(FileReader(baseHeuristic)).lines().toList()
        val defaultParams = createAgentParamsFromString(fileAsLines)
        val searchSpace = HeuristicSearchSpace(defaultParams, "./resources/test/HeuristicSearch.txt")
        val heuristicAgent = searchSpace.getAgent(doubleArrayOf(1.0, 1.0, 1.0, 1.0)) as HeuristicAgent
        assertEquals(heuristicAgent.policy, listOf(HeuristicOptions.WITHDRAW, HeuristicOptions.REINFORCE, HeuristicOptions.ATTACK))
    }
}