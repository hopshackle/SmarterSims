package utilities

fun <T> argParam(args: Array<String>, name: String, default: T): T {
    val fn: (String) -> T = when (default) {
        is Int -> { x: String -> x.toInt() as T }
        is Double -> { x: String -> x.toDouble() as T }
        is Boolean -> { x: String -> x.toBoolean() as T }
        else -> { x: String -> x as T }
    }
    val v: String? = args.find { it.startsWith(name + "=") }?.split("=")?.getOrNull(1)
    return if (v == null) default else fn(v)
}