package me.arianb.usb_hid_client.macros.engine

class Environment(private val parent: Environment? = null) {
    private val variables = mutableMapOf<String, Any>()
    private val functions = mutableMapOf<String, List<Stmt>>()

    init {
        // Pre-define system constants
        if (parent == null) {
            variables["\$_CAPSLOCK_ON"] = 0L
            variables["\$_NUMLOCK_ON"] = 0L
            variables["\$_SCROLLLOCK_ON"] = 0L
        }
    }

    fun getVar(name: String): Any? {
        val normalized = if (name.startsWith("$")) name else "$$name"
        if (variables.containsKey(normalized)) {
            return variables[normalized]
        }
        return parent?.getVar(normalized) ?: 0L
    }

    fun setVar(name: String, value: Any) {
        val normalized = if (name.startsWith("$")) name else "$$name"
        if (parent != null && parent.hasVar(normalized)) {
            parent.setVar(normalized, value)
        } else {
            variables[normalized] = value
        }
    }

    fun hasVar(name: String): Boolean {
        val normalized = if (name.startsWith("$")) name else "$$name"
        return variables.containsKey(normalized) || (parent?.hasVar(normalized) == true)
    }

    fun defineFunction(name: String, body: List<Stmt>) {
        functions[name.uppercase()] = body
    }

    fun getFunction(name: String): List<Stmt>? {
        return functions[name.uppercase()] ?: parent?.getFunction(name)
    }
}
