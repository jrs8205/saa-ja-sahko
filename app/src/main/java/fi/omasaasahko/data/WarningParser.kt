package fi.omasaasahko.data

import fi.omasaasahko.domain.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.time.Instant

/** FMI CAP 1.2 / FMI profile 1.1.0, fat Atom snapshot, including future warnings. */
object WarningParser {
    private const val CAP = "urn:oasis:names:tc:emergency:cap:1.2"
    private const val ATOM = "http://www.w3.org/2005/Atom"
    private data class Node(val name: String, val ns: String, var text: String = "", val children: MutableList<Node> = mutableListOf()) {
        fun all(name: String, ns: String = CAP) = children.filter { it.name == name && it.ns == ns }
        fun value(name: String, ns: String = CAP) = all(name, ns).firstOrNull()?.text?.trim().orEmpty()
        fun required(name: String) = value(name).also { require(it.isNotEmpty()) { "CAP: $name puuttuu" } }
        fun descendants(name: String, ns: String): List<Node> = children.flatMap { (if (it.name == name && it.ns == ns) listOf(it) else emptyList()) + it.descendants(name, ns) }
    }
    fun parse(body: String, fetchedAt: Instant): WarningSnapshot {
        require(body.length <= 16_000_000)
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(StringReader(body))
        val stack = mutableListOf<Node>(); var root: Node? = null
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.DOCDECL -> error("DTD ei ole sallittu")
                XmlPullParser.START_TAG -> {
                    require(stack.size < 32)
                    val node = Node(parser.name, parser.namespace.orEmpty())
                    if (stack.isEmpty()) { require(root == null); root = node } else stack.last().children.add(node)
                    stack.add(node)
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (stack.isNotEmpty()) stack.last().text += parser.text
                XmlPullParser.END_TAG -> stack.removeAt(stack.lastIndex)
            }
            parser.nextToken()
        }
        val feed = requireNotNull(root)
        require(feed.name == "feed" && feed.ns == ATOM)
        val published = Instant.parse(feed.value("updated", ATOM))
        val entries = feed.all("entry", ATOM)
        val alerts = entries.flatMap { entry -> entry.descendants("alert", CAP).also { require(it.isNotEmpty()) { "CAP-sanoma puuttuu" } } }
            .filter { it.value("status") == "Actual" && it.value("scope") == "Public" && it.value("sender") == "urn:oid:2.49.0.0.246.0" }
        val replaced = alerts.filter { it.value("msgType") in setOf("Update", "Cancel") }.flatMap {
            it.value("references").split(Regex("\\s+")).mapNotNull { reference ->
                reference.split(',').takeIf { fields -> fields.size == 3 }?.let { fields -> fields[0] + "|" + fields[1] }
            }
        }.toSet()
        var partial = false
        val warnings = alerts.mapNotNull { alert ->
            if (alert.value("msgType") !in setOf("Alert", "Update") || alert.value("sender") + "|" + alert.value("identifier") in replaced) return@mapNotNull null
            val infos = alert.all("info")
            val info = infos.firstOrNull { it.value("language").equals("fi-FI", true) }
                ?: infos.firstOrNull { it.value("language").startsWith("en", true) } ?: infos.firstOrNull()
                ?: error("CAP info puuttuu")
            val level = when (info.value("severity")) {
                "Moderate" -> WarningLevel.YELLOW; "Severe" -> WarningLevel.ORANGE; "Extreme" -> WarningLevel.RED
                "Minor" -> return@mapNotNull null
                else -> { partial = true; return@mapNotNull null }
            }
            val onset = Instant.parse(info.required("onset")); val expires = Instant.parse(info.required("expires"))
            require(expires > onset)
            val areas = info.all("area").map { area ->
                val polygons = area.all("polygon").map { polygon ->
                    val points = polygon.text.trim().split(Regex("\\s+")).map { pair ->
                        val coordinates = pair.split(','); require(coordinates.size == 2)
                        val lat = coordinates[0].toDouble(); val lon = coordinates[1].toDouble()
                        require(lat.isFinite() && lat in -90.0..90.0 && lon.isFinite() && lon in -180.0..180.0)
                        GeoPoint(lat, lon)
                    }
                    require(points.size >= 4 && points.first() == points.last())
                    points
                }
                // Never substitute a province-name match for a missing polygon.
                if (polygons.isEmpty()) partial = true
                WarningArea(area.required("areaDesc"), polygons)
            }
            if (areas.isEmpty()) partial = true
            val eventCode = info.all("eventCode").firstOrNull { it.value("valueName").startsWith("profile:cap:https://alerts.fmi.fi/cap/profile/") }?.value("value").orEmpty()
            WeatherWarning(alert.required("identifier"), eventCode, info.required("event"), level, onset, expires,
                info.required("description"), info.value("instruction"), areas, Instant.parse(alert.required("sent")))
        }.distinctBy { it.id }
        return WarningSnapshot(fetchedAt, published, warnings, partial)
    }
}
