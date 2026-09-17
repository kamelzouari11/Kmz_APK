package com.kamel.cccamscrapper.parser

data class CccamServer(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val raw: String
) {
    val normalizedLine: String
        get() = "C: $host $port $username $password"
}

data class ParseIssue(
    val raw: String,
    val reason: String
)

data class ClineParseResult(
    val servers: List<CccamServer>,
    val issues: List<ParseIssue>
)

object ClineParser {
    private val cLineRegex = Regex(
        pattern = """(?im)^\s*C\s*:\s*(\S+)\s+(\d{1,5})\s+(\S+)\s+(\S+).*$"""
    )

    fun parse(input: String): ClineParseResult {
        if (input.isBlank()) return ClineParseResult(emptyList(), emptyList())

        val servers = linkedMapOf<String, CccamServer>()
        val consumedLines = mutableSetOf<String>()

        cLineRegex.findAll(input).forEach { match ->
            val raw = match.value.trim()
            buildServer(
                host = match.groupValues[1],
                port = match.groupValues[2],
                username = match.groupValues[3],
                password = match.groupValues[4],
                raw = raw
            )?.let {
                servers[it.key()] = it
                consumedLines.add(raw)
            }
        }

        val issues = mutableListOf<ParseIssue>()
        input.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { consumedLines.contains(it) }
            .forEach { line ->
                parseLooseLine(line)?.let { servers[it.key()] = it } ?: issues.add(
                    ParseIssue(line, "Format non reconnu")
                )
            }

        if (servers.isEmpty()) {
            parseTokenGroups(input).forEach { servers[it.key()] = it }
            if (servers.isNotEmpty()) issues.clear()
        }

        return ClineParseResult(servers.values.toList(), issues)
    }

    private fun parseLooseLine(line: String): CccamServer? {
        val tokens = line
            .replace(Regex("""(?i)^\s*C\s*:\s*"""), "")
            .replace(Regex("""[|;,]"""), " ")
            .split(Regex("""\s+"""))
            .map { it.cleanToken() }
            .filter { it.isNotBlank() }

        for (index in 0..tokens.size - 4) {
            val host = tokens[index]
            val port = tokens[index + 1]
            val username = tokens[index + 2]
            val password = tokens[index + 3]
            if (port.toIntOrNull() != null) {
                return buildServer(host, port, username, password, line)
            }
        }

        return parseLabeledLine(line)
    }

    private fun parseLabeledLine(line: String): CccamServer? {
        val values = Regex("""(?i)\b(host|url|server|port|user|username|pass|password)\s*[:=]\s*([^\s,;|]+)""")
            .findAll(line)
            .associate { it.groupValues[1].lowercase() to it.groupValues[2].cleanToken() }

        val host = values["host"] ?: values["url"] ?: values["server"]
        val port = values["port"]
        val username = values["user"] ?: values["username"]
        val password = values["pass"] ?: values["password"]

        return if (host != null && port != null && username != null && password != null) {
            buildServer(host, port, username, password, line)
        } else {
            null
        }
    }

    private fun parseTokenGroups(input: String): List<CccamServer> {
        val tokens = input
            .replace(Regex("""(?i)\bC\s*:"""), " ")
            .replace(Regex("""[|;,]"""), " ")
            .split(Regex("""\s+"""))
            .map { it.cleanToken() }
            .filter { it.isNotBlank() }

        return tokens.chunked(4).mapNotNull { group ->
            if (group.size == 4) {
                buildServer(group[0], group[1], group[2], group[3], group.joinToString(" "))
            } else {
                null
            }
        }
    }

    private fun buildServer(
        host: String,
        port: String,
        username: String,
        password: String,
        raw: String
    ): CccamServer? {
        val parsedPort = port.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val cleanHost = host.cleanToken()
            .removePrefix("http://")
            .removePrefix("https://")
            .trim('/')

        if (cleanHost.isBlank() || username.isBlank() || password.isBlank()) return null

        return CccamServer(
            host = cleanHost,
            port = parsedPort,
            username = username.cleanToken(),
            password = password.cleanToken(),
            raw = raw
        )
    }

    private fun CccamServer.key(): String = "$host:$port:$username:$password"

    private fun String.cleanToken(): String = trim().trim('"', '\'', '`', ',', ';', '|')
}
