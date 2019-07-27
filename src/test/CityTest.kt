package test

import groundWar.*
import groundWar.EventGameParams
import math.Vec2d
import org.junit.jupiter.api.*
import kotlin.random.Random
import kotlin.test.*

class CityLocationTest {

    @Test
    fun routeCrossingDetectionTest() {
        assertTrue(routesCross(Vec2d(0.0, 0.0), Vec2d(1.0, 1.0), Vec2d(0.4, 1.0), Vec2d(0.6, 0.0)))
        assertFalse(routesCross(Vec2d(0.0, 0.0), Vec2d(0.6, 0.0), Vec2d(0.4, 1.0), Vec2d(0.6, 0.0)))
        assertTrue(routesCross(Vec2d(0.0, 0.0), Vec2d(0.6, 0.0), Vec2d(0.4, 1.0), Vec2d(0.4, -1.0)))
        assertFalse(routesCross(Vec2d(1.0, 1.0), Vec2d(0.5, 0.0), Vec2d(0.6, 1.0), Vec2d(0.2, -1.0)))
        assertTrue(routesCross(Vec2d(0.0, 0.0), Vec2d(1.0, 1.0), Vec2d(0.5, 1.0), Vec2d(0.5, 0.0)))
        assertFalse(routesCross(Vec2d(0.0, 0.0), Vec2d(1.0, 1.0), Vec2d(0.1, 0.1), Vec2d(1.1, 1.1)))

        assertFalse(routesCross(Vec2d(772.8, 413.6), Vec2d(833.9, 123.9), Vec2d(836.5, 530.6), Vec2d(772.8, 413.6)))
        assertFalse(routesCross(Vec2d(833.9, 123.9), Vec2d(772.8, 413.6), Vec2d(836.5, 530.6), Vec2d(772.8, 413.6)))
        assertFalse(routesCross(Vec2d(222.1, 346.5), Vec2d(116.2, 204.8), Vec2d(175.8, 488.6), Vec2d(116.2, 204.8)))
    }

    @Test
    fun fullyConnectedNetworkTest() {
        for (i in 1..25) {
            val world = World(params = EventGameParams(seed = i.toLong()))
            assertTrue(allCitiesConnected(world.routes, world.cities))
        }
    }
}

class CityCreationTest {

    val cityCreationParams = EventGameParams(seed = 3, minConnections = 2, autoConnect = 300, maxDistance = 1000)
    val cityCreationWorld = World(params = cityCreationParams)

    @Test
    fun allCitiesHaveTwoMinimumConnections() {
        for ((i, _) in cityCreationWorld.cities.withIndex()) {
            assert(cityCreationWorld.allRoutesFromCity[i]?.size ?: 0 >= 2)
        }
    }

    @Test
    fun allCitiesHaveThreeMinimumConnections() {
        val initialThreePlus = cityCreationWorld.cities.withIndex().count { (i, _) ->
            cityCreationWorld.allRoutesFromCity[i]?.size ?: 0 >= 3
        }

        val localCityCreationWorld = World(params = cityCreationParams.copy(minConnections = 3))
        val citiesWithAtLeastThree = localCityCreationWorld.cities.withIndex().count { (i, _) ->
            localCityCreationWorld.allRoutesFromCity[i]?.size ?: 0 >= 3
        }

        assert(citiesWithAtLeastThree > initialThreePlus)
    }

    @Test
    fun allCitiesHaveConnectionsToNeighbours() {
        with(cityCreationWorld) {
            val allCityPairs = cities.flatMap { c1 -> cities.map { c2 -> c1 to c2 } }
            val allRoutes = routes.map { r -> cities[r.fromCity] to cities[r.toCity] }
            for ((c1, c2) in allCityPairs) {
                if (c1 != c2 && c1.location.distanceTo(c2.location) <= cityCreationParams.minConnections) {
                    assert((c1 to c2) in allRoutes)
                    assert((c2 to c1) in allRoutes)
                }
            }
        }
    }

    @Test
    fun noDuplicateRoutes() {
        with(cityCreationWorld) {
            assertEquals(routes.size, routes.distinct().size)
        }
    }

    @Test
    fun cityRadiiAvoided() {
        val localWorld = World(params = cityCreationParams.copy(seed = 10, nAttempts = 25, minConnections = 3))
        val cityRadii: List<Triple<Int, Vec2d, Vec2d>> = localWorld.cities.withIndex().flatMap { (i, c2) ->
            listOf(Triple(i, c2.location + Vec2d(c2.radius.toDouble(), c2.radius.toDouble()), c2.location - Vec2d(c2.radius.toDouble(), c2.radius.toDouble())),
                    Triple(i, c2.location + Vec2d(c2.radius.toDouble(), -c2.radius.toDouble()), c2.location - Vec2d(c2.radius.toDouble(), -c2.radius.toDouble())))
        }
        assertTrue(localWorld.cities.withIndex().all { (i, c) ->
            val destinationCities = localWorld.allRoutesFromCity[i]?.map(Route::toCity)?.toList() ?: emptyList()
            destinationCities.none { dest ->
                routesCross(c.location, localWorld.cities[dest].location,
                        cityRadii.filterNot { it.first in listOf(i, dest) }.map { Pair(it.second, it.third) })
            }
        })
    }

    @Test
    fun geneLocationCalculation() {
        val game1 = LandCombatGame(cityCreationWorld)
        assertEquals(game1.cityGenes, 1)
        assertEquals(game1.routeGenes, 1)
        assertEquals(game1.nActions(), 10)
        assertEquals(game1.codonsPerAction(), 4)

        val game2 = LandCombatGame(World(params = cityCreationParams.copy(nAttempts = 100)))
        assertEquals(game2.cityGenes, 2)
        assertEquals(game2.routeGenes, 2)
        assertEquals(game2.nActions(), 10)
        assertEquals(game2.codonsPerAction(), 6)

        val game3 = LandCombatGame(World(params = cityCreationParams.copy(nAttempts = 50)))
        assertEquals(game3.cityGenes, 2)
        assertEquals(game3.routeGenes, 1)
        assertEquals(game3.nActions(), 10)
        assertEquals(game3.codonsPerAction(), 5)
    }

}

class CityCopyTest {

    val cityCreationParams = EventGameParams(seed = 6, minConnections = 2, autoConnect = 300, maxDistance = 1000)
    val world = World(params = cityCreationParams)

    @Test
    fun fortStatusIsCopied() {
        val city1 = City(Vec2d(10.0, 10.0), fort = true)
        val city2 = city1.copy()
        assert(city2.fort)

        val world = World(listOf(city1))
        assert(world.deepCopy().cities[0].fort)
    }

    @Test
    fun allCitiesAreVisible() {
        val blueCity: Int = world.cities.withIndex().filter { (_, c) -> c.owner == PlayerId.Blue }.map { (i, _) -> i }.first()
        val neighbours = world.allRoutesFromCity.getOrDefault(blueCity, emptyList())
                .map(Route::toCity)
                .toSet()
        val nonNeighbours = (0 until world.cities.size).toSet() - neighbours - blueCity
        assertFalse(nonNeighbours.isEmpty())
        assert(world.checkVisible(blueCity, PlayerId.Blue))
        assert(nonNeighbours.all { i -> world.checkVisible(i, PlayerId.Blue) })
        assert(neighbours.all { i -> world.checkVisible(i, PlayerId.Blue) })
    }
}

class WorldCreationTests {

    @Test
    fun createWorldFromMap() {
        val mapString = """
            ------
            -C----
            -M---C
            -C--F-
        """.trimIndent()
        val world = createWorld(mapString, EventGameParams())
        world.cities.forEach { it.pop = 0.0; it.owner = PlayerId.Neutral }
        assertEquals(world.cities.size, 4)
        assertEquals(world.cities[0], City(Vec2d(75.0, 75.0), name = "City_1"))
        assertEquals(world.cities[1], City(Vec2d(275.0, 125.0), name = "City_2"))
        assertEquals(world.cities[2], City(Vec2d(75.0, 175.0), name = "City_3"))
        assertEquals(world.cities[3], City(Vec2d(225.0, 175.0), name = "City_4", fort = true))

        assertEquals(world.routes.size, 8)
        assertEquals(world.allRoutesFromCity[0]?.size, 2)
        assertEquals(world.allRoutesFromCity[1]?.size, 2)
        assertEquals(world.allRoutesFromCity[2]?.size, 1)
        assertEquals(world.allRoutesFromCity[3]?.size, 3)
    }

    @Test
    fun createWorldFromJSON() {
        val jsonString = """{
            "height" : 500,
            "width" : 700,
            "cities" : [
            {   "name" : "Waterdeep",
                "x" : 100,
                "y" : 150,
                "fort" : false
            }, 
            {   "name" : "Tulan",
                "x" : 200,
                "y" : 350,
                "fort" : false
            }, 
            {   "name" : "Greyhawk",
                "x" : 400,
                "y" : 150,
                "fort" : false
            }],
            "routes" : [
            {
                "from" : "Waterdeep",
                "to" : "Greyhawk"
            },
            {
                "from" : "Greyhawk",
                "to" : "Tulan"
            }]
            }"""

        val world = createWorld(jsonString, EventGameParams())
        assertEquals(world.cities.size, 3)
        assertEquals(world.routes.size, 4)
        assertEquals(world.params.height, 500)
        assertEquals(world.params.width, 700)
        assertEquals(world.cities[0].name, "Waterdeep")
        assertEquals(world.cities[1].name, "Tulan")
        assertEquals(world.cities[2].name, "Greyhawk")
        assertEquals(world.cities[1].location, Vec2d(200.0, 350.0))
        assertEquals(world.routes[0], Route(0, 2, 300.0, 1.0))
        assertEquals(world.routes[1], Route(2, 0, 300.0, 1.0))
        assertEquals(world.routes[2], Route(2, 1, Math.sqrt(2.0 * 200.0 * 200.0), 1.0))
        assertEquals(world.routes[3], Route(1, 2, Math.sqrt(2.0 * 200.0 * 200.0), 1.0))
    }
}
