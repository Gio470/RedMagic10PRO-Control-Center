package com.elitedarkkaiser.redmagic.xposed

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * One icon pack, loaded inside a hooked app's own process.
 *
 * Ported from github.com/RichardLuo0/global-icon-pack-android's LocalSource: an icon pack is an
 * ordinary APK whose `appfilter.xml` maps a component to a drawable name, both of which this reads
 * out of the pack through [Resources]. Only `<item>` entries are handled -- upstream's dynamic
 * calendar and clock entries need per-entry metadata plumbed through the framework's icon fields,
 * which is well beyond what was asked for here.
 *
 * Entries are indexed by position: [getId] returns that index, the hooks pack it into a fake
 * resource id, and [getIcon] turns it back into a drawable. Entries whose drawable is missing from
 * the pack are dropped at load time, so an id that survives always resolves to something.
 */
class IconPackSource(context: Context, val pack: String, private val asFallback: Boolean) {

    private val res: Resources = context.packageManager.getResourcesForApplication(pack)
    private val drawableNames = mutableListOf<String>()
    private val index = mutableMapOf<ComponentName, Int>()

    init {
        parseAppFilter()
    }

    val size: Int get() = drawableNames.size

    /**
     * The pack's entry for [cn], or -- when "icon pack as fallback" is on -- the entry for any
     * component in the same package. Packs key their entries on the launcher activity, so an app
     * whose launcher class has been renamed since the pack was made otherwise loses its icon
     * everywhere, and every non-launcher component of every app never had one to begin with.
     */
    fun getId(cn: ComponentName): Int? =
        index[cn] ?: if (asFallback) index[packageEntry(cn.packageName)] else null

    fun getIcon(id: Int, density: Int): Drawable? {
        val name = drawableNames.getOrNull(id) ?: return null
        val resId = drawableId(name)
        if (resId == 0) return null
        return runCatching {
            if (density > 0) res.getDrawableForDensity(resId, density, null)
            else res.getDrawable(resId, null)
        }.getOrNull()
    }

    @SuppressLint("DiscouragedApi")
    private fun drawableId(name: String) = res.getIdentifier(name, "drawable", pack)

    private fun parseAppFilter() {
        val parser = openAppFilter() ?: return
        // An entry is worth keeping only if its drawable is actually in the pack; see the class doc.
        val idByName = mutableMapOf<String, Int>()
        fun idFor(name: String): Int? =
            idByName.getOrPut(name) {
                if (drawableId(name) == 0) -1
                else drawableNames.size.also { drawableNames.add(name) }
            }.takeIf { it >= 0 }

        runCatching {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                if (parser.name != "item") continue
                val cn = parser.getAttributeValue(null, "component")
                    ?.removeSurrounding("ComponentInfo{", "}")
                    ?.let(::unflatten) ?: continue
                val id = parser.getAttributeValue(null, "drawable")?.let(::idFor) ?: continue
                index[cn] = id
                // A pack names the launcher activity; the framework asks about the application as a
                // whole just as often, so the first entry seen for a package answers for it too.
                index.putIfAbsent(packageEntry(cn.packageName), id)
            }
        }
        (parser as? XmlResourceParser)?.close()
    }

    /** `appfilter` is normally a compiled xml resource, but some packs only ship the raw asset. */
    @SuppressLint("DiscouragedApi")
    private fun openAppFilter(): XmlPullParser? = runCatching {
        res.getIdentifier("appfilter", "xml", pack).takeIf { it != 0 }?.let { res.getXml(it) }
            ?: XmlPullParserFactory.newInstance().newPullParser().apply {
                setInput(res.assets.open("appfilter.xml"), Xml.Encoding.UTF_8.toString())
            }
    }.getOrNull()

    companion object {
        /** The stand-in component for "this package, whichever class asked". */
        fun packageEntry(packageName: String) = ComponentName(packageName, "")

        private fun unflatten(flat: String): ComponentName? {
            val sep = flat.indexOf('/')
            if (sep < 0) return null
            val pkg = flat.take(sep)
            val cls = flat.substring(sep + 1)
            if (pkg.isEmpty() || cls.isEmpty()) return null
            return ComponentName(pkg, if (cls.startsWith(".")) pkg + cls else cls)
        }
    }
}
