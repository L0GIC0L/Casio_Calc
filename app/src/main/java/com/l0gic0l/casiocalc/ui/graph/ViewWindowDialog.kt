package com.l0gic0l.casiocalc.ui.graph

import androidx.compose.runtime.Composable
import com.l0gic0l.casiocalc.data.ViewWindow
import com.l0gic0l.casiocalc.ui.CalcViewModel
import com.l0gic0l.casiocalc.ui.components.NumberFieldsDialog

@Composable
fun ViewWindowDialog(vm: CalcViewModel, onDismiss: () -> Unit) {
    val w = vm.viewWindow
    NumberFieldsDialog(
        title = "View Window",
        labels = listOf("Xmin", "Xmax", "Xscale", "Ymin", "Ymax", "Yscale", "T/θ min (θ in radians)", "T/θ max", "T/θ pitch"),
        initial = listOf(w.xmin, w.xmax, w.xscl, w.ymin, w.ymax, w.yscl, w.tmin, w.tmax, w.tstep),
        onDismiss = onDismiss,
    ) { v ->
        if (v[0] < v[1] && v[3] < v[4] && v[6] < v[7] && v[8] > 0) {
            vm.updateViewWindow(ViewWindow(v[0], v[1], kotlin.math.abs(v[2]), v[3], v[4], kotlin.math.abs(v[5]), v[6], v[7], v[8]))
        }
    }
}
