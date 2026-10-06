package com.mbidesign.terminal

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSmokeTest {
    @get:Rule val camera = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    private fun labels(v: View): List<String> = buildList {
        if (v is TextView) add(v.text.toString())
        if (v is ViewGroup) for (i in 0 until v.childCount) addAll(labels(v.getChildAt(i)))
    }
    @Test fun firstLaunchShowsProtectedSetup() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(labels(activity.findViewById(android.R.id.content)).any { it.contains("Поставување на таблет") })
            }
        }
    }
    @Test fun nativeFaceModelVaultAndCameraOpenOnAndroid() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bitmap = instrumentation.context.assets.open("lena.jpg").use { BitmapFactory.decodeStream(it) }
        val features = FaceEngine(context).feature(bitmap)
        assertNotNull(features); assertEquals(128,features!!.size)
        val worker = Worker("instrumentation-only","fixture-local","Тест на камера")
        val store = LocalStore(context)
        store.setWorkers(listOf(worker))
        val vault = Vault(context)
        AdminGate.open(); vault.saveFaces(worker,List(5) { features.clone() })
        assertTrue(FaceEngine.matches(features,vault.faces(worker),.50f))
        val intent = Intent(context,FaceCheckActivity::class.java).putExtra("worker",worker.id).putExtra("type","work_start")
        ActivityScenario.launch<FaceCheckActivity>(intent).use { scenario ->
            Thread.sleep(5000)
            scenario.onActivity { activity ->
                assertTrue(labels(activity.findViewById(android.R.id.content)).any { it == worker.name })
            }
        }
        vault.deleteFace(worker); AdminGate.close(); bitmap.recycle()
        assertTrue(store.pending(true).isEmpty())
    }
}
