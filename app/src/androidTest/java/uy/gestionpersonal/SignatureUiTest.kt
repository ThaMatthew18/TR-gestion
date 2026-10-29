package uy.gestionpersonal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uy.gestionpersonal.data.*
import uy.gestionpersonal.ui.*

@RunWith(AndroidJUnit4::class)
class SignatureUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun signatureRequiresInkAndConsentAndIsBoundToTheDelivery() {
        val delivery = Delivery("delivery", Empresa.RR, kind = DeliveryKind.EPP, item = "Casco")
        var saved: ReceiptSignature? = null
        compose.setContent { GestionTheme { SignatureDialog(delivery, null, {}, { saved = it }) } }
        compose.onNodeWithText("Usar firma").assertIsNotEnabled()
        compose.onNodeWithTag("signature-canvas").performScrollTo().performTouchInput {
            down(Offset(width * .1f, height * .6f)); moveTo(Offset(width * .3f, height * .2f)); moveTo(Offset(width * .7f, height * .8f)); up()
        }
        compose.onNodeWithTag("signature-consent").performScrollTo().performClick()
        compose.onNodeWithText("Usar firma").assertIsEnabled().performClick()
        compose.runOnIdle { assertNotNull(saved); assertNotNull(delivery.copy(conformity = true, signature = saved).validSignature()) }
    }
}
