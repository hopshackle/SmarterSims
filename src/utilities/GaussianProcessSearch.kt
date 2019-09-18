package utilities

import jgpml.*
import Jama.*
import evodef.*
import jgpml.covariancefunctions.*
import java.io.*
import java.util.ArrayList
import java.util.Arrays
import java.util.*
import kotlin.math.*
import kotlin.streams.toList

/**
 * Created by james on 31/07/2017.
 */
val SimProperties = Properties()

class GaussianProcessSearch(val name: String, val searchSpace: SearchSpace, val nSamples: Int = 1) {

    var iteration = 0
        private set
    private val parameterConstraints = ArrayList<ParameterDetail>()
    private var startTime: Long = 0
    private var mainGP: GaussianProcess? = null
    private var timeGP: GaussianProcess? = null
    private var Xmean: DoubleArray? = null
    private var Ymean: Double = 0.toDouble()
    private var Tmean: Double = 0.toDouble()
    private val useExpectedImprovement = SimProperties.getProperty("ExpectedImprovementPerUnitTimeInParameterSearch", "false").equals("true")
    private val kappa = SimProperties.getProperty("ParameterSearchKappaForGP-UCB", "2.0").toDouble()
    private val startSeeds = SimProperties.getProperty("ParameterSearchInitialRandomSeeds", "20").toInt()
    private val kernelToUse = SimProperties.getProperty("ParameterSearchKernel", "SE:LIN")

    private val rnd = Random(System.currentTimeMillis())
    // we generate a load of random points in the space, and then evaluate them all
// noise parameter is always the last one given kernel construction
// first we find the incumbent point and value (from the N random ones)
// convert from variance to sd
// overhead for GP calculations
// we measure expected improvement against the base incumbent value
    val nextPointToTry: DoubleArray
        get() {
            var N = 50.0.pow(parameterConstraints.size.toDouble()).toInt()
            N = min(N, 250000)
            val xstar = Array(N) { DoubleArray(parameterConstraints.size) }
            for (i in 0 until N) {
                val sample = randomParameterValues(true)
                for (j in sample.indices)
                    xstar[i][j] = sample[j] - Xmean!![j]
            }
            val baseNoise = exp(mainGP!!.logtheta.get(mainGP!!.logtheta.rowDimension - 1, 0)).pow(2.0)
            println(String.format("Base noise is %.3g (sd: %.3g)", baseNoise, sqrt(baseNoise)))
            println("All params: " + mainGP!!.logtheta.transpose().array[0].joinToString("|") { String.format("%2g", it) })
            val predictions = mainGP!!.predict(Matrix(xstar))
            var timePredictions = arrayOfNulls<Matrix>(2)
            if (useExpectedImprovement) {
                timePredictions = timeGP!!.predict(Matrix(xstar))
            }
            var bestScore = java.lang.Double.NEGATIVE_INFINITY
            var bestMean = java.lang.Double.NEGATIVE_INFINITY
            var bestEstimate = 0.0
            var bestPredictedTime = 0.0
            var bestEIScore = 0.0
            var bestUCB = 0.0
            var bestLatent = 0.0
            var bestExpectedImprovement = java.lang.Double.NEGATIVE_INFINITY
            val nextSetting = DoubleArray(parameterConstraints.size)
            val nextSettingWithEI = DoubleArray(parameterConstraints.size)
            val optimalSetting = DoubleArray(parameterConstraints.size)
            var negNoise = 0
            for (i in 0 until N) {
                val value = predictions[0].get(i, 0)
                if (value > bestMean) {
                    bestMean = value
                    bestUCB = sqrt(predictions[1].get(i, 0) - baseNoise)
                    for (j in optimalSetting.indices) {
                        optimalSetting[j] = xstar[i][j] + Xmean!![j]
                        if (parameterConstraints[j].logScale)
                            optimalSetting[j] = exp(optimalSetting[j])
                    }
                }
            }
            for (i in 0 until N) {
                val value = predictions[0].get(i, 0)
                var latentNoise = predictions[1].get(i, 0) - baseNoise
                if (latentNoise < 0.00) {
                    latentNoise = 0.00
                    negNoise++
                } else {
                    latentNoise = sqrt(latentNoise)
                }
                val score = value + kappa * latentNoise
                var predictedTime = 1.0
                if (useExpectedImprovement) {
                    predictedTime = timePredictions[0]!!.get(i, 0) + Tmean
                    if (predictedTime < 0.00) predictedTime = 0.00
                    predictedTime += 30.0
                }
                val expectedImprovement = (score - bestMean) / predictedTime
                if (score > bestScore) {
                    bestScore = score
                    bestLatent = latentNoise
                    bestEstimate = value
                    for (j in nextSetting.indices) {
                        nextSetting[j] = xstar[i][j] + Xmean!![j]
                        if (parameterConstraints[j].logScale)
                            nextSetting[j] = Math.exp(nextSetting[j])
                    }
                }
                if (expectedImprovement > bestExpectedImprovement) {
                    bestExpectedImprovement = expectedImprovement
                    bestEIScore = value
                    bestPredictedTime = predictedTime
                    for (j in nextSettingWithEI.indices) {
                        nextSettingWithEI[j] = xstar[i][j] + Xmean!![j]
                        if (parameterConstraints[j].logScale)
                            nextSettingWithEI[j] = Math.exp(nextSettingWithEI[j])
                    }
                }
            }
            println(String.format("Optimal mean is %.3g (sigma = %.2g) with params %s",
                    bestMean + Ymean, bestUCB, optimalSetting.joinToString("|") { String.format("%2g", it) }))
            println(String.format("Acquisition expected mean is %.3g (sigma = %.2g) with params %s",
                    bestEstimate + Ymean, bestLatent, nextSetting.joinToString("|") { String.format("%2g", it) }))

            if (negNoise > 0) println("$negNoise samples had negative latent noise")

            if (useExpectedImprovement) {
                println(String.format("EI/T expected mean is %.3g (sigma = %.2g) in predicted T of %.0f with params %s",
                        bestEIScore + Ymean, (bestExpectedImprovement * bestPredictedTime + bestMean - bestEIScore) / kappa,
                        bestPredictedTime - 30, nextSettingWithEI.joinToString("|") { String.format("%2g", it) }))
                return nextSettingWithEI
            }
            return nextSetting
        }

    init {
        // searchSpace defines the space to search - once we extract the ranges from it
        for (k in (0 until searchSpace.nDims())) {
            val key = searchSpace.name(k)
            val options = searchSpace.nValues(k)
            if (searchSpace.value(k, 0) is Number) {
                val minValue = (0 until options).map { searchSpace.value(k, it) as Double }.min() ?: 0.00
                val maxValue = (0 until options).map { searchSpace.value(k, it) as Double }.max() ?: 0.00
                parameterConstraints.add(ParameterDetail(key, minValue, maxValue))
            } else {
                parameterConstraints.add(ParameterDetail(key, (0 until options).map {
                    searchSpace.value(k, it)
                }))
            }
        }
    }

    fun getParameterSearchValues(): DoubleArray {
        // TODO: Currently just apply GP to continuous variables. Categorical ones to be added later.
        startTime = System.currentTimeMillis()
        iteration = 0
        return if (iteration <= startSeeds)
            randomParameterValues(false)
        else
            generateParametersFromGP()
    }

    /*
    categorical variables are an index from 0 to N-1
    continuous ones are on a logScale where appropriate
     */
    private fun randomParameterValues(onLogScale: Boolean): DoubleArray {
        val retValue = DoubleArray(parameterConstraints.size)
        for (i in parameterConstraints.indices) {
            val pd = parameterConstraints[i]
            if (pd.continuous) {
                retValue[i] = Math.random() * (pd.toValue - pd.fromValue) + pd.fromValue
                if (pd.logScale && !onLogScale)
                    retValue[i] = exp(retValue[i])
                if (pd.integer && !(pd.logScale && onLogScale))
                    retValue[i] = (retValue[i] + 0.5).toInt().toDouble()
            } else {
                retValue[i] = rnd.nextInt(pd.categoricalValues.size).toDouble()
            }
        }
        return retValue
    }

    private fun generateParametersFromGP(): DoubleArray {
        trainGPFromDataInTable()
        return nextPointToTry
    }

    private fun trainGPFromDataInTable() {
        // Form X and Y from database table
        val protoX = ArrayList<List<Double>>()
        val protoY = ArrayList<Double>()
        val protoT = ArrayList<Double>()

        // use file instead of database for now
        val fileAsLines = BufferedReader(FileReader("$name.txt")).lines().toList()

        fileAsLines.map {
            val allData = it.split(",")
            val rowData = parameterConstraints.withIndex().map { (i, pd) ->
                if (!pd.continuous)
                    throw AssertionError("GP not yet working for categorical parameters")
                val value = allData[i].toDouble()
                if (pd.logScale) ln(value) else value
            }
            protoX.add(rowData)

            val score = allData[allData.size - 2].toDouble()
            protoY.add(score)
            val timeTaken = allData[allData.size - 1].toDouble()
            protoT.add(timeTaken)
        }

        // protoX and protoY now populated with raw data
        val Xmean = DoubleArray(protoX[0].size)
        Ymean = 0.0
        Tmean = 0.0
        val X = Array(protoX.size) { DoubleArray(protoX[0].size) }
        val Y = Array(protoY.size) { DoubleArray(1) }
        val T = Array(protoT.size) { DoubleArray(1) }
        for (i in Y.indices) {
            Y[i][0] = protoY[i]
            T[i][0] = protoT[i]
            Ymean += Y[i][0]
            Tmean += T[i][0]
            val temp = protoX[i]
            for (j in parameterConstraints.indices) {
                X[i][j] = temp[j]
                Xmean[j] += X[i][j]
            }
        }
        Ymean /= protoY.size.toDouble()
        Tmean /= protoT.size.toDouble()
        for (i in Xmean.indices)
            Xmean[i] /= protoY.size.toDouble()

        // zero mean all the data
        for (i in Y.indices) {
            Y[i][0] -= Ymean
            T[i][0] -= Tmean
            for (j in parameterConstraints.indices) {
                X[i][j] -= Xmean[j]
            }
        }

        val kernelTypes = kernelToUse.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val allKernels = arrayOfNulls<CovarianceFunction>(kernelTypes.size + 1)
        for (i in kernelTypes.indices) {
            val type = kernelTypes[i]
            var nextKernel: CovarianceFunction? = null
            when (type) {
                "SE" -> nextKernel = CovSEard(parameterConstraints.size)
                "LIN" -> nextKernel = CovLINard(parameterConstraints.size)
                "NN" -> nextKernel = CovNNone()
                else -> throw AssertionError("Unknown kernel type $type. Valid values are SE, LIN, NN.")
            }
            allKernels[i] = nextKernel
        }
        allKernels[kernelTypes.size] = CovNoise() // put noise term last
        val kernel = CovSum(parameterConstraints.size, *allKernels)

        val kernelParameters = kernel.numParameters()  // last is always noise level
        mainGP = GaussianProcess(kernel)
        timeGP = GaussianProcess(kernel)

        val theta = Array(kernelParameters) { DoubleArray(1) }
        // train GP
        mainGP!!.train(Matrix(X), Matrix(Y), Matrix(theta), 20)
        if (useExpectedImprovement) timeGP!!.train(Matrix(X), Matrix(T), Matrix(theta), 20)
    }

    fun updateWithResult(finalScore: Double) {
        if (parameterConstraints.isEmpty()) return
        val timeTaken = (System.currentTimeMillis() - startTime) / 1000.0

        val rowData = nextPointToTry.map { it } + finalScore + timeTaken
        val outputFile = FileWriter("ParameterSearchResult.txt", true)
        outputFile.write(rowData.joinToString(",") { String.format("%5g", it) })
    }

    fun complete(): Boolean {
        return parameterConstraints.size == 0
    }
}

internal class ParameterDetail {

    var name: String
    var continuous: Boolean = false
    var logScale: Boolean = false
    var integer: Boolean = false
    var fromValue: Double = 0.toDouble()
    var toValue: Double = 0.toDouble()
    var categoricalValues: List<Any> = emptyList()

    constructor(parameter: String, from: Double, to: Double) {
        name = parameter
        continuous = true
        if (from - from.toInt() < 0.0001 && to - to.toInt() < 0.0001)
            integer = true
        if (to / from > 1e2 || from / to > 1e2) {
            if (to < 0.0) throw AssertionError("Negative values on logScale not yet supported")
            logScale = true
            fromValue = Math.log(from)
            toValue = Math.log(to)
        } else {
            logScale = false
            fromValue = from
            toValue = to
        }
        println(String.format("%s, range of %.2g to %.2g, logScale=%s", name, from, to, logScale))
    }

    constructor(parameter: String, values: List<Any>) {
        name = parameter
        continuous = false
        categoricalValues = values.map { i -> i }.toList()
    }
}

