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
package org.openmrs.android.fhir.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openmrs.android.fhir.R

@Composable
fun NetworkStatusBanner(text: String, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.fillMaxWidth().testTag("NetworkStatusBanner"),
    color = Color.Transparent,
  ) {
    Text(
      modifier =
        Modifier.fillMaxWidth()
          .background(
            colorResource(id = androidx.browser.R.color.browser_actions_bg_grey),
          )
          .padding(vertical = 8.dp)
          .testTag("NetworkStatusText"),
      text = text,
      color = colorResource(id = R.color.black),
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
fun DrawerHeader(
  label: String,
  lastSyncValue: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .height(dimensionResource(id = R.dimen.header_height))
        .padding(horizontal = 16.dp)
        .padding(top = 16.dp)
        .testTag("DrawerHeader"),
    horizontalAlignment = Alignment.Start,
  ) {
    Text(
      modifier = Modifier.padding(top = 16.dp).testTag("DrawerHeaderLabel"),
      text = label,
      color = colorResource(id = R.color.black),
      fontWeight = FontWeight.Medium,
    )
    Text(
      modifier = Modifier.padding(top = 8.dp).testTag("DrawerHeaderValue"),
      text = lastSyncValue,
      color = colorResource(id = R.color.black),
      fontWeight = FontWeight.SemiBold,
    )
  }
}
