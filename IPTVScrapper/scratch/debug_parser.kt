import java.util.regex.Pattern

fun main() {
    val text = """
🌍Host: http://live.lynxiptv.xyz:80

👤Username: 323685518569

🔑Password: beW1lXQ2mU
""".trimIndent()

    val cleanText = text.replace("\u00A0", " ")
        .replace("&nbsp;", " ")
        .replace("\r\n", "\n")
        .replace("\r", "\n")

    val urlPattern = Pattern.compile("https?://[^\\s\"'<>\\(\\)\\[\\]\\{\\}\\^\\|\\\\\\u27A4\\u2705\\u2714\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+", Pattern.CASE_INSENSITIVE)
    val urlMatcher = urlPattern.matcher(cleanText)
    
    val urlPositions = mutableListOf<Pair<Int, String>>()
    while (urlMatcher.find()) {
        urlPositions.add(urlMatcher.start() to urlMatcher.group())
    }

    println("Found ${urlPositions.size} URLs")
    for (i in urlPositions.indices) {
        val (pos, urlStr) = urlPositions[i]
        println("URL: $urlStr")
        
        val nextUrlPos = if (i + 1 < urlPositions.size) urlPositions[i + 1].first else Math.min(pos + 1000, cleanText.length)
        val segment = cleanText.substring(pos, nextUrlPos)
        println("Segment: [$segment]")

        val userPattern = Pattern.compile("(?:User|USER|Username|Login|Account|Utilisateur|👤|👤Username|👤User)\\s*[:\\u27A4]?\\s*([^\\s\"'<>]+)", Pattern.CASE_INSENSITIVE)
        val passPattern = Pattern.compile("(?:Pass|PASS|Password|Pwd|Mot\\s*de\\s*passe|Motdepasse|🔑|🔑Password|🔑Pass|Password🔑)\\s*[:\\u27A4]?\\s*([^\\s\"'<>]+)", Pattern.CASE_INSENSITIVE)
        
        val userM = userPattern.matcher(segment)
        val passM = passPattern.matcher(segment)
        
        if (userM.find()) {
            println("User found: ${userM.group(1)}")
        } else {
            println("User NOT found")
        }
        
        if (passM.find()) {
            println("Pass found: ${passM.group(1)}")
        } else {
            println("Pass NOT found")
        }
    }
}
