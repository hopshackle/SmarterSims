package games.gridgame

import agents.DoNothingAgent
import agents.RandomAgent
import agents.SimpleEvoAgent
import ggi.AbstractGameState
import ggi.ExtendedAbstractGameState
import ggi.SimplePlayerInterface
import utilities.JEasyFrame
import views.GridView
import java.util.*

// started at 20:44


fun main(args: Array<String>) {
    val game = GridGame(30, 30).setFast(false)
    // game.setFast(true)
    println(game.grid)
    val gv = GridView(game)
    val frame = JEasyFrame(gv, "Life Game")
    val actions = intArrayOf(0, 0)
    var agent: SimplePlayerInterface = SimpleEvoAgent(useMutationTransducer = false, sequenceLength = 5, nEvals = 40)
    agent = RandomAgent()
    agent = DoNothingAgent(game.doNothingAction())

    while (true) {
        actions[0] = agent.getAction(game.copy(), Constants.player1)
        game.next(actions, 0)
        gv.repaint()
        Thread.sleep(100)
        frame.title = "tick = ${game.nTicks}, score = ${game.score()}"
        // System.exit(0)
    }
}


val random = Random()

object Constants {
    val player1 = 0
    val player2 = 1
    val playerValues = intArrayOf(player1, player2)
    val on: Int = 1
    val off: Int = 0
    val outside: Int = 0
    // val range = 0..1
}

data class Grid(val w: Int = 20, val h: Int = 20, val wrap: Boolean = true) {
    var grid: IntArray = randomGrid()

    fun randomGrid() = IntArray(w * h, { random.nextInt(2) })

    fun getCell(i: Int): Int = grid[i]

    fun setCell(i: Int, v: Int) {
        grid[i] = v
    }

    fun invertCell(i: Int) {
        grid[i] = 1 - grid[i]
    }

    fun getCell(x: Int, y: Int): Int {
//        if (!wrap)
//            if (x < 0 || y < 0 || x >= w || y >= h) return Constants.outside
        val xx = (x + w) % w
        val yy = (y + h) % h
        return grid[xx + w * yy]
    }

    fun setCell(x: Int, y: Int, value: Int) {
        if (x < 0 || y < 0 || x >= w || y >= h) return
        grid[x + w * y] = value
    }

    init {

        // println(grid)


    }

    fun deepCopy(): Grid {
        val gc = this.copy()
        gc.grid = grid.copyOf()
        return gc
    }

}

var totalTicks: Long = 0

class GridGame : ExtendedAbstractGameState {
    override fun randomInitialState(): AbstractGameState {
        grid.grid = grid.randomGrid()
        return this
    }

    val updateRule = MyRule()
    var grid: Grid = Grid()
    var nTicks = 0
    var fastUpdate: FastUpdate? = null

    constructor(w: Int = 20, h: Int = 20) {
        grid = Grid(w, h)
    }

    fun doNothingAction() = grid.grid.size

    fun setFast(fast: Boolean): GridGame {
        if (fast) {
            fastUpdate = FastUpdate(grid)
        } else {
            fastUpdate = null
        }
        return this
    }

    override fun copy(): AbstractGameState {
        val gridGame = GridGame()
        gridGame.nTicks = nTicks
        gridGame.grid = grid.deepCopy()
        gridGame.fastUpdate = fastUpdate
        return gridGame
    }

    override fun next(actions: IntArray, playerId: Int): AbstractGameState {

//        val p1Action = actions[0]
//        val p2Action = actions[1]
//        // if both players choose the same action then do nothing
//
//        // otherwise invert at each position - or if inverting then no need to ignore
//        grid.invertCell(p1Action)
//        grid.invertCell(p2Action)


        val gridCopy = grid.copy()

        if (fastUpdate != null) {
            with(grid) {
                // computeIndex()
                for (i in 0 until grid.size) {
                    var sum = 0
                    for (ix in fastUpdate!!.index[i]) {
                        sum += grid.get(ix)
                    }
                    // println("$i : $sum")
                    gridCopy.setCell(i, sumFun(sum))
                }
            }
        } else {

            for (i in 0 until grid.w) {
                for (j in 0 until grid.h) {
                    gridCopy.setCell(i, j, updateRule.cellUpdate(grid, i, j))
                }
            }
        }
        grid = gridCopy
        if (actions[playerId] != doNothingAction())
            grid.invertCell(actions[playerId])

        totalTicks++
        nTicks++
        return this
    }

    fun sumFun(sum: Int): Int {
        return if (sum < 3 || sum > 4) 0 else 1
    }

    override fun nActions(): Int {
        return grid.grid.size + 1
    }

    override fun score(): Double {
        return grid.grid.sum().toDouble()
    }

    override fun isTerminal(): Boolean {
        // for now let this never end!
        return false
    }

    override fun nTicks(): Int {
        return nTicks
    }

    override fun totalTicks(): Long {
        return totalTicks
    }

    override fun resetTotalTicks() {
        totalTicks = 0;
    }

}


interface UpdateRule {
    fun cellUpdate(grid: Grid, x: Int, y: Int): Int
}

class MyRule : UpdateRule {

    fun sumFun(centre: Int, sum: Int): Int {
        if (centre == 1)
            return if (sum < 3 || sum > 4) 0 else 1
        else
            return if (sum == 3) 1 else 0
    }

    override fun cellUpdate(grid: Grid, x: Int, y: Int): Int {
        var sum = 0
        for (xx in x - 1..x + 1) {
            for (yy in y - 1..y + 1) {
                if (!(xx == y && yy == x))
                    sum += grid.getCell(xx, yy)
            }
        }
        return sumFun(grid.getCell(x, y), sum)
    }
}


// fast updateing only increases speed by about 20% therefore not worth the effort

class FastUpdate {
    val index: ArrayList<IntArray> = ArrayList()
    val nNeighbours = 9

    constructor (grid: Grid) {
        for (i in 0 until grid.grid.size) {
            // println(Arrays.toString(index[i]))
            index.add(computeIndex(grid, i))
        }
    }

    fun computeIndex(grid: Grid, i: Int): IntArray {
        // for now compute them with wrap
        val lut = IntArray(nNeighbours)
        var x = i % grid.w
        var y = i / grid.w
        // now wrap

        var j = 0
        for (xx in x - 1..x + 1) {
            for (yy in y - 1..y + 1) {
                // wrap the grid position
                val ix = (xx + grid.w) % grid.w
                val iy = (yy + grid.h) % grid.h
                lut[j] = ix + grid.w * iy
                j++
            }
        }
        // println(Arrays.toString(lut))
        return lut
    }
}


