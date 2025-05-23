package com.example.grafica

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.CheckBox
import kotlin.properties.Delegates
import com.example.grafica.databinding.ActivityLoginBinding
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import androidx.core.view.isInvisible
import androidx.core.widget.doOnTextChanged
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import android.app.ProgressDialog
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    // Variables globales accesibles desde cualquier parte de la app
    companion object{
        lateinit var useremail: String
        lateinit var providerSesion: String
    }

    private lateinit var binding: ActivityLoginBinding

    private var email by Delegates.notNull<String>()
    private var password by Delegates.notNull<String>()
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var viewCreateAccount : CheckBox

    // Autenticación de Firebase
    private lateinit var mAuth : FirebaseAuth

    // Iniciar sesión con Google
    private var RESULT_CODE_GOOGLE_SIGN_IN = 100
    private lateinit var mGoogleSignInClient: GoogleSignInClient
    private lateinit var progressDialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        binding = ActivityLoginBinding.inflate(layoutInflater)  // Inflar el binding
        setContentView(binding.root)

        // Asigna la visualización del checkbox de crear cuenta
        viewCreateAccount = findViewById(R.id.cbCrearCuenta)
        viewCreateAccount.visibility = View.INVISIBLE

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        // Inicializa la autenticación de Firebase Auth
        mAuth = FirebaseAuth.getInstance()


        // Checa si es un email válido
        manageButtonLogin()
        etEmail.doOnTextChanged { text, start, before, count -> manageButtonLogin() }
    }

    // Verifica si el usuario ya está autenticado al iniciar la actividad
    // Si el usuario ya está autenticado, lo redirige a la actividad principal
    public override fun onStart() {
        super.onStart()
        val currentUser = FirebaseAuth.getInstance().currentUser

        if(currentUser != null) {
            goHome(currentUser.email.toString(),currentUser.providerId)
        }
    }

    // Cierra la actividad de inicio de sesión y redirige a la pantalla de inicio
    override fun onBackPressed() {
        super.onBackPressed()
        val startMain = Intent(Intent.ACTION_MAIN)
        startMain.addCategory(Intent.CATEGORY_HOME)
        startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(startMain)
    }

    // Función para habilitar o deshabilitar el botón de inicio de sesión según la validez del email
    private fun manageButtonLogin(){
        val btnLogin = findViewById<View>(R.id.btnLogin)
        email = binding.etEmail.text.toString().trim()
        password = binding.etPassword.text.toString().trim()

        if(TextUtils.isEmpty(password) || TextUtils.isEmpty(email) || !ValidateEmail.isEmail(email)){
            btnLogin.setBackgroundColor(ContextCompat.getColor(this, R.color.gray))
            btnLogin.isEnabled = false
        }else{
            btnLogin.setBackgroundColor(ContextCompat.getColor(this, R.color.blue))
            (btnLogin as android.widget.Button).setTextColor(ContextCompat.getColor(this, R.color.white))
            btnLogin.isEnabled = true
        }
    }

    // Se llama al hacer clic en el botón de inicio de sesión
    fun login(view: View) {
        loginUser()
    }

    // Función para iniciar sesión
    private fun loginUser(){
        email = binding.etEmail.text.toString().trim()
        password = binding.etPassword.text.toString().trim()

        if (email.isNotEmpty() && password.isNotEmpty()) {
            // Intentar iniciar sesión con Firebase Auth
            mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Si la autenticación es exitosa, redirigir a la actividad principal
                        goHome(email, "email")
                    } else {
                        // Si falla muestra el checkbox para crear cuenta
                        if (viewCreateAccount.isInvisible) {
                            viewCreateAccount.visibility = View.VISIBLE
                        } else {
                            // Si el checkbox ya es visible, verifica si está marcado
                            // y llama a la función de registro
                            var cbAcept = findViewById<CheckBox>(R.id.cbCrearCuenta)
                            if (cbAcept.isChecked) {
                                register()
                            }
                        }
                    }
                }
        } else {
            Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show()
        }
    }

    // Función para redirigir a la actividad principal
    private fun goHome(email: String, provider: String) {
        useremail = email
        providerSesion = provider

        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }

    // Función para crear un nuevo usuario
    private fun register() {
        email = binding.etEmail.text.toString().trim()
        password = binding.etPassword.text.toString().trim()

        if (email.isNotEmpty() && password.isNotEmpty()) {
            // Intentar registrar un nuevo usuario con Firebase Auth
            FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener {
                    if (it.isSuccessful) {
                        // Guarda la fecha de registro en Firestore
                        var dateRegister = SimpleDateFormat("dd/MM/yyyy").format(Date())
                        var dbRegister = FirebaseFirestore.getInstance()

                        dbRegister.collection("users").document(email).set(
                            hashMapOf(
                                "user" to email,
                                "dateRegister" to dateRegister
                            )
                        )
                        // Si el registro es exitoso, redirigir a la actividad principal
                        goHome(email, "email")
                    } else {
                        Toast.makeText(this, "Error al registrar", Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show()
        }
    }

    // Se llama al hacer clic en el botón de "Olvidé mi contraseña"
    fun forgotPassword(view: View) {
        resetPassword()
    }

    // Restablece la contraseña del usuario via correo electrónico
    private fun resetPassword(){
        var e = binding.etEmail.text.toString()
        if (!TextUtils.isEmpty(e)) {
            mAuth.sendPasswordResetEmail(e)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Se ha enviado un correo para restablecer la contraseña", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Error al enviar el correo", Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            Toast.makeText(this, "Por favor, ingresa tu correo electrónico", Toast.LENGTH_SHORT).show()
        }
    }

    fun callSignInGoogle(view: View){
        signInGoogle()
    }

    private fun signInGoogle(){
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)
        mGoogleSignInClient.signOut()
        val signInIntent = mGoogleSignInClient.signInIntent
        startActivityForResult(signInIntent, RESULT_CODE_GOOGLE_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RESULT_CODE_GOOGLE_SIGN_IN) {

            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)!!

                if (account != null){
                    email = account.email!!
                    val credencial = GoogleAuthProvider.getCredential(account.idToken,null)
                    mAuth.signInWithCredential(credencial).addOnCompleteListener {
                        if (it.isSuccessful){
                            goHome(email, "google")
                        }else{
                            Toast.makeText(this,"Error al iniciar sesión con Google", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

            }catch (e: ApiException){
                Toast.makeText(this,"Error al iniciar sesión con Google", Toast.LENGTH_SHORT).show()
            }
        }
    }
}