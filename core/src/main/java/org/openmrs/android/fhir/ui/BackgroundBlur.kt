/*
* BSD 3-Clause License
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*
* 1. Redistributions of source code must retain the above copyright notice, this
*    list of conditions and the following disclaimer.
*
* 2. Redistributions in binary form must reproduce the above copyright notice,
*    this list of conditions and the following disclaimer in the documentation
*    and/or other materials provided with the distribution.
*
* 3. Neither the name of the copyright holder nor the names of its
*    contributors may be used to endorse or promote products derived from
*    this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
* AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
* IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
* DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
* FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
* DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
* SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
* CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
* OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
* OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package org.openmrs.android.fhir.ui

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import org.openmrs.android.fhir.R

private const val BACKGROUND_BLUR_RADIUS = 18f

/** Fades a blur in or out on what is behind the sync popup; API 31+, the scrim covers the rest. */
class BackgroundBlur(private val activity: Activity) {
  private var animator: ValueAnimator? = null
  private var radius = 0f

  fun set(blurred: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val targets =
      listOfNotNull<View>(
        activity.findViewById(R.id.main_content_container),
        activity.findViewById(R.id.top_bar_compose_container),
      )
    animator?.cancel()
    val to = if (blurred) BACKGROUND_BLUR_RADIUS else 0f
    animator =
      ValueAnimator.ofFloat(radius, to).apply {
        duration = 220
        addUpdateListener {
          radius = it.animatedValue as Float
          val effect =
            if (radius < 0.5f) {
              null
            } else {
              RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
            }
          targets.forEach { view -> view.setRenderEffect(effect) }
        }
        start()
      }
  }
}
