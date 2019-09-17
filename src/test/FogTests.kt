package test


import ggi.NoAction
import groundWar.*
import groundWar.fogOfWar.HistoricVisibility
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import kotlin.test.*

val cityCreationParams = EventGameParams(seed = 3, minConnections = 2, autoConnect = 300, maxDistance = 1000,
        fogOfWar = true, fogMemory = intArrayOf(100, 200), minAssaultFactor = doubleArrayOf(0.0, 0.0),
        fogStrengthAssumption = doubleArrayOf(1.0, 2.0))
var foggyWorld: World = World(params = cityCreationParams)
internal val noVisibility = HistoricVisibility(emptyMap())

class FogTests {

    @BeforeEach
    fun init() {
        foggyWorld = World(params = cityCreationParams)
    }

    /*
    pop=Force(size=0.0, fatigue=0.0, timeStamp=0), owner=Neutral, name=0, fort=false)
    pop=Force(size=0.0, fatigue=0.0, timeStamp=0), owner=Neutral, name=1, fort=false)
    pop=Force(size=0.0, fatigue=0.0, timeStamp=0), owner=Neutral, name=2, fort=false)
    pop=Force(size=100.0, fatigue=0.0, timeStamp=0), owner=Red, name=3, fort=true)
    pop=Force(size=100.0, fatigue=0.0, timeStamp=0), owner=Blue, name=4, fort=false)
    pop=Force(size=0.0, fatigue=0.0, timeStamp=0), owner=Neutral, name=5, fort=false)
    pop=Force(size=0.0, fatigue=0.0, timeStamp=0), owner=Neutral, name=6, fort=false)
    pop=Force(size=0.0, fatigue=0.0, timeStamp=0), owner=Neutral, name=7, fort=false)
    pop=Force(size=0.0, fatigue=0.0, timeStamp=0), owner=Neutral, name=8, fort=false)

    Route(fromCity=0, toCity=2, length=275.8455578081508, terrainDifficulty=1.0)
    Route(fromCity=0, toCity=3, length=249.31384485842722, terrainDifficulty=1.0)
    Route(fromCity=1, toCity=2, length=198.474286842283, terrainDifficulty=1.0)
    Route(fromCity=1, toCity=5, length=287.77471511074697, terrainDifficulty=1.0)
    Route(fromCity=1, toCity=4, length=466.1843577700114, terrainDifficulty=1.0)
    Route(fromCity=1, toCity=3, length=322.6316485937634, terrainDifficulty=1.0)
    Route(fromCity=2, toCity=0, length=275.8455578081508, terrainDifficulty=1.0)
    Route(fromCity=2, toCity=1, length=198.474286842283, terrainDifficulty=1.0)
    Route(fromCity=2, toCity=5, length=98.39470451810098, terrainDifficulty=1.0)
    Route(fromCity=2, toCity=6, length=278.0568436251884, terrainDifficulty=1.0)
    Route(fromCity=3, toCity=0, length=249.31384485842722, terrainDifficulty=1.0)
    Route(fromCity=3, toCity=1, length=322.6316485937634, terrainDifficulty=1.0)
    Route(fromCity=4, toCity=7, length=93.30010141668328, terrainDifficulty=1.0)
    Route(fromCity=4, toCity=8, length=146.03612945597877, terrainDifficulty=1.0)
    Route(fromCity=4, toCity=1, length=466.1843577700114, terrainDifficulty=1.0)
    Route(fromCity=5, toCity=1, length=287.77471511074697, terrainDifficulty=1.0)
    Route(fromCity=5, toCity=2, length=98.39470451810098, terrainDifficulty=1.0)
    Route(fromCity=5, toCity=6, length=196.70561630752658, terrainDifficulty=1.0)
    Route(fromCity=6, toCity=2, length=278.0568436251884, terrainDifficulty=1.0)
    Route(fromCity=6, toCity=5, length=196.70561630752658, terrainDifficulty=1.0)
    Route(fromCity=7, toCity=4, length=93.30010141668328, terrainDifficulty=1.0)
    Route(fromCity=7, toCity=8, length=138.4294339013805, terrainDifficulty=1.0)
    Route(fromCity=8, toCity=4, length=146.03612945597877, terrainDifficulty=1.0)
    Route(fromCity=8, toCity=7, length=138.4294339013805, terrainDifficulty=1.0)
*/
    @Test
    fun allCitiesAreVisible() {
        val blueCity: Int = foggyWorld.cities.withIndex().filter { (_, c) -> c.owner == PlayerId.Blue }.map { (i, _) -> i }.first()
        val neighbours = foggyWorld.allRoutesFromCity.getOrDefault(blueCity, emptyList())
                .map(Route::toCity)
                .toSet()
        val nonNeighbours = foggyWorld.cities.indices.toSet() - neighbours - blueCity
        assertFalse(nonNeighbours.isEmpty())
        assert(foggyWorld.checkVisible(blueCity, PlayerId.Blue))
        assert(nonNeighbours.none { i -> foggyWorld.checkVisible(i, PlayerId.Blue) })
        assert(neighbours.all { i -> foggyWorld.checkVisible(i, PlayerId.Blue) })
    }

    @Test
    fun transitVisibilityFromDestinationCity() {
        with(foggyWorld) {
            val blueCity = cities.withIndex().filter { (_, c) -> c.owner == PlayerId.Blue }.map { (i, _) -> i }.first()
            val redCity = cities.withIndex().filter { (_, c) -> c.owner == PlayerId.Red }.map { (i, _) -> i }.first()
            val blueToNeutral = routes.filter { r -> r.fromCity == blueCity && cities[r.toCity].owner == PlayerId.Neutral }.first()
            val redToNeutral = routes.filter { r -> r.fromCity == redCity && cities[r.toCity].owner == PlayerId.Neutral }.first()
            val blue_nb = Transit(Force(1.0), blueToNeutral.toCity, blueCity, PlayerId.Blue, 0, 1000)
            val red_nr = Transit(Force(1.0), redToNeutral.toCity, redCity, PlayerId.Red, 0, 1000)
            addTransit(blue_nb); addTransit(red_nr)

            assert(checkVisible(blue_nb, PlayerId.Blue, noVisibility))
            assert(!checkVisible(red_nr, PlayerId.Blue, noVisibility))
            assert(!checkVisible(blue_nb, PlayerId.Red, noVisibility))
            assert(checkVisible(red_nr, PlayerId.Red, noVisibility))

            val fogCopyRed = foggyWorld.deepCopyWithFog(PlayerId.Red)
            val fogCopyBlue = foggyWorld.deepCopyWithFog(PlayerId.Blue)
            assertEquals(fogCopyBlue.currentTransits.size, 1)
            assert(fogCopyBlue.currentTransits.contains(blue_nb))
            assertEquals(fogCopyRed.currentTransits.size, 1)
            assert(fogCopyRed.currentTransits.contains(red_nr))
        }
    }

    @Test
    fun transitVisibilityFromSourceCity() {
        with(foggyWorld) {
            val blueCity = cities.withIndex().filter { (_, c) -> c.owner == PlayerId.Blue }.map { (i, _) -> i }.first()
            val redCity = cities.withIndex().filter { (_, c) -> c.owner == PlayerId.Red }.map { (i, _) -> i }.first()
            val blueToNeutral = routes.filter { r -> r.fromCity == blueCity && cities[r.toCity].owner == PlayerId.Neutral }.first()
            val redToNeutral = routes.filter { r -> r.fromCity == redCity && cities[r.toCity].owner == PlayerId.Neutral }.first()
            val blue_bn = Transit(Force(1.0), blueCity, blueToNeutral.toCity, PlayerId.Blue, 0, 1000)
            val red_rn = Transit(Force(1.0), redCity, redToNeutral.toCity, PlayerId.Red, 0, 1000)
            addTransit(blue_bn); addTransit(red_rn);

            assert(checkVisible(blue_bn, PlayerId.Blue, noVisibility))
            assert(!checkVisible(red_rn, PlayerId.Blue, noVisibility))
            assert(!checkVisible(blue_bn, PlayerId.Red, noVisibility))
            assert(checkVisible(red_rn, PlayerId.Red, noVisibility))

            val fogCopyRed = foggyWorld.deepCopyWithFog(PlayerId.Red)
            val fogCopyBlue = foggyWorld.deepCopyWithFog(PlayerId.Blue)
            assertEquals(fogCopyBlue.currentTransits.size, 1)
            assert(fogCopyBlue.currentTransits.contains(blue_bn))
            assertEquals(fogCopyRed.currentTransits.size, 1)
            assert(fogCopyRed.currentTransits.contains(red_rn))
        }
    }

    @Test
    fun transitVisibilityToOtherPlayerAtDestination() {
        with(foggyWorld) {
            val blueCity = cities.withIndex().filter { (_, c) -> c.owner == PlayerId.Blue }.map { (i, _) -> i }.first()
            val blueToRed = routes.filter { r -> r.fromCity == blueCity && cities[r.toCity].owner == PlayerId.Neutral }.first()
            cities[blueToRed.toCity].owner = PlayerId.Red

            val blue_br = Transit(Force(1.0), blueCity, blueToRed.toCity, PlayerId.Blue, 0, 1000)
            addTransit(blue_br)

            assert(checkVisible(blue_br, PlayerId.Blue, noVisibility))
            assert(checkVisible(blue_br, PlayerId.Red, noVisibility))

            val fogCopyRed = foggyWorld.deepCopyWithFog(PlayerId.Red)
            val fogCopyBlue = foggyWorld.deepCopyWithFog(PlayerId.Blue)
            assertEquals(fogCopyBlue.currentTransits.size, 1)
            assertEquals(fogCopyRed.currentTransits.size, 1)
        }
    }

    @Test
    fun transitVisibilityOnSameRoute() {
        with(foggyWorld) {
            val blueCity = cities.withIndex().filter { (_, c) -> c.owner == PlayerId.Blue }.map { (i, _) -> i }.first()
            val blueToNeutral = routes.filter { r -> r.fromCity == blueCity && cities[r.toCity].owner == PlayerId.Neutral }.first()
            val blue_bn = Transit(Force(1.0), blueCity, blueToNeutral.toCity, PlayerId.Blue, 0, 1000)
            val red_bn = Transit(Force(1.0), blueCity, blueToNeutral.toCity, PlayerId.Red, 0, 1000)
            val blue_nb = Transit(Force(1.0), blueToNeutral.toCity, blueCity, PlayerId.Blue, 0, 1000)
            val red_nb = Transit(Force(1.0), blueToNeutral.toCity, blueCity, PlayerId.Red, 0, 1000)
            addTransit(blue_bn); addTransit(blue_nb); addTransit(red_bn); addTransit(red_nb)
            // these are all visible because Transits can see each other on the same route
            assert(checkVisible(blue_bn, PlayerId.Blue, noVisibility))
            assert(checkVisible(blue_nb, PlayerId.Blue, noVisibility))
            assert(checkVisible(red_bn, PlayerId.Blue, noVisibility))
            assert(checkVisible(red_nb, PlayerId.Blue, noVisibility))
            assert(checkVisible(blue_bn, PlayerId.Red, noVisibility))
            assert(checkVisible(blue_nb, PlayerId.Red, noVisibility))
            assert(checkVisible(red_bn, PlayerId.Red, noVisibility))
            assert(checkVisible(red_nb, PlayerId.Red, noVisibility))

            val fogCopyRed = foggyWorld.deepCopyWithFog(PlayerId.Red)
            val fogCopyBlue = foggyWorld.deepCopyWithFog(PlayerId.Blue)
            assertEquals(fogCopyBlue.currentTransits.size, 4)
            assertEquals(fogCopyRed.currentTransits.size, 4)
        }
    }

    @Test
    fun testFogWithNoForces() {
        foggyWorld.cities.forEach { c ->
            c.owner = PlayerId.Blue
        }
        val fogCopyRed = foggyWorld.deepCopyWithFog(PlayerId.Red)
        assertTrue(fogCopyRed.cities.all { it.owner == PlayerId.Fog && it.pop.size == cityCreationParams.fogStrengthAssumption[1] })
    }

    @Test
    fun testFogOnGameCopy() {
        foggyWorld.cities.forEach { c ->
            c.owner = PlayerId.Blue
        }
        val game = LandCombatGame(foggyWorld)
        val gameCopy = game.copy(1)
        assertTrue(gameCopy.world.cities.all {
            it.owner == PlayerId.Fog && it.pop.size == cityCreationParams.fogStrengthAssumption[1]
        })
        val gameCopy2 = gameCopy.copy()
        assertTrue(gameCopy2.world.cities.all {
            it.owner == PlayerId.Fog && it.pop.size == cityCreationParams.fogStrengthAssumption[1]
        })
    }

    @Test
    fun testFogOnCityCopy() {
        foggyWorld.cities[0].owner = PlayerId.Red
        val fullCopy = foggyWorld.deepCopy()
        val fogCopyRed = foggyWorld.deepCopyWithFog(PlayerId.Red)
        val fogCopyBlue = foggyWorld.deepCopyWithFog(PlayerId.Blue)
        val check = BooleanArray(4) { false }
        foggyWorld.cities.withIndex()
                .forEach { (i, c) ->
                    when (c.owner) {
                        PlayerId.Blue -> assert(fogCopyBlue.cities[i] == fullCopy.cities[i])
                        PlayerId.Red -> assert(fogCopyRed.cities[i] == fullCopy.cities[i])
                        else -> Unit
                    }
                    when (Pair(fullCopy.checkVisible(i, PlayerId.Red), fullCopy.checkVisible(i, PlayerId.Blue))) {
                        Pair(false, false) -> {
                            assertEquals(fogCopyBlue.cities[i].owner, PlayerId.Fog)
                            assertEquals(fogCopyRed.cities[i].owner, PlayerId.Fog)
                            check[0] = true
                        }
                        Pair(true, true) -> {
                            assertEquals(fogCopyBlue.cities[i].owner, c.owner)
                            assertEquals(fogCopyRed.cities[i].owner, c.owner)
                            check[1] = true
                        }
                        Pair(true, false) -> {
                            assertEquals(fogCopyBlue.cities[i].owner, PlayerId.Fog)
                            assertEquals(fogCopyRed.cities[i].owner, c.owner)
                            check[2] = true
                        }
                        Pair(false, true) -> {
                            assertEquals(fogCopyBlue.cities[i].owner, c.owner)
                            assertEquals(fogCopyRed.cities[i].owner, PlayerId.Fog)
                            check[3] = true
                        }
                    }
                }
        assert(check.all { it })
    }

    @Test
    fun rollForwardDoesNotIncludeInvisibleInvasion() {
        foggyWorld.cities[8].owner = PlayerId.Blue
        val game = LandCombatGame(foggyWorld)
        // We should now have cities 4 and 8 as Blue, and 3 as Red
        // If Red invades 1 (which is visible to 8), then Blue's projection will not see the result
        LaunchExpedition(PlayerId.Red, 3, 1, 1.0, 10).apply(game)
        val blueVersion = game.copy(0)
        blueVersion.next(50)
        assertEquals(blueVersion.world.cities[1].owner, PlayerId.Neutral)
        val redVersion = game.copy(1)
        redVersion.next(50)
        assertEquals(redVersion.world.cities[1].owner, PlayerId.Red)
        val masterVersion = game.copy().next(50)
        assertEquals(masterVersion.world.cities[1].owner, PlayerId.Red)
    }

    @Test
    fun copyWithPerspectiveClearsOutInvisibleFutureActionsInQueue() {
        // we want one event of each type
        // 4 is Blue, 3 is Red
        // 1 is visible to both 3 and 4
        foggyWorld.cities[8].owner = PlayerId.Blue
        foggyWorld.cities[8].pop = Force(5.0)
        val game = LandCombatGame(foggyWorld)
        //      game.eventQueue.add(Event(10, MakeDecision(1)))  // R
        //      game.eventQueue.add(Event(10, MakeDecision(0))) // B
        assertFalse(game.world.checkVisible(6, PlayerId.Blue))
        game.eventQueue.add(Event(10, CityInflux(PlayerId.Red, Force(10.0), 6))) // R
        game.eventQueue.add(Event(10, CityInflux(PlayerId.Red, Force(10.0), 1, 3))) // RB
        game.eventQueue.add(Event(10, CityInflux(PlayerId.Blue, Force(10.0), 1, 4))) // RB
        game.eventQueue.add(Event(10, CityInflux(PlayerId.Blue, Force(10.0), 2, 4))) // B
        game.eventQueue.add(Event(10, TransitStart(Transit(Force(10.0), 4, 2, PlayerId.Blue, 11, 20)))) // B
        game.world.addTransit(Transit(Force(4.0), 4, 2, PlayerId.Blue, 10, 20))
        game.eventQueue.add(Event(20, TransitEnd(PlayerId.Blue, 4, 2, 20))) // B
        game.world.addTransit(Transit(Force(4.0), 4, 1, PlayerId.Blue, 10, 20))
        game.eventQueue.add(Event(20, TransitEnd(PlayerId.Blue, 4, 1, 20))) // B
        game.world.addTransit(Transit(Force(2.0), 1, 3, PlayerId.Blue, 10, 20))
        game.eventQueue.add(Event(20, TransitEnd(PlayerId.Blue, 1, 3, 20))) // RB
        val blueForce = Transit(Force(5.0), 1, 3, PlayerId.Blue, 10, 20)
        val redForce = Transit(Force(2.0), 3, 1, PlayerId.Red, 10, 20)
        game.world.addTransit(blueForce); game.world.addTransit(redForce)
        game.eventQueue.add(Event(11, Battle(blueForce, redForce))) // RB
        game.eventQueue.add(Event(20, NoAction(1, 5)))  // R
        game.eventQueue.add(Event(20, NoAction(0, 5))) // B
        game.eventQueue.add(Event(13, LaunchExpedition(PlayerId.Red, 3, 1, 1.0, 10))) // R
        game.eventQueue.add(Event(13, LaunchExpedition(PlayerId.Blue, 8, 1, 1.0, 10))) // B

        val blueVersion = game.copy(0).next(5)
        val redVersion = game.copy(1).next(5)
        val masterVersion = game.copy().next(5)

        assertEquals(masterVersion.eventQueue.size, 13)

        //      assert(redVersion.eventQueue.contains(Event(5, MakeDecision(1))))
        assert(redVersion.eventQueue.contains(Event(10, CityInflux(PlayerId.Red, Force(10.0), 6))))
        assert(redVersion.eventQueue.contains(Event(10, CityInflux(PlayerId.Red, Force(10.0), 1, 3))))
        assert(redVersion.eventQueue.contains(Event(20, TransitEnd(PlayerId.Blue, 1, 3, 20))))
        assert(redVersion.eventQueue.contains(Event(11, Battle(blueForce, redForce)))) // RB
        assert(redVersion.eventQueue.contains(Event(20, NoAction(1, 5))))  // R
        assert(redVersion.eventQueue.contains(Event(13, LaunchExpedition(PlayerId.Red, 3, 1, 1.0, 10)))) // R
        assertEquals(redVersion.eventQueue.size, 6)

        //    assert(blueVersion.eventQueue.contains(Event(5, MakeDecision(0)))) // B
        assert(blueVersion.eventQueue.contains(Event(10, CityInflux(PlayerId.Red, Force(10.0), 1, 3)))) // RB
        assert(blueVersion.eventQueue.contains(Event(10, CityInflux(PlayerId.Blue, Force(10.0), 1, 4)))) // RB
        assert(blueVersion.eventQueue.contains(Event(10, CityInflux(PlayerId.Blue, Force(10.0), 2, 4)))) // B
        assert(blueVersion.eventQueue.contains(Event(10, TransitStart(Transit(Force(10.0), 4, 2, PlayerId.Blue, 11, 20))))) // B
        assert(blueVersion.eventQueue.contains(Event(20, TransitEnd(PlayerId.Blue, 4, 2, 20)))) // B
        assert(blueVersion.eventQueue.contains(Event(20, TransitEnd(PlayerId.Blue, 4, 1, 20)))) // RB
        assert(blueVersion.eventQueue.contains(Event(11, Battle(blueForce, redForce)))) // RB
        assert(blueVersion.eventQueue.contains(Event(20, NoAction(0, 5)))) // B
        assert(blueVersion.eventQueue.contains(Event(13, LaunchExpedition(PlayerId.Blue, 8, 1, 1.0, 10)))) // B
        assert(blueVersion.eventQueue.contains(Event(20, TransitEnd(PlayerId.Blue, 1, 3, 20))))
        assertEquals(blueVersion.eventQueue.size, 10)
    }
}

class HistoricVisibilityTests {

    @BeforeEach
    fun init() {
        foggyWorld = World(params = cityCreationParams)
    }

    @Test
    fun historicVisibilityUpdatesAfterCityConquered() {
        val game = LandCombatGame(foggyWorld)
        val gameCopy = game.copy()
        gameCopy.next(2)
        assertTrue(gameCopy.visibilityModels[PlayerId.Red]?.isEmpty() ?: false)
        assertTrue(gameCopy.visibilityModels[PlayerId.Blue]?.isEmpty() ?: false)

        CityInflux(PlayerId.Red, Force(5.0), 7, -1).apply(gameCopy)
        (0..8).forEach {
            assertEquals(gameCopy.visibilityModels[PlayerId.Red]?.lastVisible(it), -1)
            assertEquals(gameCopy.visibilityModels[PlayerId.Blue]?.lastVisible(it), -1)
        }

        gameCopy.next(3)
        CityInflux(PlayerId.Blue, Force(20.0), 7, -1).apply(gameCopy)
        val popAfterBattle = gameCopy.world.cities[7].pop.size
        (0..8).forEach {
            assertEquals(gameCopy.visibilityModels[PlayerId.Blue]?.lastVisible(it), -1)
            assertEquals(gameCopy.visibilityModels[PlayerId.Red]?.lastVisible(it), if (it in listOf(4, 7, 8)) 5 else -1)

            assertEquals(gameCopy.visibilityModels[PlayerId.Blue]?.lastKnownForce(it) ?: 0.00, 0.00, 0.001)
            assertEquals(gameCopy.visibilityModels[PlayerId.Red]?.lastKnownForce(it) ?: 0.00, when (it) {
                4 -> 100.0
                7 -> popAfterBattle
                else -> 0.00
            }, 0.001)
        }
    }

    @Test
    fun historicVisibilityUpdatesAfterBattleOnArc() {
        val game = LandCombatGame(foggyWorld)
        val gameCopy = game.copy()
        gameCopy.next(2)

        CityInflux(PlayerId.Red, Force(5.0), 7, -1).apply(gameCopy)
        CityInflux(PlayerId.Blue, Force(15.0), 8, -1).apply(gameCopy)

        gameCopy.planEvent(3, LaunchExpedition(PlayerId.Red, 7, 8, 1.0, 0))
        gameCopy.planEvent(3, LaunchExpedition(PlayerId.Blue, 4, 7, 0.1, 0))
        // 4 -> 7 will arrive before 7 -> 8 (13 ticks to get from 7 to 8; 9 to get from 4 to 7)
        gameCopy.planEvent(13, LaunchExpedition(PlayerId.Blue, 8, 7, 0.5, 0))
        // battle should take place on tick 14

        gameCopy.next(15)
        assertEquals(gameCopy.eventQueue.history.first { it.action is Battle }.tick, 14)
        assertEquals(gameCopy.world.cities[7].owner, PlayerId.Blue)
        assertEquals(gameCopy.world.cities[8].owner, PlayerId.Blue)

        assertEquals(gameCopy.visibilityModels[PlayerId.Red]?.lastVisible(7), 14)
        assertEquals(gameCopy.visibilityModels[PlayerId.Red]?.lastVisible(8), 14)
        assertEquals(gameCopy.visibilityModels[PlayerId.Red]?.lastKnownForce(7) ?: 0.00, 10.0, 0.001)
        assertEquals(gameCopy.visibilityModels[PlayerId.Red]?.lastKnownForce(8) ?: 0.00, 7.5, 0.001)
    }

    @Test
    fun historicVisibilityMaintainedCorrectlyAfterStateCopy() {
        val game = LandCombatGame(foggyWorld)
        val gameCopy = game.copy()
        gameCopy.next(2)

        gameCopy.updateVisibility(6, PlayerId.Red)
        gameCopy.updateVisibility(3, PlayerId.Blue)

        val gameCopyCopy = gameCopy.copy()
        assertEquals(gameCopyCopy.visibilityModels[PlayerId.Blue], HistoricVisibility(mapOf(3 to Pair(2, 100.0))))
        assertEquals(gameCopyCopy.visibilityModels[PlayerId.Red], HistoricVisibility(mapOf(6 to Pair(2, 0.0))))
        val gameCopyBlue = gameCopy.copy(0)
        assertEquals(gameCopyBlue.visibilityModels[PlayerId.Blue], HistoricVisibility(mapOf(3 to Pair(2, 100.0))))
        assertTrue(gameCopyBlue.visibilityModels[PlayerId.Red]?.isEmpty() ?: false)
        val gameCopyRed = gameCopy.copy(1)
        assertTrue(gameCopyRed.visibilityModels[PlayerId.Blue]?.isEmpty() ?: false)
        assertEquals(gameCopyRed.visibilityModels[PlayerId.Red], HistoricVisibility(mapOf(6 to Pair(2, 0.0))))

        // TODO: Blanking out opponents visibility is not entirely accurate, as we will often know when they last saw us!
        // TODO: So depending on how far we nest our opponent model we may wish to keep this
    }

    @Test
    fun historicVisibilityCleanedUpDuringSanityChecks() {
        val game = LandCombatGame(foggyWorld)
        val gameCopy = game.copy()
        gameCopy.next(2)

        gameCopy.updateVisibility(1, PlayerId.Red)
        gameCopy.updateVisibility(6, PlayerId.Red)
        gameCopy.updateVisibility(3, PlayerId.Red)
        gameCopy.updateVisibility(1, PlayerId.Blue)
        gameCopy.updateVisibility(6, PlayerId.Blue)
        gameCopy.updateVisibility(3, PlayerId.Blue)

        gameCopy.next(1)
        assertEquals(gameCopy.visibilityModels[PlayerId.Blue], HistoricVisibility(mapOf(1 to Pair(2, 0.0), 6 to Pair(2, 0.0), 3 to Pair(2, 100.0))))
        assertEquals(gameCopy.visibilityModels[PlayerId.Red], HistoricVisibility(mapOf(1 to Pair(2, 0.0), 6 to Pair(2, 0.0))))
    }

    @Test
    fun cityPopulationRemainsVisible() {
        val game = LandCombatGame(foggyWorld)
        val gameCopy = game.copy()
        gameCopy.updateVisibility(6, PlayerId.Red)
        gameCopy.updateVisibility(4, PlayerId.Red)

        val blueCopy = gameCopy.copy(0)
        val redCopy = gameCopy.copy(1)

        assertTrue(cityCreationParams.fogStrengthAssumption.all { it > 0.00 })
        assertEquals(blueCopy.world.cities[6].pop.size, cityCreationParams.fogStrengthAssumption[0])
        assertEquals(blueCopy.world.cities[3].pop.size, cityCreationParams.fogStrengthAssumption[0])
        assertEquals(blueCopy.world.cities[1].pop.size, 0.00)
        assertEquals(redCopy.world.cities[6].pop.size, 0.00)
        assertEquals(redCopy.world.cities[3].pop.size, 100.0)
        assertEquals(redCopy.world.cities[4].pop.size, 100.0)
        assertEquals(redCopy.world.cities[1].pop.size, 0.00)
    }

    @Test
    fun transitRemainsVisibleAfterCopyIfSeen() {
        val game = LandCombatGame(foggyWorld)
        val gameCopy = game.copy()
        // now add transits on 4->1, 4-> 7, 4-> 8
        gameCopy.planEvent(0, LaunchExpedition(PlayerId.Blue, 4, 1, 0.1, 0))
        gameCopy.planEvent(0, LaunchExpedition(PlayerId.Blue, 4, 7, 0.1, 0))
        gameCopy.planEvent(0, LaunchExpedition(PlayerId.Blue, 4, 8, 0.1, 0))
        gameCopy.next(2)

        gameCopy.updateVisibility(6, PlayerId.Red)
        gameCopy.updateVisibility(4, PlayerId.Red)
        gameCopy.updateVisibility(1, PlayerId.Red)
        gameCopy.updateVisibility(7, PlayerId.Red)

        gameCopy.next(1)
        val redCopy = gameCopy.copy(1)
        val blueCopy = gameCopy.copy(0)

        assertEquals(blueCopy.world.currentTransits.size, 3)
        assertEquals(redCopy.world.currentTransits.size, 2) // can't see the 4->8 one
        assertEquals(redCopy.world.currentTransits.filter { it.toCity == 1 && it.playerId == PlayerId.Blue && it.force.size == 10.0 }.size, 1)
        assertEquals(redCopy.world.currentTransits.filter { it.toCity == 7 && it.playerId == PlayerId.Blue && it.force.size == 10.0 }.size, 1)

        assertEquals(blueCopy.eventQueue.filter { it.action is TransitEnd }.size, 3)
        assertEquals(redCopy.eventQueue.filter { it.action is TransitEnd }.size, 2)
    }

    @Test
    fun transitsNotVisibleIfLaunchedAfterVisibilityApplies() {
        val game = LandCombatGame(foggyWorld)
        val gameCopy = game.copy()
        // now add transits on 4->1, 4-> 7, 4-> 8
        gameCopy.planEvent(0, LaunchExpedition(PlayerId.Blue, 4, 1, 0.1, 0))
        gameCopy.planEvent(5, LaunchExpedition(PlayerId.Blue, 4, 7, 0.1, 0))
        gameCopy.planEvent(10, LaunchExpedition(PlayerId.Blue, 4, 8, 0.1, 0))

        gameCopy.next(2)
        gameCopy.updateVisibility(6, PlayerId.Red)
        gameCopy.updateVisibility(4, PlayerId.Red)
        gameCopy.updateVisibility(1, PlayerId.Red)
        gameCopy.updateVisibility(7, PlayerId.Red)

        gameCopy.next(10)
        val redCopy = gameCopy.copy(1)
        val blueCopy = gameCopy.copy(0)

        assertEquals(blueCopy.world.currentTransits.size, 3)
        assertEquals(redCopy.world.currentTransits.size, 1) // can only see the 4 -> 1 one
        assertEquals(redCopy.world.currentTransits.filter { it.toCity == 1 && it.playerId == PlayerId.Blue && it.force.size == 10.0 }.size, 1)

        assertEquals(blueCopy.eventQueue.filter { it.action is TransitEnd }.size, 3)
        assertEquals(redCopy.eventQueue.filter { it.action is TransitEnd }.size, 1)
    }

    @Test
    fun battlesAreVisibleIfBothAffectedTransitsAreVisible() {
        // mind you - with two players Battles will always be visible!
        val game = LandCombatGame(foggyWorld)
        val gameCopy = game.copy()
        // now add transits on 4->1, 4-> 7, 4-> 8
        gameCopy.planEvent(0, LaunchExpedition(PlayerId.Blue, 4, 1, 0.2, 0)) // takes 46 ticks to arrive
        gameCopy.planEvent(50, LaunchExpedition(PlayerId.Blue, 1, 3, 0.5, 0))
        gameCopy.planEvent(50, LaunchExpedition(PlayerId.Red, 3, 1, 0.5, 0)) // will annihilate the Blue player
        gameCopy.planEvent(55, CityInflux(PlayerId.Red, Force (20.0), 1, -1))
        gameCopy.next(57)
        assertEquals(gameCopy.eventQueue.filter { it.action is Battle }.size, 1 )
        val redCopy = gameCopy.copy(1)
        val blueCopy = gameCopy.copy(0)
        assertEquals(redCopy.eventQueue.filter { it.action is Battle }.size, 1 )
        assertEquals(blueCopy.eventQueue.filter { it.action is Battle }.size, 1 )
    }

    @Test
    fun semiIntegrationTestOfHeuristicVisibility() {
        val game = LandCombatGame(foggyWorld)
        val gameCopy = game.copy()

        gameCopy.planEvent(0, LaunchExpedition(PlayerId.Red, 3, 1, 0.2, 0)) // takes 31 ticks to arrive
        gameCopy.planEvent(32, LaunchExpedition(PlayerId.Red, 1, 2, 0.2, 0)) // takes 19 ticks
        gameCopy.planEvent(32, LaunchExpedition(PlayerId.Red, 3, 0, 0.2, 0)) // takes 24 ticks
        gameCopy.planEvent(0, LaunchExpedition(PlayerId.Blue, 4, 1, 0.9, 0)) // takes 46
        gameCopy.planEvent(47, LaunchExpedition(PlayerId.Blue, 1, 2, 0.5, 0)) // takes 19
        gameCopy.planEvent(57, LaunchExpedition(PlayerId.Blue, 1, 2, 0.5, 0))
        gameCopy.planEvent(67, LaunchExpedition(PlayerId.Blue, 1, 2, 0.5, 0))
        gameCopy.planEvent(50, LaunchExpedition(PlayerId.Blue, 4, 7, 0.5, 0)) // takes 9

        gameCopy.next(70)
        assertEquals(gameCopy.world.currentTransits.size, 2)
        assertEquals(gameCopy.eventQueue.filter { it.action is TransitEnd }.size, 2)
        assertEquals(gameCopy.world.cities[0].owner, PlayerId.Red)
        assertEquals(gameCopy.world.cities[0].pop.size, 16.0, 0.001)
        assertEquals(gameCopy.world.cities[4].owner, PlayerId.Blue)
        assertEquals(gameCopy.world.cities[4].pop.size, 5.0, 0.001)

        val redCopy = gameCopy.copy(1)
        val blueCopy = gameCopy.copy(0)
        assertEquals(redCopy.world.currentTransits.size, 1)
        assertEquals(redCopy.eventQueue.filter { it.action is TransitEnd }.size, 1)
        assertEquals(redCopy.world.cities[0].owner, PlayerId.Red)
        assertEquals(redCopy.world.cities[0].pop.size, 16.0, 0.001)
        assertEquals(redCopy.world.cities[4].owner, PlayerId.Blue)
        assertEquals(redCopy.world.cities[4].pop.size, 10.0, 0.001)

        assertEquals(blueCopy.world.currentTransits.size, 2)
        assertEquals(blueCopy.eventQueue.filter { it.action is TransitEnd }.size, 2)
        assertEquals(blueCopy.world.cities[0].owner, PlayerId.Red)
        assertEquals(blueCopy.world.cities[0].pop.size, 16.0, 0.001)
        assertEquals(blueCopy.world.cities[4].owner, PlayerId.Blue)
        assertEquals(blueCopy.world.cities[4].pop.size, 5.0, 0.001)
    }

    @Test
    fun visibilityInformationIsForgottenAfterThreshold() {
        val game = LandCombatGame(foggyWorld)
        val gameCopy = game.copy()

        gameCopy.next(2)
        gameCopy.updateVisibility(6, PlayerId.Red)
        gameCopy.updateVisibility(4, PlayerId.Red)
        gameCopy.updateVisibility(1, PlayerId.Red)
        gameCopy.updateVisibility(7, PlayerId.Red)
        gameCopy.updateVisibility(6, PlayerId.Blue)
        gameCopy.updateVisibility(4, PlayerId.Blue)
        gameCopy.updateVisibility(1, PlayerId.Blue)
        gameCopy.updateVisibility(7, PlayerId.Blue)

        gameCopy.next(101)
        val redCopy = gameCopy.copy(1)
        val blueCopy = gameCopy.copy(0)
        assertEquals(redCopy.world.cities[1].pop.size, 0.00, 0.001)
        assertEquals(redCopy.world.cities[4].pop.size, 100.00, 0.001)
        assertEquals(redCopy.world.cities[6].pop.size, 0.0, 0.001)
        assertEquals(redCopy.world.cities[7].pop.size, 0.0, 0.001)
        assertEquals(blueCopy.world.cities[1].pop.size, 0.0, 0.001)
        assertEquals(blueCopy.world.cities[4].pop.size, 100.0, 0.001)
        assertEquals(blueCopy.world.cities[6].pop.size, cityCreationParams.fogStrengthAssumption[0], 0.001)
        assertEquals(blueCopy.world.cities[7].pop.size, 0.00, 0.001)

        gameCopy.next(100)
        val redCopy2 = gameCopy.copy(1)
        val blueCopy2 = gameCopy.copy(0)
        assertEquals(redCopy2.world.cities[1].pop.size, 0.00, 0.001)
        assertEquals(redCopy2.world.cities[4].pop.size, cityCreationParams.fogStrengthAssumption[1], 0.001)
        assertEquals(redCopy2.world.cities[6].pop.size, cityCreationParams.fogStrengthAssumption[1], 0.001)
        assertEquals(redCopy2.world.cities[7].pop.size, cityCreationParams.fogStrengthAssumption[1], 0.001)
        assertEquals(blueCopy2.world.cities[1].pop.size, 0.0, 0.001)
        assertEquals(blueCopy2.world.cities[4].pop.size, 100.0, 0.001)
        assertEquals(blueCopy2.world.cities[6].pop.size, cityCreationParams.fogStrengthAssumption[0], 0.001)
        assertEquals(blueCopy2.world.cities[7].pop.size, 0.00, 0.001)
    }

}