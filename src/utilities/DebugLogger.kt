package utilities

import java.io.*
import java.util.logging.Logger

class EntityLog(logFileName: String) {
    protected val logFile = File(logDir + File.separator + logFileName + ".txt")
    protected var logFileOpen: Boolean = false
    protected lateinit var logWriter: FileWriter

    fun log(message: String) {
        var message = message
        if (!logFileOpen) {
            try {
                logWriter = FileWriter(logFile, true)
                logFileOpen = true
            } catch (e: IOException) {
                e.printStackTrace()
                errorLogger.severe("Failed to open logWriter$e")
                return
            }

        }

        try {
            logWriter.write(message + newline)
        } catch (e: IOException) {
            errorLogger.severe(e.toString())
            e.printStackTrace()
        }

    }

    fun rename(newName: String) {
        close()
        logFile.renameTo(File(logDir + File.separator + newName + ".txt"))
    }

    fun flush() {
        if (logFileOpen) {
            try {
                logWriter.flush()
            } catch (e: Exception) {
                errorLogger.severe(e.toString())
                e.printStackTrace()
            }

        }
    }

    fun close() {
        if (logFileOpen)
            try {
                logWriter.close()
                logFileOpen = false
            } catch (e: Exception) {
                errorLogger.severe(e.toString())
                e.printStackTrace()
            }

    }

    companion object {
        var logDir = "C:\\Simulation"
        var newline = System.getProperty("line.separator")
        protected var errorLogger = Logger.getLogger("hopshackle.simulation")
    }
}
