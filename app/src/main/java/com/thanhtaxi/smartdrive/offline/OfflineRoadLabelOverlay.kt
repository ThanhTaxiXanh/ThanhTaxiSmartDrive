package com.thanhtaxi.smartdrive.offline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import org.maplibre.android.maps.MapLibreMap

/** Labels from already-rendered, local vector roads; no remote glyph/font requests. */
data class RoadLabelCandidate(val text: String, val x: Float, val y: Float)

object RoadLabelPlacement {
    fun choose(candidates: List<RoadLabelCandidate>, width: Int, height: Int,
               maxLabels: Int = 9): List<RoadLabelCandidate> {
        if (width <= 0 || height <= 0 || maxLabels !in 1..20) return emptyList()
        val chosen = ArrayList<RoadLabelCandidate>()
        val usedNames = HashSet<String>()
        for (candidate in candidates) {
            if (!candidate.x.isFinite() || !candidate.y.isFinite() ||
                candidate.x < 60f || candidate.x > width-60f ||
                candidate.y < 26f || candidate.y > height-30f) continue
            val name = candidate.text.trim()
            if (name.isEmpty()) continue
            if (name in usedNames) continue
            if (chosen.any {
                val dx=candidate.x-it.x
                val dy=candidate.y-it.y
                dx*dx+dy*dy < 135f*135f
            }) continue
            chosen.add(candidate.copy(text=name.take(28)))
            usedNames.add(name)
            if (chosen.size >= maxLabels) break
        }
        return chosen
    }
}

/**
 * Android Paint renders Vietnamese text from the installed system typeface.
 * Unlike MapLibre SymbolLayer, it does not require a glyph URL or bundled font.
 * The samples are *display only*; they never feed matching or speed limits.
 */
class OfflineRoadLabelOverlay(context: Context) : View(context) {
    private val density=resources.displayMetrics.density
    private val textPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=Color.rgb(20,40,55)
        typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        textSize=12.5f*density
    }
    private val boxPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=0xEAF6F7F5.toInt()
    }
    private val strokePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=0xBB687E87.toInt()
        style=Paint.Style.STROKE
        strokeWidth=density
    }
    private var labels: List<RoadLabelCandidate> = emptyList()

    fun show(candidates: List<RoadLabelCandidate>) {
        labels = RoadLabelPlacement.choose(candidates,width,height)
        invalidate()
    }
    fun clear() { labels=emptyList();invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (l in labels) {
            val w=textPaint.measureText(l.text)
            val x=l.x
            val y=l.y
            val rect=RectF(x-w/2-7*density,y-18*density,
                x+w/2+7*density,y+5*density)
            canvas.drawRoundRect(rect,5*density,5*density,boxPaint)
            canvas.drawRoundRect(rect,5*density,5*density,strokePaint)
            canvas.drawText(l.text,x-w/2,y-2*density,textPaint)
        }
    }
}

/**
 * Queries at most 36 visible sample points on the UI thread, delayed until the
 * camera is idle. No continuous GPS or per-frame CPU-intensive sampling.
 */
class OfflineRoadLabelSampler(
    private val map: MapLibreMap,
    private val overlay: OfflineRoadLabelOverlay
) {
    private val handler=Handler(Looper.getMainLooper())
    private var generation=0
    private var enabled=false
    private val pending=Runnable { sample() }

    fun setEnabled(value: Boolean) {
        enabled=value
        if(!value) { handler.removeCallbacks(pending);overlay.clear() }
        else schedule()
    }
    fun schedule() {
        handler.removeCallbacks(pending)
        if(!enabled) return
        generation++
        handler.postDelayed(pending,550L)
    }
    fun stop() {
        enabled=false
        generation++
        handler.removeCallbacks(pending)
        overlay.clear()
    }
    private fun sample() {
        if(!enabled || map.style == null) return
        val w=overlay.width; val h=overlay.height
        if(w < 250 || h < 160) return
        val points=ArrayList<RoadLabelCandidate>()
        val cols=4; val rows=5
        for (row in 0 until rows) for(col in 0 until cols) {
            val x=w*(col+0.5f)/cols
            val y=h*(row+0.5f)/rows
            val features=runCatching {
                map.queryRenderedFeatures(PointF(x,y),"road-lines")
            }.getOrDefault(emptyList())
            val fields=features.firstOrNull()?.properties()?.entrySet()?.associate { entry ->
                entry.key to (if(entry.value.isJsonPrimitive) entry.value.asString else null)
            }.orEmpty()
            val roadName=RoadFeatureSummary.shortName(fields)
            if(roadName!=null) points.add(RoadLabelCandidate(roadName,x,y))
        }
        overlay.show(points)
    }
}
