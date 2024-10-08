package org.openmrs.android.fhir

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.openmrs.android.fhir.auth.AuthStateManager

class SplashActivity : AppCompatActivity() {

    private lateinit var authStateManager: AuthStateManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        authStateManager = AuthStateManager.getInstance(applicationContext)
        //TODO: fix that point for real offline mode
        //by removing needsTokenRefresh we have an exception later on net.openid.appauth.AuthState.mRefreshToken being null
        // in this method: net.openid.appauth.AuthState.createTokenRefreshRequest(java.util.Map<java.lang.String,java.lang.String>)
        if (authStateManager.current.isAuthorized && !authStateManager.current.needsTokenRefresh) {
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }
}