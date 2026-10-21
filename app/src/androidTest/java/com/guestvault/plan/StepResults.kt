package com.guestvault.plan

import android.content.Context
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONObject

/**
 * One verdict per numbered step of the manual test plan.
 *
 * Two channels, deliberately redundant:
 *
 *  1. `sendStatus` — shows up in the `am instrument -r` output, so CI logs
 *     contain every verdict even when the artifact upload fails.
 *  2. `files/step-results.jsonl` — pulled by `scripts/ci/run-step-plan.sh`
 *     with `run-as`, so a lost log line cannot hide a failure.
 *
 * A step *failure* is recorded and swallowed by [StepHarness.verify]; the run
 * keeps going so one broken step never hides the verdicts after it. The gate
 * lives in `scripts/ci/step_report.py`, which fails the job if any step that
 * was supposed to run has no PASS.
 */
object StepResults {

    private const val KEY = "guestvault-verify"

    private val entries = mutableListOf<JSONObject>()
    private var file: File? = null

    fun initialise(context: Context) {
        if (file != null) return
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        file = File(dir, "step-results.jsonl").also { it.delete() }
    }

    fun pass(id: String, expect: String, note: String, millis: Long) =
        record(id, expect, "PASS", note, millis)

    fun fail(id: String, expect: String, note: String, millis: Long) =
        record(id, expect, "FAIL", note, millis)

    fun skip(id: String, expect: String, reason: String) =
        record(id, expect, "SKIP", reason, 0)

    fun count(): Int = synchronized(entries) { entries.size }

    private fun record(
        id: String,
        expect: String,
        status: String,
        note: String,
        millis: Long
    ) {
        val json = JSONObject()
            .put("id", id)
            .put("expect", expect)
            .put("status", status)
            .put("note", note.replace("\n", " ").take(400))
            .put("ms", millis)

        synchronized(entries) {
            entries += json
            runCatching { file?.appendText(json.toString() + "\n") }
        }
        runCatching {
            InstrumentationRegistry.getInstrumentation().sendStatus(
                2,
                Bundle().apply { putString(KEY, json.toString()) }
            )
        }
    }
}
