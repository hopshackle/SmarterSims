package groundWar

import math.Vec2d
import kotlin.random.Random
import org.json.*

enum class PlayerId {
    Blue, Red, Neutral, Fog
}

fun playerIDToNumber(playerID: PlayerId): Int {
    return when (playerID) {
        PlayerId.Blue -> 0
        PlayerId.Red -> 1
        PlayerId.Neutral -> 99
        PlayerId.Fog -> -1
    }
}

fun numberToPlayerID(player: Int): PlayerId {
    return when (player) {
        0 -> PlayerId.Blue
        1 -> PlayerId.Red
        else -> throw java.lang.AssertionError("Only 0 and 1 supported")
    }
}


data class City(val location: Vec2d, val radius: Int = 25, var pop: Double = 0.0,
                var owner: PlayerId = PlayerId.Neutral, val name: String = "", val fort: Boolean = false)

data class Route(val fromCity: Int, val toCity: Int, val length: Double, val terrainDifficulty: Double)

fun routesCross(start: Vec2d, end: Vec2d, routesToCheck: List<Route>, cities: List<City>): Boolean {
    return routesToCheck.any { r -> routesCross(start, end, cities[r.fromCity].location, cities[r.toCity].location) }
}

fun routesCross(start: Vec2d, end: Vec2d, pathsToCheck: List<Pair<Vec2d, Vec2d>>): Boolean {
    val result = pathsToCheck.any { p -> routesCross(start, end, p.first, p.second) }
    //   println("$start to $end : $result")
    return result
}

fun allCitiesConnected(routes: List<Route>, cities: List<City>): Boolean {
    // check to see if we can get from every city to every other city
    // We start from any one city, and make sure we can reach the others

    var connectedCities = setOf(0)
    do {
        val networkSizeOnLastIteration = connectedCities.size
        connectedCities = routes.filter { r -> r.fromCity in connectedCities }
                .map(Route::toCity)
                .toSet() + connectedCities
    } while (networkSizeOnLastIteration != connectedCities.size)
    return connectedCities.size == cities.size
}

fun routesCross(start1: Vec2d, end1: Vec2d, start2: Vec2d, end2: Vec2d): Boolean {
    // If we consider the two lines in the form parameterised by t: start + t(end - start)
    // then t is between 0 and 1 on each line.
    // what this code does (hopefully) is calculate the two t parameters of for the two lines (as they will cross somewhere, unless parallel)
    // and check that both parameters are in the range that mean the lines cross between the locations

    //   println("Checking $start1 -> $end1 against $start2 -> $end2")
    // first check if lines are parallel
//    if ((end1.y - start1.y) / (end1.x - start1.x) == (end2.y - start2.y) / (end2.x - start2.x)) return false

    val t2 = ((start2.y - start1.y) * (end1.x - start1.x) - (start2.x - start1.x) * (end1.y - start1.y)) /
            ((end2.x - start2.x) * (end1.y - start1.y) - (end2.y - start2.y) * (end1.x - start1.x))
    val t1 = ((start2.x - start1.x) + t2 * (end2.x - start2.x)) / if (end1.x == start1.x) 0.001 else (end1.x - start1.x)
    //   println("$start1 -> $end1 with $start2 -> $end2 : $t1, $t2")
    return (t2 in 0.001..0.999 && t1 in 0.001..0.999) || (t1 == 0.0 && t2 in 0.001..0.999)
}

data class Transit(val nPeople: Double, val fromCity: Int, val toCity: Int, val playerId: PlayerId, val startTime: Int, val endTime: Int) {
    fun currentPosition(time: Int, cities: List<City>): Vec2d {
        val proportion: Double = (time - startTime).toDouble() / (endTime - startTime).toDouble()
        return cities[fromCity].location + (cities[toCity].location - cities[fromCity].location) * proportion
    }

    fun collisionEvent(otherTransit: Transit, world: World, currentTime: Int): Event {
        val currentEnemyPosition = otherTransit.currentPosition(currentTime, world.cities)
        val ourPosition = this.currentPosition(currentTime, world.cities)
        val distance = ourPosition.distanceTo(currentEnemyPosition) / 2.0
        val timeOfCollision = currentTime + (distance / world.params.speed[playerIDToNumber(playerId)]).toInt()
        return Event(timeOfCollision, Battle(this, otherTransit))
    }
}

data class World(var cities: List<City> = ArrayList(), var routes: List<Route> = ArrayList(),
                 val random: Random = Random(3),
                 val params: EventGameParams = EventGameParams()) {

    var currentTransits: ArrayList<Transit> = ArrayList()
        private set(newTransits) {
            field = newTransits
        }

    var allRoutesFromCity: Map<Int, List<Route>> = HashMap()

    init {
        if (cities.isEmpty()) initialise()
        allRoutesFromCity = routes.groupBy(Route::fromCity)
        if (cities.none { it.owner == PlayerId.Blue || it.owner == PlayerId.Red }) setPlayerBases()
    }

    private fun initialise() {

        // just keep it like so
        cities = ArrayList()
        with(params) {
            var n = 0
            for (i in 0 until nAttempts) {
                val isFort = random.nextDouble() < percentFort;
                val location = Vec2d(radius + random.nextDouble((width - 2.0 * radius)),
                        radius + random.nextDouble((height - 2.0 * radius)))
                val city = City(location, radius, 0.0, name = n.toString(), fort = isFort)
                if (canPlace(city, cities, citySeparation)) {
                    cities += city
                    n++
                }
            }
        }

        for (i in 0 until cities.size) {
            // for each city we connect to all cities within a specified range
            for (j in 0 until cities.size) {
                if (i != j && cities[i].location.distanceTo(cities[j].location) <= params.autoConnect
                        && !routesCross(cities[i].location, cities[j].location, routes, cities)) {
                    routes += Route(i, j, cities[i].location.distanceTo(cities[j].location), 1.0)
                }
            }
            var count = 0;
            while (count < 50 && routes.filter { r -> r.fromCity == i }.size < params.minConnections) {
                // then connect to random cities up to minimum
                linkRandomCityTo(i)
                count++
            }
        }


        var count = 0;
        while (!allCitiesConnected(routes, cities)) {
            linkRandomCityTo(random.nextInt(cities.size))
            count++
            if (count > 50) {
                throw AssertionError("WTF")
            }
        }
    }

    private fun setPlayerBases() {
        if (cities.size == 1) return
        var blueBase = 0
        var redBase = 0
        while (blueBase == redBase) {
            blueBase = random.nextInt(cities.size)
            redBase = random.nextInt(cities.size)
        }
        cities[blueBase].owner = PlayerId.Blue
        cities[blueBase].pop = params.startingForce[0].toDouble()
        cities[redBase].owner = PlayerId.Red
        cities[redBase].pop = params.startingForce[1].toDouble()
    }

    private fun linkRandomCityTo(cityIndex: Int): Boolean {
        val eligibleCities = cities.filter {
            val distance = cities[cityIndex].location.distanceTo(it.location)
            distance > params.autoConnect && distance <= params.maxDistance
        }.filter {
            !routes.any { r -> r.fromCity == cityIndex && r.toCity == cities.indexOf(it) }
        }.filter {
            !routesCross(cities[cityIndex].location, it.location, routes, cities)
        }
        if (eligibleCities.isEmpty())
            return false

        val proposal = eligibleCities[random.nextInt(eligibleCities.size)]
        val distance = cities[cityIndex].location.distanceTo(proposal.location)
        routes += Route(cityIndex, cities.indexOf(proposal), distance, 1.0)
        routes += Route(cities.indexOf(proposal), cityIndex, distance, 1.0)
        return true
    }

    fun canPlace(c: City, cities: List<City>, minSep: Int): Boolean {
        for (el in cities)
            if (c.location.distanceTo(el.location) < c.radius + el.radius + minSep) return false
        return true
    }

    fun deepCopyWithFog(perspective: PlayerId): World {
        val state = copy()
        state.cities = ArrayList(cities.withIndex().map { (i, c) ->
            if (checkVisible(i, perspective)) City(c.location, c.radius, c.pop, c.owner, c.name, c.fort)
            else City(c.location, c.radius, params.fogStrengthAssumption[playerIDToNumber(perspective)], PlayerId.Fog, c.name, c.fort)
        })
        state.currentTransits = ArrayList(currentTransits.filter { t -> checkVisible(t, perspective) }) // each Transit is immutable, but not the list of active ones
        state.routes = routes       // immutable, so safe
        state.allRoutesFromCity = allRoutesFromCity // immutable, so safe
        return state
    }

    fun deepCopy(): World {
        val state = copy()
        state.cities = ArrayList(cities.map { c -> City(c.location, c.radius, c.pop, c.owner, c.name, c.fort) })
        state.currentTransits = ArrayList(currentTransits.filter { true }) // each Transit is immutable, but not the list of active ones
        state.routes = routes       // immutable, so safe
        state.allRoutesFromCity = allRoutesFromCity // immutable, so safe
        return state
    }

    fun checkVisible(city: Int, perspective: PlayerId): Boolean {
        if (!params.fogOfWar) return true
        return (cities[city].owner == perspective) ||
                routes.any { r -> r.toCity == city && cities[r.fromCity].owner == perspective } ||
                currentTransits.any { t -> t.playerId == perspective && (t.toCity == city || t.fromCity == city) }
    }

    fun checkVisible(route: Route, perspective: PlayerId): Boolean {
        if (!params.fogOfWar) return true
        return (cities.withIndex().any { (i, c) -> c.owner == perspective && (route.toCity == i || route.fromCity == i) })
    }

    fun checkVisible(transit: Transit, perspective: PlayerId): Boolean {
        if (!params.fogOfWar) return true
        return transit.playerId == perspective ||
                cities[transit.toCity].owner == perspective ||
                cities[transit.fromCity].owner == perspective ||
                currentTransits.any { t ->
                    t.playerId == perspective && (
                            (t.toCity == transit.toCity && t.fromCity == transit.fromCity)
                                    || (t.toCity == transit.fromCity && t.fromCity == transit.toCity))
                }
    }


    fun addTransit(transit: Transit) {
        currentTransits.add(transit)
    }

    fun removeTransit(transit: Transit) {
        if (currentTransits.contains(transit))
            currentTransits.remove(transit)
        else
            throw AssertionError("Transit to be removed is not recognised")
    }

    fun nextCollidingTransit(newTransit: Transit, currentTime: Int): Transit? {
        if (currentTransits.any {
                    it.fromCity == newTransit.fromCity
                            && it.toCity == newTransit.toCity
                            && it.playerId == newTransit.playerId
                            && it !== newTransit
                }) return null
        // the check above looks for any pre-existing force by the same player on the arc. If one exists, then it will fight a battle first
        val collidingTransit = currentTransits.filter {
            it.fromCity == newTransit.toCity
                    && it.toCity == newTransit.fromCity
                    && it.playerId != newTransit.playerId
                    && it.endTime > currentTime
        }.minBy(Transit::endTime)
        // find the transit on the rout closest to us
        return collidingTransit
    }

    fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("height", params.height)
        json.put("width", params.width)
        json.put("cities", cities.map {
            JSONObject(mapOf(
                    "name" to it.name,
                    "x" to it.location.x,
                    "y" to it.location.y,
                    "fort" to it.fort
            ))
        })

        json.put("routes", routes.filter { it.fromCity < it.toCity }.map {
            JSONObject(mapOf(
                    "from" to cities[it.fromCity].name,
                    "to" to cities[it.toCity].name
            ))
        })
        return json
    }
}


fun createWorld(data: String, params: EventGameParams): World {
    return if (data.trim().startsWith("{"))
        createWorldFromJSON(data, params)
    else
        createWorldFromMap(data, params)
}

fun createWorldFromJSON(data: String, params: EventGameParams): World {

    val json = JSONObject(data)
    val height = json.getInt("height")
    val width = json.getInt("width")
    val cities = json.getJSONArray("cities").map {
        val c = it as JSONObject
        City(Vec2d(c.getDouble("x"), c.getDouble("y")), radius = params.radius, pop = 0.0, fort = c.getBoolean("fort"), name = c.getString("name"))
    }
    // name, x, y, fort are expected values
    val routes = json.getJSONArray("routes").flatMap {
        val r = it as JSONObject
        val from: Int = cities.indexOfFirst { it.name == r.getString("from") }
        val to: Int = cities.indexOfFirst { it.name == r.getString("to") }
        val length = cities[from].location.distanceTo(cities[to].location)
        listOf(Route(from, to, length, 1.0),
                Route(to, from, length, 1.0))
    }
    // from, to are expected values (referring to city names)

    return World(cities, routes, params = params.copy(height = height, width = width))
}

fun createWorldFromMap(data: String, params: EventGameParams): World {
    val lines = data.split("\n").toList()
    if (lines.any { it.length != lines[0].length })
        throw AssertionError("All lines in map file must have same length")

    var cityCount = 0
    val cities: List<City> = lines.withIndex().flatMap { (y, line) ->
        line.withIndex().filter { it.value in listOf('C', 'F') }.map { (x, char) ->
            cityCount++
            City(Vec2d(x * 50.0 + 25, y * 50.0 + 25), params.radius, 0.0, name = "City_$cityCount", fort = char == 'F')
        }.toList()
    }

    // for each mountain square, we create two paths to block routes - one between each diagonally opposite pair of corners
    val mountains: List<Pair<Vec2d, Vec2d>> = lines.withIndex().flatMap { (y, line) ->
        line.withIndex().filter { it.value == 'M' }.flatMap { (x, char) ->
            listOf(Pair(Vec2d(x * 50.0, y * 50.0), Vec2d(x * 50.0 + 50, y * 50.0 + 50)),
                    Pair(Vec2d(x * 50.0 + 50, y * 50.0), Vec2d(x * 50.0, y * 50.0 + 50)))
        }.toList()
    }

    val routes: List<Route> = cities.withIndex().fold(emptyList(), { acc1: List<Route>, city1 ->
        acc1 + cities.withIndex().fold(emptyList(), { acc2: List<Route>, city2 ->
            //      println("Processing cities ${city1.value.name} and ${city2.value.name}; ${acc1.size}:${acc2.size} routes")
            acc2 + if (city1.index < city2.index && !routesCross(city1.value.location, city2.value.location, acc1, cities)
                    && !routesCross(city1.value.location, city2.value.location, mountains)) {
                //       println("Route between cities $city1 and $city2; ${acc1.size} routes")
                val length = city1.value.location.distanceTo(city2.value.location)
                listOf(Route(city1.index, city2.index, length, 1.0),
                        Route(city2.index, city1.index, length, 1.0))
            } else {
                emptyList()
            }
        })
    })

    return World(cities, routes, params = params.copy(height = lines.size * 50, width = lines[0].length * 50))
}

